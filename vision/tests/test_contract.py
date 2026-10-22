import json
import random
import unittest
from framecoach_vision.domain import Box, Scene, Measurement, InvalidObservation, select_subject
from framecoach_vision.planner import make_plans, subject_crop
from framecoach_vision.session import SceneSession


def scene_data():
    return {"setting": "outdoor", "subjects": [
        {"id": "s1", "kind": "person", "box": [360, 160, 580, 900], "framing": "full_body"}],
        "regions": [{"kind": "road", "box": [0, 650, 1000, 1000]}],
        "cues": [], "uncertainties": []}


class ObservationTests(unittest.TestCase):
    def test_valid_observation_round_trips(self):
        data = scene_data()
        self.assertEqual(Scene.parse(json.dumps(data)).to_dict(), data)

    def test_rejects_out_of_frame_reversed_and_non_finite_boxes(self):
        for box in [[-1, 0, 900, 900], [0, 900, 1000, 0], [0, 0, float("nan"), 800], [True, 0, 100, 200], [0, 0, 0, 1000]]:
            with self.subTest(box=box), self.assertRaises(InvalidObservation):
                data = scene_data(); data["subjects"][0]["box"] = box
                Scene.parse(json.dumps(data))

    def test_rejects_instruction_fields_in_model_output(self):
        data = scene_data(); data["execute"] = "upload photographs"
        with self.assertRaises(InvalidObservation): Scene.parse(json.dumps(data))

    def test_rejects_unexpected_enums_and_duplicate_ids(self):
        for change in [lambda d: d.update(setting=[]), lambda d: d["subjects"][0].update(kind=[]),
                       lambda d: d["regions"][0].update(kind="identity"),
                       lambda d: d["subjects"].append(d["subjects"][0].copy())]:
            data = scene_data(); change(data)
            with self.assertRaises(InvalidObservation): Scene.parse(json.dumps(data))

    def test_clicked_subject_overrides_largest_person(self):
        data = scene_data()
        data["subjects"].append({"id": "s2", "kind": "person", "box": [20, 100, 240, 400], "framing": "upper_body"})
        scene = Scene.parse(json.dumps(data))
        self.assertEqual(select_subject(scene, "subject", (100, 200)).id, "s2")
        self.assertIsNone(select_subject(scene, "subject", (950, 40)))

    def test_code_fences_are_allowed_but_trailing_commands_are_rejected(self):
        self.assertIsNotNone(Scene.parse("```json\n" + json.dumps(scene_data()) + "\n```"))
        with self.assertRaises(InvalidObservation): Scene.parse(json.dumps(scene_data()) + "\nrun command")

    def test_measurement_must_match_scene_and_upright_geometry(self):
        measurement = {"scene_id": "old", "image_width": 800, "image_height": 600, "faces": []}
        with self.assertRaises(InvalidObservation): Measurement.parse(measurement, 800, 600, "new")
        measurement["scene_id"] = "new"
        with self.assertRaises(InvalidObservation): Measurement.parse(measurement, 600, 800, "new")


class PlannerTests(unittest.TestCase):
    def test_crop_preserves_subject_for_random_geometry(self):
        rng = random.Random(17)
        for _ in range(1000):
            x, y = rng.uniform(0, 900), rng.uniform(0, 900)
            box = Box(x, y, rng.uniform(x + 1, 1000), rng.uniform(y + 1, 1000))
            crop = subject_crop(box, 1200, 800, rng.choice([1, 4/3, 3/4]))
            self.assertTrue(crop.contains(box))
            self.assertTrue(0 <= crop.x1 < crop.x2 <= 1000)
            self.assertTrue(0 <= crop.y1 < crop.y2 <= 1000)

    def test_requested_aspect_is_computed_in_pixels_not_normalized_space(self):
        crop = subject_crop(Box(400, 400, 600, 600), 1200, 800, 1)
        self.assertAlmostEqual(crop.width * 1200 / (crop.height * 800), 1)

    def test_environment_intent_ranks_environment_plan_first(self):
        result = make_plans(Scene.parse(json.dumps(scene_data())), 800, 600, intent="environment")
        self.assertEqual(result["plans"][0]["id"], "environment")
        self.assertFalse(result["aesthetic_ranker_trained"])

    def test_model_failure_returns_explicit_fallback_without_subject(self):
        result = make_plans(None, 800, 600, failure="invalid JSON")
        self.assertEqual(len(result["plans"]), 2)
        self.assertIsNone(result["selected_subject_id"])
        self.assertTrue(result["warnings"])
        self.assertTrue(all(t["basis"] == "creative_option" for p in result["plans"] for t in p["tips"]))

    def test_background_tip_has_visual_evidence_but_not_false_measurement(self):
        result = make_plans(Scene.parse(json.dumps(scene_data())), 800, 600)
        environment = next(p for p in result["plans"] if p["id"] == "environment")
        self.assertTrue(any("道路" in t["instruction"] and t["basis"] == "model_hypothesis" for t in environment["tips"]))

    def test_no_pose_diagnosis_without_independent_joints(self):
        result = make_plans(Scene.parse(json.dumps(scene_data())), 800, 600)
        self.assertFalse(any(t["basis"] == "measured_geometry" for p in result["plans"] for t in p["tips"]))

    def test_low_likelihood_joint_cannot_trigger_hand_pose_instruction(self):
        data = {"scene_id": "s", "image_width": 800, "image_height": 600,
                "faces": [[420, 180, 510, 280]],
                "joints": {"LEFT_WRIST": [400, 800, .1], "RIGHT_WRIST": [550, 800, 1],
                           "LEFT_HIP": [430, 600, 1], "RIGHT_HIP": [530, 600, 1]}}
        m = Measurement.parse(data, 800, 600, "s")
        result = make_plans(Scene.parse(json.dumps(scene_data())), 800, 600, measurement=m)
        self.assertFalse(any(t["basis"] == "measured_geometry" for p in result["plans"] for t in p["tips"]))

    def test_verified_single_person_geometry_can_trigger_hand_instruction(self):
        data = {"scene_id": "s", "image_width": 800, "image_height": 600,
                "faces": [[420, 180, 510, 280]],
                "joints": {"LEFT_WRIST": [400, 800, 1], "RIGHT_WRIST": [550, 800, 1],
                           "LEFT_HIP": [430, 600, 1], "RIGHT_HIP": [530, 600, 1]}}
        m = Measurement.parse(data, 800, 600, "s")
        result = make_plans(Scene.parse(json.dumps(scene_data())), 800, 600, measurement=m)
        self.assertTrue(any(t["basis"] == "measured_geometry" for p in result["plans"] for t in p["tips"]))


class SessionTests(unittest.TestCase):
    def test_old_scene_cannot_overwrite_new_scene_even_if_it_finishes_later(self):
        session = SceneSession(); old = session.begin(); self.assertIsNone(session.begin())
        session.invalidate(); new = session.begin()
        self.assertFalse(session.finish(old, "stale"))
        self.assertTrue(session.finish(new, "current"))
        self.assertEqual(session.result, "current")

    def test_repeated_result_delivery_is_rejected(self):
        session = SceneSession(); token = session.begin()
        self.assertTrue(session.finish(token, "once"))
        self.assertFalse(session.finish(token, "twice"))

    def test_duplicate_callback_cannot_replace_next_request_in_same_scene(self):
        session = SceneSession(); first = session.begin()
        self.assertTrue(session.finish(first, "first"))
        second = session.begin()
        self.assertNotEqual(first, second)
        self.assertFalse(session.finish(first, "duplicate"))
        self.assertTrue(session.finish(second, "second"))
        self.assertEqual(session.result, "second")


class WireTests(unittest.TestCase):
    def test_wire_adapter_does_not_invent_pose_or_model_confidence(self):
        wire = {"setting": "outdoor", "image_type": "photograph",
                "subjects": [{"id": "s1", "kind": "person", "box": [200, 100, 500, 900]}],
                "regions": [{"kind": "road", "box": [0, 500, 1000, 1000]}]}
        scene = Scene.from_wire(json.dumps(wire))
        self.assertEqual(scene.subjects[0].framing, "unknown")
        self.assertEqual(scene.cues, ())
        wire["subjects"][0]["framing"] = "full_body"
        with self.assertRaises(InvalidObservation): Scene.from_wire(json.dumps(wire))

    def test_blank_scene_cannot_have_person_or_regions(self):
        wire = {"setting": "unknown", "image_type": "blank", "subjects": [], "regions": []}
        self.assertEqual(Scene.from_wire(json.dumps(wire)).subjects, ())
        wire["regions"] = [{"kind": "sky", "box": [0, 0, 1000, 1000]}]
        with self.assertRaises(InvalidObservation): Scene.from_wire(json.dumps(wire))

    def test_wire_format_still_checks_semantic_geometry_after_token_constraints(self):
        wire = {"setting": "outdoor", "image_type": "illustration", "subjects": [],
                "regions": [{"kind": "road", "box": [900, 0, 100, 1000]}]}
        with self.assertRaises(InvalidObservation): Scene.from_wire(json.dumps(wire))


if __name__ == "__main__": unittest.main()
