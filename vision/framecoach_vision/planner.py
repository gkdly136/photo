"""Original photography rules. This module is not a trained aesthetic ranker."""
from .domain import Box, Measurement, Scene, Subject, select_subject

FULL = Box(0, 0, 1000, 1000)


def subject_crop(box: Box, width: int, height: int, aspect=None):
    if width <= 0 or height <= 0:
        raise ValueError("Invalid image dimensions")
    ratio = width / height if aspect is None else aspect
    if not .25 <= ratio <= 4:
        raise ValueError("Unsupported aspect ratio")
    # Normalized x/y units have different pixel lengths in a non-square image.
    k = ratio * height / width
    crop_h = max(box.height * 1.25, box.width * 1.25 / k, 150)
    crop_w = crop_h * k
    if crop_h > 1000 or crop_w > 1000:
        return FULL
    cx, cy = box.center
    x1 = min(max(cx - crop_w / 2, 0), 1000 - crop_w)
    y1 = min(max(cy - crop_h / 2, 0), 1000 - crop_h)
    crop = Box(x1, y1, x1 + crop_w, y1 + crop_h)
    return crop if crop.contains(box) else FULL


def tip(category, instruction, evidence, basis="creative_option", sources=None):
    return {"category": category, "instruction": instruction, "evidence": evidence,
            "basis": basis, "source_ids": sources or ["freeman"]}


def body_tip(subject: Subject | None, measurement: Measurement | None, mood: str):
    # One pose stream cannot be assigned to an arbitrary person in a group.
    if subject is not None and measurement and len(measurement.faces) == 1:
        j = measurement.joints or {}
        needed = ["LEFT_WRIST", "RIGHT_WRIST", "LEFT_HIP", "RIGHT_HIP"]
        if all(k in j for k in needed) and j["LEFT_WRIST"][1] >= j["LEFT_HIP"][1] and j["RIGHT_WRIST"][1] >= j["RIGHT_HIP"][1]:
            # Require that the independent face lies within the selected subject as well.
            face = measurement.faces[0]
            if subject.box.contains_point(*face.center):
                return tip("动作", "试着一只手轻扶衣角，另一只手自然放松。", "同帧单个人脸位于所选人物内，双侧手腕/髋部入画概率达阈值，手腕低于髋部；二维位置不评价姿态好坏。", "measured_geometry", ["nikon-background"])
    if subject is None or subject.kind != "person":
        return tip("观察", "先确认你最想保留的景物，再比较两种取景。", "没有可靠的已选人物，不给当前姿态诊断。", sources=["shore-nature"])
    instruction = {"natural": "肩膀放松，轻轻呼气；试一个自己舒服的微侧身版本。",
                   "quiet": "保持舒适站姿或坐姿，目光停在画外，停留一个呼吸。",
                   "lively": "在原地轻轻转头、打个招呼，比较前后两个瞬间。"}[mood]
    return tip("动作", instruction, "按用户选择的氛围提供可选动作；视觉模型没有测量关节角度，也没有识别情绪。", sources=["nikon-background", "moma-posed"])


def make_plans(scene: Scene | None, width, height, intent="subject", mood="natural", point=None,
               aspect=None, measurement=None, failure=None):
    if intent not in {"subject", "environment", "landscape"} or mood not in {"natural", "quiet", "lively"}:
        raise ValueError("Invalid photographic intent or mood")
    if width <= 0 or height <= 0:
        raise ValueError("Invalid image size")
    subject = select_subject(scene, intent, point) if scene else None
    regions = {r.kind for r in scene.regions} if scene else set()
    warnings = []
    if failure:
        warnings.append("视觉分析未得到可用结构化结果；以下为明确标识的通用创作方案。")
    if point and subject is None:
        warnings.append("点击位置未匹配到模型识别的主体；请重新选择，没有自动改选其他人物。")
    if scene and len([s for s in scene.subjects if s.kind == "person"]) > 1:
        warnings.append("画面中有多个人物，建议点选主角；未将单人骨架关联到多人场景。")
        measurement = None
    uncertainties = list(scene.uncertainties) if scene else ["视觉模型不可用"]
    crop = subject_crop(subject.box, width, height, aspect) if subject else FULL
    close = []
    if subject:
        close.append(tip("构图", "参考框保留已识别主体，比较收紧画面后是否更突出主角。" if crop != FULL else "主体已接近画面边界，先保留原画幅，检查头顶与脚边。",
                         "裁剪框经过几何包含校验；主体框来自视觉模型估计，不是像素级分割。", "model_geometry"))
    else:
        close.append(tip("构图", "先选择主体，观察画面边缘有没有分散注意力的元素。",
                         "没有可靠的已选主体；不画假定的人脸落点。"))
    close.append(body_tip(subject, measurement, mood))
    close.append(tip("表情" if subject and subject.kind == "person" else "观察",
                     {"natural": "像和朋友聊天一样，呼气后看向镜头旁。", "quiet": "让目光停在画外，嘴角自然放松。", "lively": "在原地打个招呼，抓住自然的一瞬。"}[mood] if subject and subject.kind == "person" else "先说出你想留下的形状、光或故事，再决定是否收紧画面。",
                     "来自用户氛围选择与创作意图，不是情绪检测结果。", sources=["moma-posed", "shore-nature"]))
    wide = [tip("构图", "保留完整画面，比较主角与周围环境的关系。", "保留原画幅是可比较的创作方案，不表示模型已经证明它更美。")]
    if "road" in regions:
        wide.append(tip("环境", "观察道路边线是否自然引向主角；适合时把它作为视觉线索。", "模型估计画面含道路；道路框不代表精确引导线或可行走路线。", "model_hypothesis"))
    elif "window" in regions:
        wide.append(tip("光线", "比较人物朝向窗户前后的脸部明暗，选择你更喜欢的一版。", "模型估计画面含窗户；窗户位置不能证明真实光源方向。", "model_hypothesis", ["risd-light"]))
    elif "sky" in regions and ("water" in regions or "ground" in regions):
        wide.append(tip("环境", "比较天空与地面或水面，决定这一张更想强调哪一部分。", "模型估计了天空及地面/水面的粗略区域；没有自动测量真实水平线。", "model_hypothesis", ["kelby-landscape"]))
    elif "vegetation" in regions:
        wide.append(tip("环境", "观察树影或枝叶能否作为边框，同时检查它们是否遮挡主体。", "模型估计画面含植被；没有测量真实遮挡深度。", "model_hypothesis"))
    elif "building" in regions or "wall" in regions:
        wide.append(tip("环境", "观察建筑或墙面的线条，比较整齐排列与有意倾斜的效果。", "模型估计画面含建筑或墙面；尚未校准镜头透视。", "model_hypothesis"))
    else:
        wide.append(tip("环境", "比较前景、主体、背景的关系，先保留你认为重要的内容。", "没有足够的背景语义依据；使用通用观察提示。"))
    wide.append(tip("动作" if subject and subject.kind == "person" else "观察",
                    "保持舒适姿态，试着望向画面内的景物，让目光与环境发生联系。" if subject and subject.kind == "person" else "观察近处是否有可用于表达层次的元素，再决定是否纳入画面。",
                    "环境叙事创作选项；不评价人物情绪，不给物理移动距离。", sources=["shore-nature", "moma-context"]))
    plans = [{"id": "subject", "title": "突出主体", "crop": crop.values(), "tips": close},
             {"id": "environment", "title": "保留环境", "crop": FULL.values(), "tips": wide}]
    if intent in {"environment", "landscape"}:
        plans.reverse()
    return {"planner": "deterministic_photography_rules_v1", "aesthetic_ranker_trained": False,
            "selected_subject_id": subject.id if subject else None, "plans": plans,
            "warnings": warnings, "uncertainties": uncertainties,
            "geometry_source": "upstream_visual_model_estimate" if scene else "none"}
