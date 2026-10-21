# 摄影资料与知识库

资料核对日期：2026-10-21。以下是可公开核对的第一批材料，不代表全面通读、院校背书或训练授权。

## 当前核实的来源

| ID | 官方资料 | 实际核对范围 | 在首版中的用途 |
| --- | --- | --- | --- |
| freeman | [The Photographer’s Eye: Graphic Guide，Michael Freeman，Routledge，2013，ISBN 9780240824604](https://www.routledge.com/The-Photographers-Eye-Graphic-Guide-Composition-and-Design-for-Bette/Freeman/p/book/9780240824604) | 出版社公开书介与目录；全文未读，页面直接抓取曾返回 403 | 取景、位置、视点、图形关系的知识分类；没有复制原书规则表或照片 |
| risd-light | [RISD Photography Courses](https://www.risd.edu/academics/photography/courses?page=0) | 公开课程说明，包括人工光线与概念、情绪和空间的关系；并非完整教材 | 让光线提示同时考虑表达意图，而不仅是亮不亮 |
| nikon-portrait | [Nikon: Quick Tips for Taking Better Portraits](https://www.nikonusa.com/learn-and-explore/c/tips-and-techniques/quick-tips-for-taking-better-portraits) | 公开人像拍摄教程 | 将主体受光、对焦和曝光检查改写为操作提示 |
| nikon-background | [Nikon: Take Better Portraits](https://www.nikonusa.com/learn-and-explore/c/tips-and-techniques/take-better-portraits) | 背景简化、身体角度与柔光的教学示例 | 人像构图和微侧身方向；不照搬其关于所有人都应如何显瘦的判断 |
| moma-context | [MoMA: Documenting and interpreting the modern world](https://www.moma.org/collection/terms/photography/explore-the-many-different-ways-photography-has-been-used-to-document-and-interpret-the-modern-world) | 官方摄影教育主题入口 | 构图服务于表达，允许打破模板 |
| moma-posed | [MoMA: Posed/Unposed](https://www.moma.org/collection/terms/photography/posed-unposed) | 摆拍/非摆拍教育主题入口 | 提供静态与动态拍摄方向；具体表情指令是本项目原创建议 |
| swpa-landscape | [Sony World Photography Awards 2025: Landscape](https://www.worldphoto.org/sony-world-photography-awards/winners-galleries/2025/professional/landscape) | 官方获奖目录，包括 Seido Kino 的 The Strata of Time、Lalo de Almeida 的 Apocalypse；未逐幅分析 | 建立后续研究条目，不参与当前图像匹配或训练 |
| swpa-portrait | [Sony World Photography Awards 2025: Professional](https://www.worldphoto.org/sony-world-photography-awards/winners-galleries/2025/professional) | 人像与风光分类索引；人像内页曾抓取超时 | 待完成逐作品来源、授权与视觉标注后再加入案例库 |

## 用户点名的书籍

用户已确认作者为本·克莱门茨、斯科特·凯尔、斯蒂芬·肖尔；以下是已核实的英文对应书目或作者公开内容。中文译本版本仍需另行核对，不能将读过书介当作通读正文。

| 书籍 | 核对结果 | 当前阅读范围与转化 |
| --- | --- | --- |
| 《照片的本质》 | [Stephen Shore: The Nature of Photographs: A Primer，Phaidon，ISBN 9780714859040](https://www.phaidon.com/en-us/products/the-nature-of-photographs) | 核对作者、136 页英文版和出版社书介。将“学习观看与理解照片”作为产品研究目标，不声称掌握具体章节论证 |
| 《摄影构图学》 | [Photographic composition，Ben Clements / David Rosenfeld，Van Nostrand Reinhold，1980，©1974](https://search.worldcat.org/title/Photographic-composition/oclc/988073145) | 核对馆藏书目、作者与出版信息；未读取正文，不将具体规则强行归因 |
| 《风光摄影手册》 | [Scott Kelby 本人介绍其 The Landscape Photography Book 公开节选](https://scottkelby.com/four-landscape-photography-recipes/)，链接到 [出版社 Landscape Recipes](https://rockynook.com/article/landscape-recipes/) | 已阅读四个公开案例的说明。原创提炼：先判断天空/前景的表达价值，再选画面占比；低机位有助于尝试前景关系，具体效果需现场比较。书中单个案例参数不能直接当成所有手机的建议 |

Scott Kelby 的姓名常译为斯科特·凯尔比；这里按用户提供的作者线索匹配，未擅自确认中文 ISBN。

本项目的 `freeman` 条目是已核实的独立参考书，**不把它当成用户所说《摄影构图学》的已确认版本**。

## 已实现的规则映射

| 规则 | 观测或选择 | 输出 | 原则来源 / 边界 |
| --- | --- | --- | --- |
| 三分/居中 | 用户构图选项 + 最大人脸位置 | 人物向画面左/右调整 | freeman、nikon-background；目标点是方案，不是评分 |
| 亮部检查 | 采样 Y ≥ 230/255 的比例 > .12 | 检查曝光与主体明暗 | nikon-portrait；兼顾有限范围 YUV 预览，阈值为工程启发式，尚未逐机型校准 |
| 暗场检查 | 平均亮度 < .20 | 稳住设备、考虑柔光 | risd-light、nikon-portrait；不推断现场照度 |
| 手部动作 | 双手腕/髋部可见且置信度 ≥ .65，手腕低于髋部 | 尝试扶衣角等自然落点 | 原创动作提示；二维位置并不证明姿势僵硬 |
| 回头尝试 | 头部转角绝对值 > 30° | 比较回头与侧脸版本 | nikon-background；不把侧脸当错误 |
| 自然/安静/轻快 | 用户主动选择 | 对应动作与表情提示 | moma-posed 是创作方向入口；不做情绪识别 |
| 风光层次 | 用户构图选项 | 天地取舍、对称、前景观察 | freeman；未自动识别道路和天空 |

## 后续案例记录格式

```json
{
  "id": "case-unique-id",
  "title": "具体作品与作者",
  "sourceUrl": "官方单件作品页",
  "rights": "link-only / licensed-reference / licensed-training",
  "reviewStatus": "pending / visually-reviewed / expert-reviewed",
  "scene": ["environmental-portrait"],
  "principle": "以原创文字描述视觉关系",
  "evidence": "可核对的画面事实，与主观解读分开",
  "counterexample": "不适用的场景",
  "action": "拍摄者可以执行的一步",
  "confidence": "expert-reviewed 后才能提高"
}
```

当前没有训练数据集，没有将获奖目录直接转成“好照片”标签。数据收集应覆盖普通场景、失败样本、不同肤色、年龄、体型、行动能力、焦距与光照，并请摄影师复核。

已额外阅读 [The Strata of Time 官方系列说明](https://www.worldphoto.org/sony-world-photography-awards/winners-galleries/2025/professional/strata-time-seido-kino?back=category)：作者将历史与当下的同一地点影像叠置，讨论发展与环境、人口的关系。由此得到的产品设计推论是：获奖价值可能来自时间关系与议题表达，不能靠单张取景的三分法或亮度评分重现。当前阅读的是作者说明，并未完成逐幅视觉分析或获得图片训练权。

## 实现依据

- [CameraX 官方版本记录](https://developer.android.com/jetpack/androidx/releases/camera)：选用稳定版 1.5.3。
- [CameraX ML Kit Analyzer](https://developer.android.com/media/camera/camerax/mlkitanalyzer)：预览坐标变换。
- [ML Kit Face Android](https://developers.google.com/ml-kit/vision/face-detection/android)：bundled 模型、输入尺寸和低光限制。
- [ML Kit Pose Android](https://developers.google.com/ml-kit/vision/pose-detection/android)：端侧关键点与置信度；仍为 Beta。
- [AGP 8.9 兼容信息](https://developer.android.com/build/releases/agp-8-9-0-release-notes)：Gradle 8.11.1、JDK 17、API 35。
