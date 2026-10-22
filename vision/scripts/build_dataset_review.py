"""Build a standalone local review page; no server, remote scripts or image upload."""
import argparse
import base64
from collections import Counter
from io import BytesIO
import json
from pathlib import Path
import sys
from PIL import Image, ImageOps
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "vision"))
from framecoach_vision.dataset import digest

SOURCES = [
    {"id": "nikon-background", "title": "Nikon · Take Better Portraits", "url": "https://www.nikonusa.com/learn-and-explore/c/tips-and-techniques/take-better-portraits",
     "use": "原创概括背景干扰、取景与动作比较；未下载教程配图、未复制全文。"},
    {"id": "kelby-landscape", "title": "Scott Kelby / Rocky Nook · Landscape Recipes 公开节选", "url": "https://rockynook.com/article/landscape-recipes/",
     "use": "原创概括前景层次、天空占比与倒影观察；未下载教材配图、未复制全文。"},
]


def embedded_preview(path):
    """Display derivative only: upright, bounded-size JPEG, original asset unchanged."""
    with Image.open(path) as original:
        icc = original.info.get("icc_profile")
        preview = ImageOps.exif_transpose(original).convert("RGB")
    preview.thumbnail((960, 960))
    output = BytesIO()
    options = {"quality": 82, "optimize": True}
    if icc:
        options["icc_profile"] = icc
    preview.save(output, format="JPEG", **options)
    return "data:image/jpeg;base64," + base64.b64encode(output.getvalue()).decode("ascii")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--batch", default="vision/data/batches/batch-001")
    args = parser.parse_args(); folder = Path(args.batch)
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    records = [json.loads(line) for line in (folder / "annotations.jsonl").read_text(encoding="utf-8").splitlines()]
    labels = {r["id"]: r for r in records}
    if len(labels) != len(manifest["items"]): raise ValueError("Every downloaded photo needs a draft annotation")
    items = []
    for item in manifest["items"]:
        image_path = item["image_path"]
        if not image_path.startswith("images/") or ".." in Path(image_path).parts or not (folder / image_path).is_file():
            raise ValueError("Invalid photo path")
        annotation = labels[item["id"]]
        if annotation["image_sha256"] != item["sha256"]: raise ValueError("Stale annotation")
        items.append({key: item[key] for key in ["id", "group", "title", "author", "license", "license_url", "source_page", "image_path", "sha256", "restrictions"]}
                     | {"annotation": annotation, "image_src": embedded_preview(folder / image_path)})
    payload = {"dataset_id": manifest["dataset_id"], "manifest_sha256": digest(manifest), "items": items, "sources": SOURCES}
    embedded = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    template = (ROOT / "vision/review-template.html").read_text(encoding="utf-8")
    (folder / "review.html").write_text(template.replace("__DATASET_JSON__", embedded), encoding="utf-8")
    (folder / "tutorial-sources.json").write_text(json.dumps(SOURCES, ensure_ascii=False, indent=2), encoding="utf-8")
    summary = {"dataset_id": manifest["dataset_id"], "count": len(items), "groups": manifest["counts"], "authors": manifest["authors"],
               "licenses": dict(Counter(i["license"] for i in items)), "valid_model_observations": sum(r["perception"] is not None for r in records),
               "flagged_for_review": sum(bool(r["review_flags"]) for r in records),
               "human_approved": 0, "training_performed": False,
               "annotation_status": "machine_drafts_pending_human_review"}
    (folder / "batch-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=True, indent=2))


if __name__ == "__main__": main()
