from copy import deepcopy
import json
import unittest
from PIL import Image
from framecoach_vision.dataset import digest, measure, validate_review, draft
from framecoach_vision.domain import Scene


class DatasetTests(unittest.TestCase):
    def setUp(self):
        self.manifest = {"dataset_id": "batch", "items": [{"id": "FC-0001", "sha256": "image-hash"}]}
        self.annotations = [{"id": "FC-0001", "annotation_sha256": "label-hash", "perception": {"setting": "unknown", "subjects": [], "regions": [], "cues": [], "uncertainties": []}}]
        self.row = {"id": "FC-0001", "image_sha256": "image-hash", "annotation_sha256": "label-hash",
                    "photo_decision": "keep", "annotation_decision": "approve", "preference": "pending", "notes": "",
                    "corrected_perception": None, "human_labels": {}}
        self.payload = {"dataset_id": "batch", "manifest_sha256": digest(self.manifest), "records": [self.row]}

    def test_digest_is_order_independent_but_content_bound(self):
        self.assertEqual(digest({"b": 2, "a": 1}), digest({"a": 1, "b": 2}))
        self.assertNotEqual(digest({"a": 1}), digest({"a": 2}))

    def test_only_explicitly_kept_and_approved_rows_are_returned(self):
        self.assertEqual(len(validate_review(self.payload, self.manifest, self.annotations)), 1)
        self.row["annotation_decision"] = "pending"
        self.assertEqual(validate_review(self.payload, self.manifest, self.annotations), [])

    def test_stale_or_other_batch_exports_are_rejected(self):
        for field, value in [("dataset_id", "other"), ("manifest_sha256", "changed")]:
            p = deepcopy(self.payload); p[field] = value
            with self.assertRaises(ValueError): validate_review(p, self.manifest, self.annotations)
        self.row["image_sha256"] = "changed"
        with self.assertRaises(ValueError): validate_review(self.payload, self.manifest, self.annotations)

    def test_stale_annotation_and_duplicate_rows_are_rejected(self):
        p = deepcopy(self.payload); p["records"][0]["annotation_sha256"] = "old"
        with self.assertRaises(ValueError): validate_review(p, self.manifest, self.annotations)
        p = deepcopy(self.payload); p["records"].append(self.row)
        with self.assertRaises(ValueError): validate_review(p, self.manifest, self.annotations)

    def test_corrected_labels_require_valid_coordinates(self):
        self.row["annotation_decision"] = "corrected"
        with self.assertRaises(ValueError): validate_review(self.payload, self.manifest, self.annotations)
        self.row["corrected_perception"] = {"setting": "unknown", "subjects": [], "regions": [], "cues": [], "uncertainties": []}
        self.assertEqual(len(validate_review(self.payload, self.manifest, self.annotations)), 1)
        self.row["corrected_perception"]["regions"] = [{"kind": "sky", "box": [900, 0, 100, 1000]}]
        with self.assertRaises(ValueError): validate_review(self.payload, self.manifest, self.annotations)

    def test_human_text_is_bounded_and_does_not_accept_hidden_fields(self):
        self.row["human_labels"] = {"training_eligible": True}
        with self.assertRaises(ValueError): validate_review(self.payload, self.manifest, self.annotations)

    def test_failed_machine_output_cannot_be_promoted_by_approval_without_correction(self):
        self.annotations[0]["perception"] = None
        with self.assertRaises(ValueError): validate_review(self.payload, self.manifest, self.annotations)
        self.row["annotation_decision"] = "reject"
        self.assertEqual(validate_review(self.payload, self.manifest, self.annotations), [])

    def test_white_preview_is_measured_without_claiming_raw_overexposure(self):
        measured = measure(Image.new("RGB", (100, 100), "white"))
        self.assertEqual(measured["mean_luma"], 1)
        self.assertEqual(measured["bright_pixel_fraction_above_229"], 1)
        self.assertIn("RAW", measured["interpretation"])

    def test_machine_draft_never_invents_human_preference_or_approval(self):
        raw = json.dumps({"setting": "outdoor", "image_type": "photograph",
                          "subjects": [{"id": "s1", "kind": "person", "box": [200, 100, 500, 900]}], "regions": []})
        scene = Scene.from_wire(raw)
        result = draft({"id": "FC-1", "sha256": "photo", "group": "portrait"}, scene, raw, {}, None,
                       Image.new("RGB", (800, 600)), {"model_id": "test-double", "revision": "test"}, "prompt")
        self.assertFalse(result["training_eligible"])
        self.assertIsNone(result["preference_label"])
        self.assertIsNone(result["human_labels"]["light_direction"])
        self.assertEqual(result["status"], "machine_draft_pending_human_review")


if __name__ == "__main__": unittest.main()
