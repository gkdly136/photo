from dataclasses import dataclass
import json
import math
from typing import Any

KINDS = {"person", "object", "landmark"}
REGIONS = {"sky", "water", "vegetation", "road", "building", "wall", "window", "ground", "other"}
CUES = {"leading_lines", "busy_background", "reflection", "strong_verticals", "dark_subject"}
FRAMING = {"full_body", "upper_body", "face_only", "unknown"}


class InvalidObservation(ValueError):
    pass


def number(value: Any, low=0, high=1000) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not low <= value <= high:
        raise InvalidObservation(f"Expected a finite number between {low} and {high}")
    return float(value)


def bounded_text(value: Any, maximum=240) -> str:
    if not isinstance(value, str) or len(value) > maximum:
        raise InvalidObservation("Invalid text field")
    return value


@dataclass(frozen=True)
class Box:
    x1: float
    y1: float
    x2: float
    y2: float

    @classmethod
    def parse(cls, value):
        if not isinstance(value, list) or len(value) != 4:
            raise InvalidObservation("A box must contain four coordinates")
        box = cls(*(number(v) for v in value))
        if box.x1 >= box.x2 or box.y1 >= box.y2:
            raise InvalidObservation("Box has zero or reversed extent")
        return box

    @property
    def width(self): return self.x2 - self.x1

    @property
    def height(self): return self.y2 - self.y1

    @property
    def area(self): return self.width * self.height

    @property
    def center(self): return ((self.x1 + self.x2) / 2, (self.y1 + self.y2) / 2)

    def contains(self, other):
        return self.x1 <= other.x1 and self.y1 <= other.y1 and self.x2 >= other.x2 and self.y2 >= other.y2

    def contains_point(self, x, y): return self.x1 <= x <= self.x2 and self.y1 <= y <= self.y2

    def values(self): return [round(self.x1, 2), round(self.y1, 2), round(self.x2, 2), round(self.y2, 2)]


@dataclass(frozen=True)
class Subject:
    id: str
    kind: str
    box: Box
    framing: str


@dataclass(frozen=True)
class Region:
    kind: str
    box: Box


@dataclass(frozen=True)
class Scene:
    setting: str
    subjects: tuple[Subject, ...]
    regions: tuple[Region, ...]
    cues: tuple[str, ...]
    uncertainties: tuple[str, ...]

    @classmethod
    def from_wire(cls, raw):
        """Minimal neural wire format. No invented pose/framing/confidence attributes."""
        if len(raw) > 16000:
            raise InvalidObservation("Model output is too large")
        try:
            data = json.loads(raw)
        except (json.JSONDecodeError, TypeError) as error:
            raise InvalidObservation("Model did not return valid JSON") from error
        if not isinstance(data, dict) or set(data) != {"setting", "image_type", "subjects", "regions"}:
            raise InvalidObservation("Unexpected wire observation fields")
        image_type = data["image_type"]
        names = {"photograph": "照片", "illustration": "插图", "screenshot": "屏幕截图",
                 "blank": "空白画面", "unknown": "未知类型"}
        if not isinstance(image_type, str) or image_type not in names:
            raise InvalidObservation("Invalid image type")
        if not isinstance(data["subjects"], list) or len(data["subjects"]) > 3 or not isinstance(data["regions"], list) or len(data["regions"]) > 4:
            raise InvalidObservation("Invalid wire observation size")
        for item in data["subjects"]:
            if not isinstance(item, dict) or set(item) != {"id", "kind", "box"}:
                raise InvalidObservation("Invalid wire subject fields")
        if image_type == "blank" and (data["subjects"] or data["regions"] or data["setting"] != "unknown"):
            raise InvalidObservation("Blank image cannot also contain a scene")
        canonical = {"setting": data["setting"],
                     "subjects": [dict(s, framing="unknown") for s in data["subjects"]],
                     "regions": data["regions"], "cues": [],
                     "uncertainties": [f"模型将输入识别为{names[image_type]}，该判断可能有误。",
                                       "主体与背景均为模型粗略估计；没有校准置信度或姿态诊断。"]}
        return cls.parse(json.dumps(canonical, ensure_ascii=False))

    @classmethod
    def parse(cls, raw: str):
        if len(raw) > 16000:
            raise InvalidObservation("Model output is too large")
        text = raw.strip()
        if text.startswith("```json") and text.endswith("```"):
            text = text[7:-3].strip()
        elif text.startswith("```") and text.endswith("```"):
            text = text[3:-3].strip()
        try:
            data = json.loads(text, parse_constant=lambda _: (_ for _ in ()).throw(InvalidObservation("Non-finite JSON")))
        except (json.JSONDecodeError, TypeError) as error:
            raise InvalidObservation("Model did not return valid JSON") from error
        if not isinstance(data, dict) or set(data) != {"setting", "subjects", "regions", "cues", "uncertainties"}:
            raise InvalidObservation("Unexpected observation fields")
        if not isinstance(data["setting"], str) or data["setting"] not in {"outdoor", "indoor", "unknown"}:
            raise InvalidObservation("Unknown setting")
        for key, limit in [("subjects", 8), ("regions", 12), ("cues", 5), ("uncertainties", 6)]:
            if not isinstance(data[key], list) or len(data[key]) > limit:
                raise InvalidObservation(f"Invalid {key} list")
        subjects = []
        ids = set()
        for item in data["subjects"]:
            if not isinstance(item, dict) or set(item) != {"id", "kind", "box", "framing"}:
                raise InvalidObservation("Invalid subject")
            ident = bounded_text(item["id"], 32)
            if not ident or ident in ids or not isinstance(item["kind"], str) or item["kind"] not in KINDS or not isinstance(item["framing"], str) or item["framing"] not in FRAMING:
                raise InvalidObservation("Invalid or duplicate subject label")
            ids.add(ident)
            subjects.append(Subject(ident, item["kind"], Box.parse(item["box"]), item["framing"]))
        regions = []
        for item in data["regions"]:
            if not isinstance(item, dict) or set(item) != {"kind", "box"} or not isinstance(item["kind"], str) or item["kind"] not in REGIONS:
                raise InvalidObservation("Invalid region")
            regions.append(Region(item["kind"], Box.parse(item["box"])))
        if any(not isinstance(c, str) or c not in CUES for c in data["cues"]):
            raise InvalidObservation("Invalid visual cue")
        return cls(data["setting"], tuple(subjects), tuple(regions), tuple(data["cues"]),
                   tuple(bounded_text(t) for t in data["uncertainties"]))

    def to_dict(self):
        return {"setting": self.setting,
                "subjects": [{"id": s.id, "kind": s.kind, "box": s.box.values(), "framing": s.framing} for s in self.subjects],
                "regions": [{"kind": r.kind, "box": r.box.values()} for r in self.regions],
                "cues": list(self.cues), "uncertainties": list(self.uncertainties)}


@dataclass(frozen=True)
class Measurement:
    """Optional, independently measured geometry from the SAME upright image."""
    faces: tuple[Box, ...] = ()
    joints: dict | None = None

    @classmethod
    def parse(cls, data, width, height, scene_id):
        if not isinstance(data, dict) or set(data) - {"scene_id", "image_width", "image_height", "faces", "joints"}:
            raise InvalidObservation("Invalid measurement fields")
        if data.get("scene_id") != scene_id or data.get("image_width") != width or data.get("image_height") != height:
            raise InvalidObservation("Measurement belongs to another image or scene")
        faces = data.get("faces", [])
        joints = data.get("joints", {})
        if not isinstance(faces, list) or len(faces) > 32 or not isinstance(joints, dict) or len(joints) > 33:
            raise InvalidObservation("Invalid measurement size")
        clean = {}
        for key, point in joints.items():
            if not isinstance(key, str) or not isinstance(point, list) or len(point) != 3:
                raise InvalidObservation("Invalid joint")
            x, y, likelihood = number(point[0]), number(point[1]), number(point[2], 0, 1)
            if likelihood >= .65:
                clean[key] = (x, y, likelihood)
        return cls(tuple(Box.parse(box) for box in faces), clean)


def select_subject(scene: Scene, intent, point=None):
    candidates = list(scene.subjects)
    if point is not None:
        x, y = number(point[0]), number(point[1])
        hits = [s for s in candidates if s.box.contains_point(x, y)]
        # A miss is explicit; never silently switch the user's selected protagonist.
        return min(hits, key=lambda s: s.box.area) if hits else None
    people = [s for s in candidates if s.kind == "person"]
    preferred = people if people and intent != "landscape" else [s for s in candidates if s.kind != "person"]
    return max(preferred, key=lambda s: s.box.area) if preferred else None
