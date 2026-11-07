"""Auditable draft labels and human review binding; no automatic human approval."""
import hashlib
import json
from .domain import Scene
from .planner import make_plans


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                    separators=(",", ":"), allow_nan=False).encode("utf-8")).hexdigest()


def measure(image):
    hist = image.convert("L").resize((128, 128)).histogram()
    count = sum(hist)
    return {"method": "128x128_rendered_preview_luma_v1", "mean_luma": round(sum(i*n for i, n in enumerate(hist)) / count / 255, 4),
            "dark_pixel_fraction_below_26": round(sum(hist[:26]) / count, 4),
            "bright_pixel_fraction_above_229": round(sum(hist[230:]) / count, 4),
            "interpretation": "显示图亮度统计；不是 RAW 曝光、真实照度或光源方向测量。"}


def draft(item, scene, raw, performance, failure, image, observer_lock, prompt_sha):
    intent = "subject" if item["group"] == "portrait" else "landscape"
    guidance = make_plans(scene, *image.size, intent=intent, failure=failure)
    flags = []
    people = [s for s in scene.subjects if s.kind == "person"] if scene else []
    if failure: flags.append("模型输出未通过协议校验")
    if item["group"] == "portrait" and not people: flags.append("来源为人像候选，但模型未检出人物")
    if len(people) > 1: flags.append("多人场景，请确认主角")
    if scene is not None:
        wire = json.loads(raw)
        if wire["image_type"] != "photograph": flags.append("模型未将图片判断为照片，请复核")
    if guidance["selected_subject_id"] is None: flags.append("未选出可靠主体，建议人工框选")
    selected = next((s for s in scene.subjects if s.id == guidance["selected_subject_id"]), None) if scene else None
    geometry = None
    if selected:
        box = selected.box; cx, cy = box.center
        geometry = {"source": "visual_model_box_estimate", "selected_id": selected.id,
                    "center_normalized": [round(cx, 2), round(cy, 2)],
                    "image_area_fraction": round(box.area / 1_000_000, 4),
                    "margins_normalized": [box.x1, box.y1, 1000-box.x2, 1000-box.y2],
                    "interpretation": "根据模型框计算，不代表身体所有部位都已检出。"}
    result = {"schema_version": 1, "id": item["id"], "image_sha256": item["sha256"],
              "status": "machine_draft_pending_human_review", "training_eligible": False,
              "base_model": observer_lock["model_id"], "base_revision": observer_lock["revision"],
              "prompt_sha256": prompt_sha, "image_size": list(image.size),
              "coordinates": "upright_image_normalized_0_1000",
              "perception": scene.to_dict() if scene else None, "raw_model_output": raw,
              "validation_error": failure, "performance": performance,
              "measurements": measure(image), "subject_geometry": geometry,
              "guidance": guidance, "review_flags": flags,
              "human_labels": {"pose_description": None, "expression_direction": None,
                               "light_direction": None, "photographic_intent": None,
                               "preferred_plan": None},
              "tutorial_checks": [
                  {"source_id": "nikon-background", "question": "背景中的线条或杂物是否干扰主体？完整画幅和收紧取景各保留了什么？", "type": "review_question"},
                  {"source_id": "kelby-landscape", "question": "天空、主体和前景中，哪部分最值得保留？若有水面，请人工确认倒影是否真实可见。", "type": "review_question"}],
              "preference_label": None, "preference_label_status": "requires_human_choice",
              "notes": "社区精选仅是素材筛选依据；原图不自动标为优于裁剪，也不由模型替人类投票。"}
    result["annotation_sha256"] = digest(result)
    return result


def validate_review(payload, manifest, annotations):
    """Reject exports for another batch, changed images or stale machine annotations."""
    if not isinstance(payload, dict) or payload.get("dataset_id") != manifest["dataset_id"] or payload.get("manifest_sha256") != digest(manifest):
        raise ValueError("Review does not match this exact dataset manifest")
    if not isinstance(payload.get("records"), list) or len(payload["records"]) > len(manifest["items"]):
        raise ValueError("Invalid review records")
    items = {i["id"]: i for i in manifest["items"]}
    labels = {a["id"]: a for a in annotations}
    seen = set()
    approved = []
    for row in payload["records"]:
        if not isinstance(row, dict): raise ValueError("Invalid review row")
        ident = row.get("id")
        if not isinstance(ident, str) or ident not in items or ident in seen:
            raise ValueError("Unknown or duplicate photo ID")
        seen.add(ident)
        if row.get("image_sha256") != items[ident]["sha256"] or row.get("annotation_sha256") != labels[ident]["annotation_sha256"]:
            raise ValueError("Review points to stale image or annotation")
        if row.get("photo_decision") not in {"pending", "keep", "reject"} or row.get("annotation_decision") not in {"pending", "approve", "corrected", "reject"}:
            raise ValueError("Invalid review decision")
        if row.get("preference") not in {"pending", "A", "B", "tie", "neither"}:
            raise ValueError("Invalid preference")
        if not isinstance(row.get("notes", ""), str) or len(row.get("notes", "")) > 10000:
            raise ValueError("Invalid reviewer note")
        human = row.get("human_labels", {})
        if not isinstance(human, dict) or set(human) - {"pose_description", "expression_direction", "light_direction", "photographic_intent"}:
            raise ValueError("Invalid human label fields")
        if any(not isinstance(v, str) or len(v) > 10000 for v in human.values()):
            raise ValueError("Invalid human label text")
        corrected = row.get("corrected_perception")
        if row["annotation_decision"] == "approve" and labels[ident].get("perception") is None:
            raise ValueError("Failed machine observations require correction or rejection, not approval")
        if row["annotation_decision"] == "corrected":
            if corrected is None: raise ValueError("Corrected decision requires corrected labels")
            Scene.parse(json.dumps(corrected, allow_nan=False))
        elif corrected is not None:
            raise ValueError("Corrected labels require a corrected decision")
        # Human approval is necessary, but rights/quality admission remains a separate step.
        if row["photo_decision"] == "keep" and row["annotation_decision"] in {"approve", "corrected"}:
            approved.append(row)
    return approved
