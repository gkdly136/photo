import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

from PIL import Image
from framecoach_vision.cli import analyze
from framecoach_vision.domain import Scene
from framecoach_vision.planner import subject_crop
from framecoach_vision.domain import Box


class StubObserver:
    """Only a pipeline test double; these tests DO NOT evaluate a neural model."""
    lock = {"model_id": "test-double", "revision": "test-only"}

    def __init__(self, failure=None):
        self.calls = 0
        self.failure = failure

    def observe(self, image):
        self.calls += 1
        raw = json.dumps({"setting": "unknown", "subjects": [], "regions": [], "cues": [],
                          "uncertainties": ["<script>alert('test')</script>"]})
        return (None if self.failure else Scene.parse(raw), raw,
                {"inference_ms": 0, "device": "test-double"}, self.failure)


class PipelineTests(unittest.TestCase):
    def args(self, image, output, **changes):
        fields = dict(image=image, output=output, scene_id=None, subject_x=None, subject_y=None,
                      measurements=None, model_dir=None, device="cpu", intent="subject",
                      mood="natural", aspect=None)
        fields.update(changes)
        return SimpleNamespace(**fields)

    def test_artifacts_are_offline_and_escape_model_text(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / "image.png"
            Image.new("RGB", (160, 120)).save(source)
            result = analyze(self.args(source, root / "report"), StubObserver())
            self.assertEqual(result["status"], "ok")
            self.assertFalse(result["photography_finetuned"])
            report = (root / "report/report.html").read_text(encoding="utf-8")
            self.assertNotIn("<script>", report)
            self.assertIn("&lt;script&gt;", report)
            self.assertIn("img-src data:", report)
            self.assertEqual(json.loads((root / "report/result.json").read_text(encoding="utf-8")), result)

    def test_exif_rotation_applies_before_geometry_and_report(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / "rotated.jpg"
            image = Image.new("RGB", (160, 120)); exif = Image.Exif(); exif[274] = 6
            image.save(source, exif=exif)
            result = analyze(self.args(source, root / "report"), StubObserver())
            self.assertEqual(result["image_size"], [120, 160])

    def test_invalid_ui_input_rejected_before_model_inference(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / "image.png"
            Image.new("RGB", (160, 120)).save(source)
            for changes in [dict(aspect=0), dict(aspect=float("nan")),
                            dict(subject_x=-1, subject_y=50), dict(subject_x=50)]:
                observer = StubObserver()
                with self.assertRaises(ValueError):
                    analyze(self.args(source, root / "report", **changes), observer)
                self.assertEqual(observer.calls, 0)

    def test_model_parse_failure_is_saved_as_fallback(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / "image.png"
            Image.new("RGB", (160, 120)).save(source)
            result = analyze(self.args(source, root / "report"), StubObserver("bad JSON"))
            self.assertEqual(result["status"], "fallback")
            self.assertIsNone(result["perception"])
            self.assertEqual(result["validation_error"], "bad JSON")

    def test_zero_aspect_cannot_silently_select_default(self):
        with self.assertRaises(ValueError):
            subject_crop(Box(400, 400, 600, 600), 800, 600, 0)


if __name__ == "__main__":
    unittest.main()
