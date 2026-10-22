# 本机验证记录 · 2026-10-22

本轮交付的是可运行的预训练组合模型，不是已经完成摄影专用训练或手机部署的模型。

## 运行环境与权重

- Windows，Python 3.11.13，PyTorch 2.7.1+cu118，Transformers 4.57.6，LM Format Enforcer 0.11.3。
- NVIDIA GeForce RTX 4060 Laptop GPU，8GB 显存；BF16、SDPA，图像预算不超过 512×512 像素。
- 基础模型 `Qwen/Qwen3-VL-2B-Instruct`，固定版本 `89644892e4d85e24eaac8bacfd4f463576704203`。
- 原始 safetensors 权重 4,255,140,312 字节；全部 11 个模型文件完成 SHA-256 校验。完整清单见 `base_model.lock.json`，大文件仅保存在本地。
- 模型加载和分词约束树初始化约 11.2 秒，另有 Python 导入、磁盘散列校验时间，不包含在这个加载计时中。

## 实际结果

26 项协议与流水线测试通过，覆盖 1,000 组随机裁剪几何、主体点选、多人测量隔离、EXIF 方向、无效 JSON/坐标、错误降级、HTML 转义、旧场景和重复回调隔离。流水线测试使用替身观察器，不能视为神经模型测试。

另用真实 GPU 和实际权重处理三张原创合成插图，以下计时包含图像处理、受约束生成与解码，不含模型加载。只有三张样本，不能据此推断平均速度、真实摄影质量或手机性能。

| 样本 | 结构化协议 | 语义检查 | 推理耗时 | 峰值分配显存 |
| --- | --- | --- | --- | --- |
| 人物 / 道路 | 通过 | 有人物、有道路，无额外背景类别 | 8.693 秒 | 4233.3 MiB |
| 山湖 | 通过 | 无人物、天空与水面正确；**额外误判了 building** | 9.634 秒 | 4233.6 MiB |
| 空白 | 通过 | 无人物、无背景区域，类型 blank | 1.106 秒 | 4233.6 MiB |

因此：**协议 3/3 通过，基础语义检查 2/3 通过；整套语义冒烟测试仍为失败。** `verification/smoke-summary.json` 中明确保留 `passed: false`，脚本会返回 1；没有把视觉误检改成绿色结果。已保存每张的原始输出及完整方案 JSON。

最初仅靠提示词的版本出现漏字段、协议外区域名和示例词照抄，校验层将其降级。随后加入逐 token JSON 字段/标签约束，并把视觉任务收敛为主体和背景观察；取景类型默认 unknown、不让小模型编造姿态解释。这解决了本轮样本的协议问题，**没有解决所有语义误检**。

报告中的主体裁剪包含已识别框；若模型框漏掉了手脚，包含校验无法补回这些内容。山湖样本仍作为失败案例保留，后续真实照片评测和训练需要重点覆盖背景误检。

## 复现与查看

按 README 初始化后运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File vision/scripts/run-windows.ps1 smoke --device cuda:0 --output vision/reports/local-smoke
```

需要重新评价已保存的真实输出而不再加载权重时：

```powershell
python vision/scripts/smoke_model.py --evaluate-existing --output vision/reports/local-smoke
```

仅评测模式从已有报告读取计时和模型信息，在摘要中标记 `saved_real_inference_outputs`；不是重新进行神经推理。本次最终摘要采用这一模式补充背景误检评价，原始推理输出没有被修改。

本地 HTML 报告位于 `vision/reports/local-smoke/<样本名>/report.html`；公开记录只包含原创合成样本，不含用户照片。Android 0.2 的验证记录仍见项目 `docs/VERIFICATION.md`，本轮未更改 Android 实现。

2.5 秒稳定停留在未来接入中表示“开始分析”的时机，不能宣称这个桌面原型会在 2.5 秒内给出结果。K80 实时延迟、量化后的质量/内存/发热与真实摄影质量均未验证。
