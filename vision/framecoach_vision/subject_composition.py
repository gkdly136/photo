"""Subject-first crop annotation inspired by SACD, not a trained SAC-Net model."""
from .domain import Box


def iou(a, b):
    a = Box.parse(list(a)); b = Box.parse(list(b))
    intersection = max(0, min(a.x2, b.x2)-max(a.x1, b.x1)) * max(0, min(a.y2, b.y2)-max(a.y1, b.y1))
    return intersection / (a.area+b.area-intersection)


def grid_windows():
    # 10 horizontal intervals x 10 vertical intervals = 100 windows on the 5x5 grid.
    return [[200*x1, 200*y1, 200*x2, 200*y2]
            for y1 in range(4) for y2 in range(y1+2, 6)
            for x1 in range(4) for x2 in range(x1+2, 6)]


def candidates(subject_box, maximum_iou=.90):
    if subject_box is None:
        return {"method":"sacd_inspired_subject_conditioned_grid_v1", "trained_on_sacd":False,
                "grid_window_count":100, "eligible_window_count":0, "subject_box":None,
                "windows":[], "notice":"先确认主体，才能按主体筛选构图；没有自动编造两套相同方案。"}
    subject = Box.parse(list(subject_box))
    eligible = [b for b in grid_windows() if Box.parse(b).contains(subject)]
    # Original first as a baseline only, never an automatic positive/aesthetic winner.
    eligible.sort(key=lambda b: -(b[2]-b[0])*(b[3]-b[1]))
    kept = []
    for box in eligible:
        if all(iou(box, other) < maximum_iou for other in kept): kept.append(box)
    windows = [{"id": f"crop-{i+1:03d}", "box": b, "kind":"original" if b == [0,0,1000,1000] else "grid_candidate",
                "preference":None, "aesthetic_score":None} for i,b in enumerate(kept)]
    return {"method":"sacd_inspired_subject_conditioned_grid_v1", "trained_on_sacd":False,
            "grid_window_count":100, "eligible_window_count":len(eligible), "subject_box":list(subject_box),
            "max_pair_iou":maximum_iou, "windows":windows,
            "notice":"候选都包含所选主体框；顺序不是审美排名。请保留多个可接受构图，再选择最喜欢的一张。" if len(windows)>1 else "主体已占据较大画幅，没有足够不同的裁剪候选；不强行生成 A/B。"}
