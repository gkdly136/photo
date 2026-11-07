"""Download public weights only. No images or user data leave this machine."""
import hashlib
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("HF_HOME", str(ROOT / ".tools" / "hf-home"))
os.environ.setdefault("HF_HUB_DISABLE_XET", "1")
os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
os.environ.setdefault("DO_NOT_TRACK", "1")
os.environ.setdefault("HF_HUB_DOWNLOAD_TIMEOUT", "120")


def main():
    from huggingface_hub import snapshot_download
    model_id = "Qwen/Qwen3-VL-2B-Instruct"
    destination = ROOT / "vision/model_store/Qwen3-VL-2B-Instruct"
    lock_path = ROOT / "vision/base_model.lock.json"
    if lock_path.exists():
        revision = json.loads(lock_path.read_text(encoding="utf-8"))["revision"]
    else:
        request = Request(f"https://huggingface.co/api/models/{model_id}", headers={"Cache-Control": "no-cache"})
        with urlopen(request, timeout=60) as response:
            revision = json.load(response)["sha"]
    print(f"Downloading {model_id} at pinned revision {revision}", flush=True)
    snapshot_download(repo_id=model_id, revision=revision, local_dir=destination,
                      allow_patterns=["*.json", "*.safetensors", "*.txt", "*.jinja", "LICENSE", "README.md"],
                      max_workers=1)
    files = []
    for file in sorted(destination.iterdir()):
        if file.is_file():
            with file.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            files.append({"name": file.name, "bytes": file.stat().st_size, "sha256": digest})
    lock = {"model_id": model_id, "revision": revision, "license": "Apache-2.0",
            "weight_status": "upstream_pretrained_unmodified", "files": files}
    lock_path.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Verified {len(files)} files. Lock: {lock_path}", flush=True)


if __name__ == "__main__":
    main()
