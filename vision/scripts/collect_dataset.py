"""Collect a reproducible Commons photo-review batch, with per-file attribution.

Downloads explicitly requested photo data; no user photos are uploaded.
Images remain under their individual licenses. Nothing is marked human-approved.
"""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import html
from io import BytesIO
import json
from pathlib import Path
import random
import re
import time
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
UA = "FrameCoachDataset/0.1 (photo annotation research; https://github.com/gkdly136/photo)"
API = "https://commons.wikimedia.org/w/api.php"
CATEGORIES = {
    "portrait": ["Featured pictures of people", "Featured pictures of actors",
                 "Featured pictures of women", "Featured pictures of scientists",
                 "Featured pictures of musicians", "Featured pictures of writers"],
    "landscape": ["Featured pictures of landscapes", "Featured pictures of mountains",
                  "Featured pictures of forests", "Featured pictures of coasts"],
}


def plain(value):
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]*>", " ", str(value)))).strip()


def request(url, limit=15_000_000):
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname not in {"commons.wikimedia.org", "upload.wikimedia.org", "thumb.wikimedia.org"}:
        raise ValueError("Unapproved photo source")
    last = None
    for attempt in range(4):
        try:
            with urlopen(Request(url, headers={"User-Agent": UA}), timeout=55) as response:
                data = response.read(limit + 1)
            if len(data) > limit:
                raise ValueError("Download exceeds size budget")
            return data
        except Exception as error:
            last = error
            time.sleep(min(2 ** attempt, 8))
    raise last


def query(**params):
    data = json.loads(request(API + "?" + urlencode(dict(action="query", format="json", **params))))
    if "error" in data:
        raise RuntimeError(str(data["error"]))
    return data


def metadata(pages):
    output = []
    for start in range(0, len(pages), 40):
        part = pages[start:start+40]
        data = query(pageids="|".join(str(p["pageid"]) for p in part), prop="imageinfo",
                     iiprop="url|size|mime|extmetadata", iiurlwidth=1280, iiurlheight=1280,
                     iiextmetadatafilter="Artist|LicenseShortName|LicenseUrl|Copyrighted|Restrictions|Assessments|Categories|Credit|ImageDescription|License")
        source = {p["pageid"]: p for p in part}
        for page in data.get("query", {}).get("pages", {}).values():
            if not page.get("imageinfo"):
                continue
            info = page["imageinfo"][0]
            ext = info.get("extmetadata", {})
            field = lambda key: plain(ext.get(key, {}).get("value", ""))
            license_name = field("LicenseShortName")
            license_url = field("LicenseUrl")
            cats = field("Categories")
            text = (page["title"] + " " + cats + " " + field("Artist") + " " + field("ImageDescription")).lower()
            # This pilot targets conventional portrait/landscape photos.
            excluded = ["nude", "nudity", "naked", "corpse", "dead bod", "human remains", "human skull", "ossuar", "catacomb", "child", "children", "baby", "babies", "infant", "painting", "drawing", "engrav", "lithograph", "poster", "illustration", "sculpture", "collage", "montage"]
            if any(word in text for word in excluded):
                continue
            if info.get("mime") not in {"image/jpeg", "image/png"}:
                continue
            if min(info["width"], info["height"]) < 640 or not .4 <= info["width"] / info["height"] <= 3:
                continue
            allowed = (re.fullmatch(r"CC BY(?:-SA)? [1-4]\.0", license_name) is not None
                       or license_name in {"CC0", "CC0 1.0", "Public domain"})
            if not allowed or not field("Artist") or not info.get("thumburl"):
                continue
            if license_name != "Public domain" and not license_url.startswith("https://creativecommons.org/"):
                continue
            if "featured" not in field("Assessments").split("|"):
                continue
            output.append({"page_id": page["pageid"], "title": page["title"],
                           "group": source[page["pageid"]]["group"],
                           "source_category": source[page["pageid"]]["source_category"],
                           "source_page": info["descriptionurl"], "original_url": info["url"],
                           "download_url": info["thumburl"], "author": field("Artist"),
                           "license": license_name, "license_url": license_url,
                           "credit": field("Credit"), "restrictions": field("Restrictions"),
                           "selection_evidence": field("Assessments"),
                           "original_size": [info["width"], info["height"]],
                           "mime": info["mime"], "source_metadata": ext})
        print(f"metadata {min(start+40, len(pages))}/{len(pages)}; eligible {len(output)}", flush=True)
    return output


def dhash(image):
    pixels = list(ImageOps.grayscale(image).resize((9, 8)).getdata())
    result = 0
    for y in range(8):
        for x in range(8):
            result = (result << 1) | (pixels[y*9+x] > pixels[y*9+x+1])
    return result


def fetch_image(item):
    try:
        data = request(item["download_url"])
        with Image.open(BytesIO(data)) as image:
            image.load()
            if image.format not in {"JPEG", "PNG"}:
                raise ValueError("Not a supported photo image")
            upright = ImageOps.exif_transpose(image)
            size = list(upright.size)
            fingerprint = dhash(upright)
        return item, data, size, fingerprint, None
    except Exception as error:
        return item, None, None, None, str(error)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="vision/data/batches/batch-001")
    parser.add_argument("--count", type=int, default=200)
    args = parser.parse_args()
    if not 1 <= args.count <= 500:
        raise ValueError("This collector is limited to 1..500 review photos")
    dest = Path(args.output)
    dest.mkdir(parents=True, exist_ok=True)
    if (dest / "manifest.json").exists():
        raise ValueError("This batch already has a manifest. Use a new output directory; existing reviews must not be overwritten.")
    assets = dest / "images"; assets.mkdir(exist_ok=True)
    candidate_path = dest / "candidates.json"
    if candidate_path.exists():
        candidates = json.loads(candidate_path.read_text(encoding="utf-8"))
    else:
        pages = {}; rng = random.Random(20261022)
        for group, categories in CATEGORIES.items():
            for category in categories:
                data = query(list="categorymembers", cmtitle="Category:" + category, cmtype="file", cmlimit=500)
                members = data.get("query", {}).get("categorymembers", [])
                rng.shuffle(members)
                limit = 180 if group == "landscape" else 100
                for p in members[:limit]:
                    pages.setdefault(p["pageid"], dict(p, group=group, source_category=category))
                print(f"discovery {category}: {len(members)}", flush=True)
        candidates = metadata(list(pages.values()))
        candidate_path.write_text(json.dumps(candidates, ensure_ascii=False, indent=2), encoding="utf-8")
    rng = random.Random(20261022); rng.shuffle(candidates)
    accepted = []; errors = []; author_counts = Counter(); hashes = set(); perceptual = []
    quotas = {"portrait": args.count // 2, "landscape": args.count - args.count // 2}
    counts = Counter()
    # Bounded concurrency: 3 simultaneous image requests, never download full-resolution panoramas.
    with ThreadPoolExecutor(max_workers=3) as pool:
        for start in range(0, len(candidates), 9):
            selection = [c for c in candidates[start:start+9]
                         if counts[c["group"]] < quotas[c["group"]] and author_counts[c["author"]] < 10]
            for item, data, size, fingerprint, error in pool.map(fetch_image, selection):
                if error:
                    errors.append({"page_id": item["page_id"], "error": error}); continue
                if counts[item["group"]] >= quotas[item["group"]] or author_counts[item["author"]] >= 10:
                    continue
                sha = hashlib.sha256(data).hexdigest()
                if sha in hashes or any((fingerprint ^ old).bit_count() <= 5 for old in perceptual):
                    continue
                hashes.add(sha); perceptual.append(fingerprint)
                ident = f"FC-{len(accepted)+1:04d}"
                extension = ".png" if item["mime"] == "image/png" else ".jpg"
                relative = f"images/{ident}{extension}"
                (dest / relative).write_bytes(data)
                creator_key = hashlib.sha256(item["author"].casefold().encode()).hexdigest()
                bucket = int(creator_key[:8], 16) % 100
                split = "train_candidate" if bucket < 70 else "validation_candidate" if bucket < 85 else "test_candidate"
                accepted.append(dict(item, id=ident, image_path=relative, sha256=sha,
                                     dhash=f"{fingerprint:016x}", image_size=size,
                                     split_candidate=split, split_group="creator:" + creator_key[:20],
                                     review_status="pending", training_eligible=False,
                                     downloaded_at=datetime.now(timezone.utc).isoformat(),
                                     asset_note="Commons 1280px preview derivative; downloaded bytes unchanged"))
                counts[item["group"]] += 1; author_counts[item["author"]] += 1
                print(f"downloaded {len(accepted)}/{args.count} {ident} {item['group']}", flush=True)
            if len(accepted) == args.count:
                break
    manifest = {"dataset_id": "framecoach-review-batch-001", "schema_version": 1,
                "created_at": datetime.now(timezone.utc).isoformat(), "requested_count": args.count,
                "counts": dict(counts), "authors": len(author_counts), "items": accepted,
                "status": "pending_human_review", "training_performed": False,
                "sampling_seed": 20261022, "license_scope": "Each image retains its listed original license; this is not a blanket training or personality-rights clearance.",
                "split_policy": "Conservative creator grouping; candidate splits only. Cross-author same-location leakage still requires review."}
    (dest / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (dest / "download-errors.json").write_text(json.dumps(errors, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"downloaded": len(accepted), "counts": dict(counts), "authors": len(author_counts), "errors": len(errors)}, ensure_ascii=True), flush=True)
    return 0 if len(accepted) == args.count else 1


if __name__ == "__main__":
    raise SystemExit(main())
