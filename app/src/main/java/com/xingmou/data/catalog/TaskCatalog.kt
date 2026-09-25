package com.xingmou.data.catalog

data class TaskDefinition(
    val id: String,
    val name: String,
    val domain: String,
    val engine: String,
    val goal: String,
    val materialType: String = "图片"
)

/** 原版 22 个训练模块的第一版目录，题库内容后续按模块逐步补齐。 */
object TaskCatalog {
    val all: List<TaskDefinition> = listOf(
        TaskDefinition("P01", "颜色辨别", "A", "target_search", "在干扰中找到目标颜色"),
        TaskDefinition("P02", "形状匹配", "A", "target_search", "识别并匹配目标形状"),
        TaskDefinition("P03", "目标搜索", "A", "target_search", "保持注意并找到指定目标"),
        TaskDefinition("P04", "共同注意", "A", "observed", "根据指向或视线关注目标"),
        TaskDefinition("M01", "即时记忆", "B", "memory_match", "短时保持单个信息"),
        TaskDefinition("M02", "图片配对", "B", "memory_match", "记住并匹配相同图片"),
        TaskDefinition("M03", "顺序记忆", "B", "sequence", "按顺序复现信息"),
        TaskDefinition("M04", "工作记忆", "B", "memory_match", "在干扰下保持并操作信息"),
        TaskDefinition("E01", "分类推理", "C", "sorting", "根据规则进行分类"),
        TaskDefinition("E02", "大小排序", "C", "sorting", "按大小或数量排序"),
        TaskDefinition("E03", "按顺序放图片", "C", "sequence", "按照事件顺序排列图片"),
        TaskDefinition("E04", "找不同", "C", "odd_one_out", "发现规则中的不同项"),
        TaskDefinition("L01", "指认物品", "D", "choice", "理解词语并指认目标"),
        TaskDefinition("L02", "跟读词语", "D", "audio", "模仿并表达目标词语"),
        TaskDefinition("L03", "理解指令", "D", "choice", "理解一步或两步指令"),
        TaskDefinition("L04", "替代沟通", "D", "observed", "使用图片或动作表达需要"),
        TaskDefinition("S01", "情绪识别", "E", "choice", "识别基础情绪线索"),
        TaskDefinition("S02", "互动轮流", "E", "observed", "在互动中等待和轮流"),
        TaskDefinition("S03", "情境选择", "E", "choice", "在熟悉情境中选择支持方式"),
        TaskDefinition("D01", "生活工具", "F", "choice", "识别生活工具及用途"),
        TaskDefinition("D02", "模仿动作", "F", "observed", "模仿一个简单动作"),
        TaskDefinition("D03", "生活顺序", "F", "sequence", "按步骤完成生活流程")
    )

    fun find(id: String): TaskDefinition? = all.firstOrNull { it.id == id }
}
