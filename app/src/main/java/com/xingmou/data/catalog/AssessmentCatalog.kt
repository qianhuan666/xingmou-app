package com.xingmou.data.catalog

data class AssessmentDefinition(val id: String, val name: String, val domains: List<String>, val note: String)

object AssessmentCatalog {
    val all = listOf(
        AssessmentDefinition("GESELL", "Gesell", listOf("A", "B", "D", "F"), "专业端转录入口，不用于平台自动诊断"),
        AssessmentDefinition("GRIFFITHS", "Griffiths", listOf("A", "B", "D", "F"), "专业端转录入口，不用于平台自动诊断"),
        AssessmentDefinition("WISC", "WISC", listOf("B", "C", "D"), "需由具备资质人员解释"),
        AssessmentDefinition("DDST", "DDST", listOf("A", "D", "F"), "专业端发育筛查转录"),
        AssessmentDefinition("SOCIAL_LIFE", "社会生活能力", listOf("E", "F"), "专业端适应行为转录"),
        AssessmentDefinition("VINELAND", "Vineland", listOf("D", "E", "F"), "专业端适应行为转录"),
        AssessmentDefinition("S_S", "S-S 语言发育", listOf("D"), "专业端语言评估转录"),
        AssessmentDefinition("PEP_3", "PEP-3", listOf("A", "D", "E", "F"), "专业端转录入口"),
        AssessmentDefinition("VB_MAPP", "VB-MAPP", listOf("D", "E"), "专业端转录入口"),
        AssessmentDefinition("ABLLS_R", "ABLLS-R", listOf("D", "E", "F"), "专业端转录入口"),
        AssessmentDefinition("ABC", "ABC", listOf("E"), "行为观察转录入口"),
        AssessmentDefinition("CARS", "CARS", listOf("E", "D"), "专业端转录入口，不用于平台自动诊断"),
        AssessmentDefinition("VABS", "VABS", listOf("D", "E", "F"), "专业端适应行为转录"),
        AssessmentDefinition("M_CHAT", "M-CHAT-R/F", listOf("A", "E"), "专业端筛查转录入口"),
        AssessmentDefinition("OTHER", "其他专业量表", emptyList(), "必须填写工具版本、日期和来源")
    )
}
