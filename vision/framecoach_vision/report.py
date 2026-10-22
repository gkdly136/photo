import base64
import html
from io import BytesIO
import json


def render_report(image, result):
    image_bytes = BytesIO()
    preview = image.copy()
    preview.thumbnail((1280, 1280))
    preview.save(image_bytes, format="PNG")
    data_uri = "data:image/png;base64," + base64.b64encode(image_bytes.getvalue()).decode("ascii")
    escape = html.escape
    labels = {"model_geometry": "模型粗框", "model_hypothesis": "视觉推测",
              "measured_geometry": "同帧几何测量", "creative_option": "创作选项"}
    cards = []
    for plan in result["guidance"]["plans"]:
        x1, y1, x2, y2 = plan["crop"]
        tips = "".join(f'<li><strong>{escape(t["category"])}</strong> {escape(t["instruction"])}<small>{escape(labels.get(t["basis"], t["basis"]))} · {escape(t["evidence"])}</small></li>' for t in plan["tips"])
        cards.append(f'''<article><h2>{escape(plan["title"])}</h2><div class="frame"><img src="{data_uri}" alt="输入图片与建议取景框">
        <svg viewBox="0 0 1000 1000" preserveAspectRatio="none" aria-label="建议裁剪范围"><rect x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}" fill="none" stroke="#efc58e" stroke-width="3" vector-effect="non-scaling-stroke"/></svg></div><ul>{tips}</ul></article>''')
    status = "视觉结果解析成功" if result["status"] == "ok" else "视觉结果未通过校验，显示通用方案"
    observations = result["guidance"]["warnings"] + result["guidance"]["uncertainties"]
    notices = '<p class="note">观察限制：' + escape('；'.join(observations)) + '</p>' if observations else ''
    payload = escape(json.dumps(result, ensure_ascii=False, indent=2))
    return f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'">
    <title>观景视觉模型 · 本地推理报告</title><style>
    *{{box-sizing:border-box}}body{{background:#111315;color:#eeeae3;font:16px/1.6 system-ui,sans-serif;margin:0 auto;padding:28px;max-width:1200px}}
    h1{{font-size:30px}}.note,small{{color:#b2b8bf}}.pill{{color:#efc58e}}main{{display:grid;grid-template-columns:1fr 1fr;gap:20px}}article{{background:#1d2023;padding:20px;border-radius:20px}}h2{{font-size:22px;margin-top:0}}
    .frame{{position:relative}}img{{display:block;width:100%;border-radius:12px}}svg{{position:absolute;inset:0;width:100%;height:100%}}ul{{padding-left:20px}}li{{margin:16px 0}}small{{display:block;font-size:12px;margin-top:5px}}pre{{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px}}details{{margin-top:24px}}@media(max-width:700px){{main{{grid-template-columns:1fr}}body{{padding:16px}}}}
    </style><h1>观景 · FrameCoach Vision 0.1</h1><p class="pill">{status}</p>
    <p class="note">预训练视觉模型分析 + 原创规则生成方案。未微调摄影审美，尚未验证红米 K80 部署；演示样本不代表真实摄影质量评测。</p>
    {notices}<main>{''.join(cards)}</main><details><summary>查看模型来源、耗时、原始观察与限制</summary><pre>{payload}</pre></details></html>'''
