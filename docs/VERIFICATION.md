# 验证记录

日期：2026-10-21。

验证代码提交：`a11ab0c46f9298d5ed8f67d1b56f1c497bb7967d`。

[完整 GitHub Actions 运行](https://github.com/gkdly136/photo/actions/runs/39583156155) 的两个任务全部成功。

| 检查 | 实际结果 |
| --- | --- |
| 核心单元测试 | 24 项通过，0 失败，0 跳过；覆盖稳定时序、缓慢平移、曝光变化、YUV 亮度边界、构图与低置信度处理 |
| Android Lint | `:app:lintDebug` 通过 |
| Debug APK | `:app:assembleDebug` 成功 |
| Android 15 模拟器 | 2 项通过，0 失败，0 跳过 |
| 实时相机链路 | 模拟器预览进入 STREAMING，快门写入有效 JPEG，并检查 JPEG 文件头 |
| 界面行为 | 演示建议、人像/风光切换、Activity 重建与辅助线开关通过 |
| 截图 | 人像与风光两张 1080×2400 截图已保存并查看，主要控件和文字显示正常，建议区域支持滚动 |
| 本地静态核对 | 4 份 XML 可解析，12 个来源条目中的 10 个规则引用均可解析 |

构建环境为 GitHub Ubuntu Runner、JDK 17、Android SDK 35、Gradle 8.11.1。当前 Windows 工作机没有配置 Android SDK，未把本地静态检查冒充编译或真机测试。

标准 Wrapper JAR 与 Gradle 官方 v8.11.1 仓库 Git blob `a4b76b9530d66f5e68d973ea569d8e19de379189` 一致。

## 可下载产物

- [首版 APK 与测试报告 ZIP](https://github.com/gkdly136/photo/actions/runs/39583156155/artifacts/12338222181)
- [模拟器报告与两张界面截图](https://github.com/gkdly136/photo/actions/runs/39583156155/artifacts/12339416470)

Actions 附件保留 30 天，通常需要登录 GitHub 下载。APK 是包含多个 CPU 架构和打包视觉模型的 Debug 试用包。

APK 文件大小：135,779,550 字节。

APK SHA-256：`ab6214bd1f4ab831ecf9b17d837da59b9d5371db78dc9a4558620a2631a1d9f4`。

## 尚未验证

真实手机的检测准确率、前置镜像与旋转坐标、连续使用温升、电量消耗、复杂人群和动态背景仍需按照 [真机验收清单](DEVICE_TEST_PLAN.md) 验证。模拟器的拍照成功不能替代这些检查。当前尚未实现外接单反或微单的实时取景。
