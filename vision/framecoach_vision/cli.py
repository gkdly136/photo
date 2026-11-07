import argparse
import hashlib
import json
from pathlib import Path
import sys

from .domain import Measurement, InvalidObservation, number
from .planner import make_plans
from .report import render_report


def analyze(args, observer=None):
    from PIL import Image, ImageOps
    from .backend import QwenObserver
    source = Path(args.image).resolve()
    if source.stat().st_size > 30 * 1024 * 1024:
        raise ValueError("Image exceeds 30 MiB limit")
    with Image.open(source) as original:
        if original.width * original.height > 60_000_000:
            raise ValueError("Image exceeds 60 megapixels")
        image = ImageOps.exif_transpose(original).convert("RGB")
    width, height = image.size
    if width < 64 or height < 64:
        raise ValueError("Image is too small")
    scene_id = args.scene_id or hashlib.sha256(source.read_bytes()).hexdigest()[:24]
    point = None
    if (args.subject_x is None) != (args.subject_y is None):
        raise ValueError("Both subject coordinates must be supplied")
    if args.subject_x is not None:
        point = (number(args.subject_x), number(args.subject_y))
    if args.aspect is not None:
        number(args.aspect, .25, 4)
    measurement = None
    if args.measurements:
        file = Path(args.measurements)
        if file.stat().st_size > 64000:
            raise ValueError("Measurement file is too large")
        measurement = Measurement.parse(json.loads(file.read_text(encoding="utf-8")), width, height, scene_id)
    observer = observer or QwenObserver(args.model_dir, args.device)
    scene, raw, stats, failure = observer.observe(image)
    guidance = make_plans(scene, width, height, args.intent, args.mood, point, args.aspect, measurement, failure)
    result = {"schema_version": 1, "model": "FrameCoach-Vision-0.1", "scene_id": scene_id,
              "status": "ok" if scene else "fallback", "base_model": observer.lock["model_id"],
              "base_revision": observer.lock["revision"], "photography_finetuned": False,
              "image_size": [width, height], "coordinates": "upright_image_normalized_0_1000",
              "intent": args.intent, "mood": args.mood, "perception": scene.to_dict() if scene else None,
              "guidance": guidance, "performance": stats,
              "raw_model_output": raw, "validation_error": failure}
    destination = Path(args.output)
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (destination / "report.html").write_text(render_report(image, result), encoding="utf-8")
    print(json.dumps({"status": result["status"], "inference_ms": stats["inference_ms"], "output": str(destination.resolve())}, ensure_ascii=True))
    return result


def main():
    parser = argparse.ArgumentParser(description="Run FrameCoach Vision locally; no image upload")
    sub = parser.add_subparsers(dest="command", required=True)
    infer = sub.add_parser("analyze")
    infer.add_argument("image")
    infer.add_argument("--output", default="vision/reports/local-analysis")
    infer.add_argument("--intent", choices=["subject", "environment", "landscape"], default="subject")
    infer.add_argument("--mood", choices=["natural", "quiet", "lively"], default="natural")
    infer.add_argument("--device", choices=["auto", "cuda:0", "cpu"], default="auto")
    infer.add_argument("--model-dir")
    infer.add_argument("--subject-x", type=float)
    infer.add_argument("--subject-y", type=float)
    infer.add_argument("--aspect", type=float)
    infer.add_argument("--measurements")
    infer.add_argument("--scene-id")
    args = parser.parse_args()
    try:
        result = analyze(args)
    except (OSError, ValueError, RuntimeError, InvalidObservation) as error:
        print(f"Analysis failed: {error}", file=sys.stderr)
        return 2
    return 0 if result["status"] == "ok" else 3


if __name__ == "__main__":
    raise SystemExit(main())
