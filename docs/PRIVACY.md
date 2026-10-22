# 试用版隐私说明

本应用没有账号、业务服务器、广告或照片上传功能。相机只在用户授予权限并开启后使用；实时帧仅用于内存中的亮度、稳定和 ML Kit 推理，不落盘。应用进入后台时，CameraX 随生命周期停止相机。

只有主动点击「拍照」才向系统相册 `Pictures/FrameCoach` 写入照片。应用不索取麦克风、位置或读取整个相册的权限。不进行人脸身份识别，不保存人脸模板，不从脸部判断情绪、性格或容貌分数。

ML Kit 使用打包模型，端侧处理图像。Google 的 SDK 仍可能自行收集设备、性能或使用诊断信息；不能把“本应用代码不上传照片”扩展为“所有依赖完全没有网络通信”。分发前须核对最终合并 Manifest、SDK 数据实践与 Google Play Data Safety 披露。

参考：[ML Kit 数据披露与隐私](https://developers.google.com/ml-kit/terms)、[Google API 数据披露](https://developers.google.com/ml-kit/android-data-disclosure)。

来源卡片的链接会打开外部浏览器；相应网站适用其隐私政策。

用户可在系统设置中撤回相机权限，或卸载应用。已保存到相册的照片需在相册中自行删除。
