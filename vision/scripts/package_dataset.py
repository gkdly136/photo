"""Package only the current batch assets, excluding QA data and retired images."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--batch", default="vision/data/batches/batch-001")
    args = parser.parse_args(); folder = Path(args.batch).resolve()
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    files = [folder / name for name in ["review.html", "manifest.json", "annotations.jsonl", "batch-summary.json", "tutorial-sources.json", "curation-history.json", "使用说明.md"]]
    for item in manifest["items"]:
        file = folder / item["image_path"]
        if not file.resolve().is_relative_to(folder) or hashlib.sha256(file.read_bytes()).hexdigest() != item["sha256"]:
            raise ValueError("Image path or checksum mismatch")
        files.extend([file, folder / "annotations" / (item["id"] + ".json")])
    archive = folder.parent / "FrameCoach-照片审核-第一批200张.zip"
    with ZipFile(archive, "w", compression=ZIP_STORED) as z:
        for file in files:
            z.write(file, "FrameCoach-照片审核-第一批/" + file.relative_to(folder).as_posix())
    with ZipFile(archive) as z:
        if z.testzip() is not None or len(z.namelist()) != len(files): raise ValueError("Archive failed verification")
    print(json.dumps({"archive": str(archive), "files": len(files), "bytes": archive.stat().st_size}, ensure_ascii=True))


if __name__ == "__main__": main()
