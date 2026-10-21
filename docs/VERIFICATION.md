# 验证记录

日期：2026-10-21。

- 当前工作环境：Windows，发现 JBR 21；未配置 Android SDK、adb 或 Gradle。
- 当前包含 24 个稳定检测、预览亮度与规则引擎单元测试，以及 Android 模拟器演示、相机预览与 JPEG 保存测试。
- 自动构建工作流会执行 `:core:test :app:lintDebug :app:assembleDebug`，成功后再运行 `:app:connectedDebugAndroidTest`。
- 已验证基线：提交 `4d9db50b37adc892b0b1eb41fc59f5fd2916ab88` 的 [构建与测试](https://github.com/gkdly136/photo/actions/runs/39576451307) 全部通过：20 个核心单元测试、Lint、Debug APK、1 个 Android 15 模拟器演示测试。APK 已生成。
- 相机验证基线：提交 `dba8bebee88c96a5aaaf021b20e86d4db0e631fd` 的 [构建与测试](https://github.com/gkdly136/photo/actions/runs/39578692370) 全部通过，包括 2 个 Android 15 模拟器测试，覆盖预览进入 STREAMING 与快门保存有效 JPEG。
- 当前新增的预览 YUV 亮度边界测试与标准 Wrapper 将再运行一次完整验证；最终结果会补充在本文件中。
- 标准 Wrapper JAR 与 Gradle 官方 v8.11.1 仓库 Git blob `a4b76b9530d66f5e68d973ea569d8e19de379189` 一致。
- 真实手机摄像头、ML Kit 检测准确率、前置镜像、温升与实际拍照仍需按照真机验收清单验证。
