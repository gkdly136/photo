# 验证记录

## 0.3.0（2026-11-07）

验证源码提交：`574f059cf775f57520486d3622a5423764cf187b`。

[完整 GitHub Actions 运行](https://github.com/gkdly136/photo/actions/runs/42330741785) 通过核心测试、Lint、APK 构建和 Android 15 模拟器检查。

| 检查 | 实际结果 |
| --- | --- |
| 核心单元测试 | 52 项通过，0 失败、0 跳过；其中 19 项针对姿势筛选、比例、镜像、旧帧、目标锁定、多人、遮挡、连续帧与冷却 |
| Android Lint | 通过，31 warnings；包含 SDK/依赖更新、绘制分配、View 工具构造函数与文案资源化等提示，未宣称零警告 |
| Debug APK | 构建成功，versionCode 3 / versionName 0.3.0 |
| Android 15 模拟器 | 5 项通过，0 失败、0 跳过；包括相机预览与有效 JPEG 保存、原有演示/横屏回归、新姿势库选择与锁定、重建撤回、座椅条件和收藏持久化 |
| 图片完整性与视觉复核 | 6 张模拟器截图可解码；已查看姿势库、目标参考、座椅条件及收藏三张截图，文字、线稿与主要控件正常显示 |
| 安装包完整性 | ZIP CRC 与 GitHub artifact SHA-256 核对通过；APK 哈希及签名证书摘要已记录 |

首轮姿势筛选用例在 Spinner 弹出窗口的根视图选择处遇到焦点异常，4/5 通过。限定到实际平台弹出窗口后，本次 5/5 通过；未删除、跳过或放宽筛选与收藏断言。

本版提供 40 个原创 Canvas 线稿动作，没有打包第三方姿势图库图片或从图片提取坐标。参考手臂未经真实摄影标定；动态或遮挡动作仅展示参考，不自动纠正。没有新增付费云端调用，没有训练摄影模型或把本地待审核视频案例自动用于训练。

### 安装包与报告

- [0.3.0 APK、核心测试与 Lint 报告 ZIP](https://github.com/gkdly136/photo/actions/runs/42330741785/artifacts/13557232078)
- [模拟器报告与截图](https://github.com/gkdly136/photo/actions/runs/42330741785/artifacts/13557552524)
- 本地交付目录：`verification/local-v03/`，含 APK、使用说明、元数据、测试报告与截图。

APK 大小：135,894,858 字节。

APK SHA-256：`46d327b2b9525311573dedcfee777f7280e142174077faf323924afeae9a8a26`。

签名证书 SHA-256：`88be68ec7720ef500249bc89e452738822d387c3d6fbac95ce70733ee26f131b`。已核对与本地 0.2 安装包不同，不能直接覆盖安装；需卸载旧试用版后安装，已拍原片位于共享相册 `Pictures/FrameCoach`。分发附件保留 30 天，通常需要登录 GitHub。

构建在 GitHub Ubuntu Runner、JDK 17、Android SDK 35、Gradle 8.11.1 完成。本机使用下载产物进行哈希、报告和截图核对，没有本机 Android SDK 编译或红米 K80 真机测试。K80 的提示准确性、手抖触发、延迟、温升与功耗仍待按照[真机验收清单](DEVICE_TEST_PLAN.md)验证。

## 0.2.0（2026-10-22）

验证源码提交：`f1a1c62e8de5f6ee525dc6c8d9a8ba1eee485f01`。

[完整 GitHub Actions 运行](https://github.com/gkdly136/photo/actions/runs/39733032227) 的构建与模拟器任务全部成功。

| 检查 | 实际结果 |
| --- | --- |
| 核心单元测试 | 33 项通过，0 失败、0 跳过；覆盖手持震动、独立图像抖动、单次陀螺仪尖峰、连续旋转、缓慢平移、换景、手动请求与摄影建议边界 |
| Android Lint | `:app:lintDebug` 通过；报告为 24 warnings，包含 SDK/依赖更新、绘制分配等提示，未宣称零警告 |
| Debug APK | `:app:assembleDebug` 成功，versionCode 2 / versionName 0.2.0 |
| Android 15 模拟器 | 3 项通过，0 失败、0 跳过 |
| 相机链路 | 预览进入 STREAMING；快门写入有效 JPEG，并验证文件头 |
| 界面行为 | 自动/手动辅助、人像/风光、动作/表情类别、设置、Activity 重建与横屏可用 |
| 截图检查 | 人像、风光竖屏及实际横屏 3 张截图已下载并查看；演示比例正常，无测试 Toast 遮挡主要控件；横屏建议可滚动 |

手持回归使用合成的三轴正负速度和独立的交替图像变化，验证门控不会无限等待；**不是 Redmi K80 的传感器实录或真机通过证明**。尚未测量该机真实触发率、P50/P95 延迟、识别质量、耗电或温升。详见 [真机清单](DEVICE_TEST_PLAN.md)。

构建环境：GitHub Ubuntu Runner、JDK 17、Android SDK 35、Gradle 8.11.1。本地 Windows 未配置 Android SDK，编译与设备检查在 CI 完成。

### 安装包与报告

- [0.2.0 APK 与核心测试/Lint 报告 ZIP](https://github.com/gkdly136/photo/actions/runs/39733032227/artifacts/12405867561)
- [模拟器测试报告及界面截图](https://github.com/gkdly136/photo/actions/runs/39733032227/artifacts/12405992953)

附件保留 30 天，下载通常需登录 GitHub。ZIP 中的 APK 为含多架构与本地视觉模型的 Debug 试用包。

APK 大小：135,796,098 字节。

APK SHA-256：`a9056ff1c6c695ceac06fc804a879881f81a4e4172451f9f6ad32c1120f4c614`。

已比较首版与本轮 Debug APK 的签名证书：不同，不能直接覆盖安装旧试用版，需要先卸载旧版再安装。拍摄原片位于共享相册 `Pictures/FrameCoach`。长期分发需要固定签名管理。

本次没有训练新的模型；书籍与获奖作品也没有被训练进模型。能力与后续路线见 [辅助逻辑](ASSISTANCE_LOGIC.md)。不支持外接单反/微单实时取景。

## 0.1.0 历史验证（2026-10-21）


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

### 可下载产物

- [首版 APK 与测试报告 ZIP](https://github.com/gkdly136/photo/actions/runs/39583156155/artifacts/12338222181)
- [模拟器报告与两张界面截图](https://github.com/gkdly136/photo/actions/runs/39583156155/artifacts/12339416470)

Actions 附件保留 30 天，通常需要登录 GitHub 下载。APK 是包含多个 CPU 架构和打包视觉模型的 Debug 试用包。

APK 文件大小：135,779,550 字节。

APK SHA-256：`ab6214bd1f4ab831ecf9b17d837da59b9d5371db78dc9a4558620a2631a1d9f4`。

### 尚未验证

真实手机的检测准确率、前置镜像与旋转坐标、连续使用温升、电量消耗、复杂人群和动态背景仍需按照 [真机验收清单](DEVICE_TEST_PLAN.md) 验证。模拟器的拍照成功不能替代这些检查。当前尚未实现外接单反或微单的实时取景。
