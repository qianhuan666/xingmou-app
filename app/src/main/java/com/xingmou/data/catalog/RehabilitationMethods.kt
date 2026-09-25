package com.xingmou.data.catalog

data class RehabilitationMethod(
    val id: String,
    val name: String,
    val summary: String,
    val boundary: String,
    val sourceRef: String = "LOCAL_REVIEWED_METHODS_V1",
    val reviewStatus: String = "已审核"
)

object RehabilitationMethods {
    val all = listOf(
        RehabilitationMethod("ABA", "ABA", "以可观察行为和环境支持为基础的训练方法。", "必须由专业人员制定和监督"),
        RehabilitationMethod("TEACCH", "TEACCH", "通过结构化环境和视觉支持降低任务负担。", "不替代个体化专业评估"),
        RehabilitationMethod("NDBI", "NDBI", "在自然互动中嵌入发展和行为支持。", "需要专业人员选择适配目标"),
        RehabilitationMethod("SENSORY", "感觉统合", "关注感觉输入、环境调整和参与舒适度。", "不将感觉表现解释为诊断"),
        RehabilitationMethod("SLT", "语言治疗", "围绕理解、表达和替代沟通设计支持。", "需专业人员确认沟通目标"),
        RehabilitationMethod("OT", "作业治疗", "围绕生活参与、动作和任务适应提供支持。", "高风险动作需线下评估"),
        RehabilitationMethod("PT", "物理治疗", "围绕动作、姿势和功能参与提供专业支持。", "平台不提供远程治疗处方"),
        RehabilitationMethod("ART", "艺术治疗", "使用绘画、音乐等媒介支持表达和参与。", "以舒适和自愿参与为前提"),
        RehabilitationMethod("SOCIAL_STORY", "社交故事", "用简短、具体的故事支持情境理解。", "不得强迫眼神接触或服从"),
        RehabilitationMethod("AAC", "AAC 替代沟通", "使用图片、手势或设备辅助表达。", "由专业人员与家庭共同选择"),
        RehabilitationMethod("COGNITIVE", "认知训练", "围绕注意、记忆和执行过程设计练习。", "训练表现不等于能力诊断"),
        RehabilitationMethod("FAMILY", "家庭支持", "通过短时、低刺激、可停止的家庭练习支持泛化。", "不得剥夺基本需要或施加惩罚")
    )
}
