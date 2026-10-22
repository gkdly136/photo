"""Run the actual local visual model over a resumable downloaded review batch."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import time
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "vision"))
from framecoach_vision.backend import QwenObserver, SYSTEM_PROMPT
from framecoach_vision.dataset import draft


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--batch", default="vision/data/batches/batch-001")
    parser.add_argument("--batch-size", type=int, choices=[1, 2, 4], default=4)
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()
    folder = Path(args.batch)
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    output = folder / "annotations"; output.mkdir(exist_ok=True)
    prompt_sha = hashlib.sha256(SYSTEM_PROMPT.encode()).hexdigest()
    todo = []
    for item in manifest["items"]:
        target = output / (item["id"] + ".json")
        if target.exists():
            existing = json.loads(target.read_text(encoding="utf-8"))
            if existing["image_sha256"] == item["sha256"] and existing["prompt_sha256"] == prompt_sha:
                continue
            raise ValueError("Existing annotation does not match this run; use a new batch directory")
        todo.append(item)
    if args.limit is not None: todo = todo[:args.limit]
    if todo:
        observer = QwenObserver(device="cuda:0")
        for offset in range(0, len(todo), args.batch_size):
            items = todo[offset:offset+args.batch_size]
            images = []
            for item in items:
                file = folder / item["image_path"]
                if hashlib.sha256(file.read_bytes()).hexdigest() != item["sha256"]:
                    raise ValueError("Downloaded image checksum changed")
                with Image.open(file) as original:
                    images.append(ImageOps.exif_transpose(original).convert("RGB"))
            started = time.perf_counter()
            try:
                results = observer.observe_many(images, max_new_tokens=640)
            except observer.torch.cuda.OutOfMemoryError:
                observer.torch.cuda.empty_cache()
                results = [observer.observe(image, max_new_tokens=640) for image in images]
            for item, image, (scene, raw, stats, failure) in zip(items, images, results):
                label = draft(item, scene, raw, stats, failure, image, observer.lock, prompt_sha)
                # Rename only after a complete JSON write; interruption can resume safely.
                target = output / (item["id"] + ".json")
                temporary = target.with_suffix(".tmp")
                temporary.write_text(json.dumps(label, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                temporary.replace(target)
            print(json.dumps({"done": min(offset+len(items), len(todo)), "total_this_run": len(todo),
                              "ids": [i["id"] for i in items], "seconds": round(time.perf_counter()-started, 1),
                              "invalid": sum(r[0] is None for r in results)}, ensure_ascii=True), flush=True)
    records = [json.loads((output / (item["id"] + ".json")).read_text(encoding="utf-8"))
               for item in manifest["items"] if (output / (item["id"] + ".json")).exists()]
    (folder / "annotations.jsonl").write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in records), encoding="utf-8")
    print(f"Saved {len(records)} machine drafts; all pending human review.", flush=True)


if __name__ == "__main__": main()
