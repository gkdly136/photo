"""Token-time JSON format constraints; domain.py independently validates semantics."""
from .domain import KINDS, REGIONS


def record(properties):
    return {"type": "object", "properties": properties, "required": list(properties),
            "additionalProperties": False}


def enum(values):
    return {"type": "string", "enum": sorted(values)}


def array(items, maximum):
    return {"type": "array", "items": items, "maxItems": maximum}


BOX_SCHEMA = {"type": "array", "items": {"type": "integer", "minimum": 0, "maximum": 1000},
              "minItems": 4, "maxItems": 4}
OBSERVATION_SCHEMA = record({
    "setting": enum(["outdoor", "indoor", "unknown"]),
    "image_type": enum(["photograph", "illustration", "screenshot", "blank", "unknown"]),
    "subjects": array(record({"id": {"type": "string", "minLength": 1, "maxLength": 32},
                              "kind": enum(KINDS), "box": BOX_SCHEMA}), 3),
    "regions": array(record({"kind": enum(REGIONS), "box": BOX_SCHEMA}), 4),
})
