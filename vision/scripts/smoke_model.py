"""Real neural inference on synthetic fixtures. Does not measure photographic quality."""
import argparse
import json
from pathlib import Path
import platform
import sys
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "vision"))
from framecoach_vision.backend import QwenObserver
from framecoach_vision.cli import analyze
from make_examples import main as make_examples


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="vision/reports/local-smoke")
    parser.add_argument("--device", default="cuda:0", choices=["cuda:0", "cpu"])
    parser.add_argument("--evaluate-existing", action="store_true",
                        help="Regrade saved real inference outputs without loading weights")
    args = parser.parse_args()
    if not args.evaluate_existing:
        make_examples()
    observer = None if args.evaluate_existing else QwenObserver(device=args.device)
    cases = []
    for name, intent in [("person_road", "subject"), ("mountain_lake", "landscape"), ("blank", "subject")]:
        if args.evaluate_existing:
            result = json.loads((Path(args.output) / name / "result.json").read_text(encoding="utf-8"))
        else:
            result = analyze(SimpleNamespace(image=ROOT / f"vision/data/examples/{name}.png",
                         scene_id=name, subject_x=None, subject_y=None, aspect=None,
                         measurements=None, model_dir=None, device=args.device, intent=intent,
                         mood="natural", output=Path(args.output) / name), observer)
        scene = result["perception"] or {"subjects": [], "regions": []}
        people = sum(s["kind"] == "person" for s in scene["subjects"])
        expected_person = name == "person_road"
        regions = {r["kind"] for r in scene["regions"]}
        required = {"person_road": {"road"}, "mountain_lake": {"sky", "water"}, "blank": set()}[name]
        allowed = {"person_road": {"road", "sky", "vegetation", "ground"},
                   "mountain_lake": {"sky", "water", "vegetation", "ground"}, "blank": set()}[name]
        cases.append({"name": name, "structured_output_valid": result["status"] == "ok",
                      "person_presence_correct": (people > 0) == expected_person,
                      "required_background_present": required <= regions,
                      "unexpected_background_labels": sorted(regions - allowed),
                      "detected_people": people, "regions": [r["kind"] for r in scene["regions"]],
                      "performance": result["performance"], "validation_error": result["validation_error"]})
    if observer is None:
        summary = json.loads((Path(args.output) / "summary.json").read_text(encoding="utf-8"))
    else:
        torch = observer.torch
        import transformers
        summary = {"scope": "synthetic_engineering_smoke_only_not_photography_benchmark",
               "base_model": observer.lock["model_id"], "revision": observer.lock["revision"],
               "photography_finetuned": False, "python": platform.python_version(),
               "torch": torch.__version__, "transformers": transformers.__version__,
               "gpu": torch.cuda.get_device_name(0) if args.device.startswith("cuda") else None}
    summary["evaluation_mode"] = "saved_real_inference_outputs" if observer is None else "live_neural_inference"
    summary["cases"] = cases
    summary["passed"] = all(c["structured_output_valid"] and c["person_presence_correct"]
                            and c["required_background_present"] and not c["unexpected_background_labels"] for c in cases)
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=True, indent=2))
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
