"""Import a user's exported review. Does not train or grant automatic admission."""
import argparse
import json
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "vision"))
from framecoach_vision.dataset import validate_review


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("review")
    parser.add_argument("--batch", default="vision/data/batches/batch-001")
    args = parser.parse_args(); folder = Path(args.batch); file = Path(args.review)
    if file.stat().st_size > 8_000_000: raise ValueError("Review file exceeds limit")
    payload = json.loads(file.read_text(encoding="utf-8-sig"))
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    annotations = [json.loads(line) for line in (folder / "annotations.jsonl").read_text(encoding="utf-8").splitlines()]
    approved = validate_review(payload, manifest, annotations)
    labels = {a["id"]: a for a in annotations}
    reviewed = []
    for row in approved:
        original = labels[row["id"]]
        chosen = row["preference"]
        preferred = original["guidance"]["plans"][0 if chosen == "A" else 1]["id"] if chosen in {"A", "B"} else None
        reviewed.append({"id": row["id"], "image_sha256": row["image_sha256"],
                         "source_annotation_sha256": row["annotation_sha256"],
                         "perception": row.get("corrected_perception") or original["perception"],
                         "human_labels": row.get("human_labels", {}), "reviewer_notes": row["notes"],
                         "candidates_as_reviewed": original["guidance"]["plans"],
                         "preference": chosen, "preferred_plan_id": preferred,
                         "status": "human_reviewed_pending_dataset_admission", "training_eligible": False})
    (folder / "human-reviewed.jsonl").write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in reviewed), encoding="utf-8")
    (folder / "human-review-export.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"human_reviewed_records": len(reviewed), "training_started": False}, ensure_ascii=True))


if __name__ == "__main__": main()
