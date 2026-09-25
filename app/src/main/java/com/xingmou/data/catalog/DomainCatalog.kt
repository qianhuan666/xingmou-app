package com.xingmou.data.catalog

data class DomainDefinition(
    val id: String,
    val name: String,
    val summary: String,
    val displayColor: String
)

/** 原版六域目录的 Kotlin 单一来源。 */
object DomainCatalog {
    val all: List<DomainDefinition> = listOf(
        DomainDefinition("A", "注意与感知觉", "视觉、听觉、共同注意和目标保持", "coral"),
        DomainDefinition("B", "记忆", "工作记忆、序列记忆和信息保持", "sky"),
        DomainDefinition("C", "执行功能与逻辑", "分类、排序、计划和问题解决", "amber"),
        DomainDefinition("D", "语言沟通", "理解、表达、模仿和替代沟通", "violet"),
        DomainDefinition("E", "社会情绪", "情绪识别、互动和调节", "mint"),
        DomainDefinition("F", "生活适应与动作", "生活工具、动作计划和日常适应", "blue")
    )

    fun find(id: String): DomainDefinition? = all.firstOrNull { it.id == id }
}
