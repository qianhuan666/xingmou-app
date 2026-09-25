package com.xingmou.data.catalog

/** 统一题目模型，基线和课程都通过同一份结构渲染。 */
enum class QuestionType { CHOICE, MEMORY, SEQUENCE, OBSERVED }

data class QuestionDefinition(
    val id: String,
    val version: Int,
    val moduleId: String,
    val domain: String,
    val type: QuestionType,
    val prompt: String,
    val options: List<String>,
    val correctOption: Int? = null,
    val sourceRef: String = "local-v0.7b"
)

object QuestionCatalog {
    /** 第一版基线固定六题，每题覆盖一个训练域，只记录过程表现。 */
    val baselineQuestions: List<QuestionDefinition> = listOf(
        QuestionDefinition("BL-A-01", 1, "baseline", "A", QuestionType.CHOICE, "找出圆形", listOf("圆形", "三角形"), 0),
        QuestionDefinition("BL-B-01", 1, "baseline", "B", QuestionType.CHOICE, "找出和左边一样的颜色", listOf("蓝色", "黄色"), 0),
        QuestionDefinition("BL-C-01", 1, "baseline", "C", QuestionType.CHOICE, "找出和左边一样的形状", listOf("方形", "星形"), 0),
        QuestionDefinition("BL-D-01", 1, "baseline", "D", QuestionType.MEMORY, "刚才先出现的是什么", listOf("小球", "小树"), 0),
        QuestionDefinition("BL-E-01", 1, "baseline", "E", QuestionType.SEQUENCE, "先做哪一步", listOf("先拿杯子", "先喝水"), 0),
        QuestionDefinition("BL-F-01", 1, "baseline", "F", QuestionType.OBSERVED, "现在更想怎么做", listOf("自己试试", "找大人帮忙"), null)
    )

    /** V0.7B 第一条课程链路的题目入口，后续按版本扩展。 */
    val firstCourseQuestions: List<QuestionDefinition> = listOf(
        QuestionDefinition("M02-L1-01", 1, "M02", "B", QuestionType.MEMORY, "找到相同的图片", listOf("小球", "小树"), 0),
        QuestionDefinition("M02-L1-02", 1, "M02", "B", QuestionType.MEMORY, "找到相同的图片", listOf("小树", "小球"), 1),
        QuestionDefinition("M02-L1-03", 1, "M02", "B", QuestionType.MEMORY, "找到相同的图片", listOf("小球", "小树"), 0),
        QuestionDefinition("M02-L1-04", 1, "M02", "B", QuestionType.MEMORY, "找到相同的图片", listOf("小树", "小球"), 1),
        QuestionDefinition("M02-L1-05", 1, "M02", "B", QuestionType.MEMORY, "找到相同的图片", listOf("小球", "小树"), 0)
    )
}
