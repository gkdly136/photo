package com.framecoach.app

data class Source(val id: String, val title: String, val url: String, val note: String)

object Knowledge {
    val sources = listOf(
        Source("shore-nature", "斯蒂芬·肖尔 · 照片的本质", "https://www.phaidon.com/en-us/products/the-nature-of-photographs", "已核对 The Nature of Photographs: A Primer 的 Phaidon 书介，ISBN 9780714859040。用于理解照片与观看的研究方向；未通读全书，不将其简化为三分法评分。"),
        Source("kelby-landscape", "Scott Kelby · 风光摄影公开节选", "https://rockynook.com/article/landscape-recipes/", "已阅读出版社公开的 Landscape Recipes 节选，涉及倒影、前景、天空占比和机位。建议依据为原创概括；不复制原图，也不把单张示例的参数套用到手机。"),
        Source("clements-composition", "本·克莱门茨 / David Rosenfeld · 摄影构图学", "https://search.worldcat.org/title/Photographic-composition/oclc/988073145", "已核对 Photographic composition 的馆藏书目与两位作者，英文原书版权年 1974。中文版本和正文尚待核对；暂不把某条具体规则归因于原书。"),
        Source("freeman", "Michael Freeman · The Photographer’s Eye: Graphic Guide", "https://www.routledge.com/The-Photographers-Eye-Graphic-Guide-Composition-and-Design-for-Bette/Freeman/p/book/9780240824604", "已核对出版社公开介绍和目录：取景、位置、分割、视点等。只用作知识结构参考，未读取或复制整本书。"),
        Source("risd-light", "RISD · Photography Courses", "https://www.risd.edu/academics/photography/courses?page=0", "公开课程说明将光线与情绪、观念和空间联系起来。本应用的亮度阈值是工程启发式参数，不是院校评分标准。"),
        Source("nikon-portrait", "Nikon · Quick Tips for Taking Better Portraits", "https://www.nikonusa.com/learn-and-explore/c/tips-and-techniques/quick-tips-for-taking-better-portraits", "公开人像教学：注意主体的光、对焦和明暗。应用只测量预览亮度，不自动设置光圈或推断现场照明设备。"),
        Source("nikon-background", "Nikon · Take Better Portraits", "https://www.nikonusa.com/learn-and-explore/c/tips-and-techniques/take-better-portraits", "公开示例讨论背景干扰、取景、身体角度与柔光。这里将原则改写成可尝试的指令，不要求统一姿势或体型。"),
        Source("moma-posed", "MoMA · Posed/Unposed", "https://www.moma.org/collection/terms/photography/posed-unposed", "将摆拍与自然瞬间作为创作方向。表情提示由用户选择，不依据微笑概率推断性格、情绪或审美分数。"),
        Source("moma-context", "MoMA · Documenting and interpreting the modern world", "https://www.moma.org/collection/terms/photography/explore-the-many-different-ways-photography-has-been-used-to-document-and-interpret-the-modern-world", "公开摄影教育资源提示影像也在表达与解释世界。App 不把三分法当作唯一的好照片标准。"),
        Source("swpa-landscape", "Sony World Photography Awards · 2025 Landscape", "https://www.worldphoto.org/sony-world-photography-awards/winners-galleries/2025/professional/landscape", "已核对官方获奖目录，含 Seido Kino 的 The Strata of Time。仅作为研究入口；尚未完成逐幅视觉分析，未下载图片或将作品用于模型训练。"),
        Source("swpa-portrait", "Sony World Photography Awards · Professional galleries", "https://www.worldphoto.org/sony-world-photography-awards/winners-galleries/2025/professional", "官方人像与风光分类入口。后续应在获授权条件下，为具体作品标注光线、空间关系和适用情境。"),
        Source("mlkit-face", "Google · ML Kit Face Detection", "https://developers.google.com/ml-kit/vision/face-detection/android", "人脸位置与头部角度来自端侧模型。远小人脸、遮挡、暗光会影响结果；它不进行身份识别。")
    )
    fun find(id: String): Source? = sources.find { it.id == id }
}
