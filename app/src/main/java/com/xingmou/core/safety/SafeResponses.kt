package com.xingmou.core.safety

/**
 * 确定性降级用的固定安全文案（对应提示词【安全状态固定响应】）。
 * 用于：规则引擎命中风险、模型超时/格式错误/越界时回退，保证任何情况下都有安全输出。
 */
object SafeResponses {

    // SAFETY_STOP —— 儿童端（短句、单步、找人）
    val SAFETY_STOP_CHILD = listOf(
        "先停下来。",
        "去找身边的大人。",
        "小星会等你。"
    )

    // SAFETY_STOP —— 家长端
    val SAFETY_STOP_PARENT =
        "请立即停止当前训练，先确保孩子和周围人员安全，并联系现场专业人员。" +
            "若存在正在发生的严重伤害、呼吸/吞咽困难、持续抽搐或意识异常，请立即联系当地急救服务。" +
            "本平台不在此时提供训练建议。"

    // SAFETY_STOP —— 专业端（结构化提示，不继续编排训练）
    val SAFETY_STOP_PROFESSIONAL = "检测到高风险信号，已停止训练方案生成。请转介有资质的临床专业人员进行评估。"

    // PAUSE_AND_SOOTHE —— 儿童端
    val PAUSE_CHILD = listOf(
        "我们先休息。",
        "你可以选：安静坐一会儿，或找大人帮忙。"
    )

    // PAUSE_AND_SOOTHE —— 家长/专业端
    val PAUSE_PARENT =
        "建议暂停任务、降低刺激、允许恢复；待情绪稳定后再由低难度熟悉任务开始。" +
            "若反复出现或程度加重，请转专业评估。"

    // 模型不可用时的儿童端兜底
    val LLM_FALLBACK_CHILD = listOf(
        "我们休息一下。",
        "去找大人帮忙吧。"
    )

    // 信息不足时的通用兜底
    val INSUFFICIENT_DATA = "目前信息不足，暂不能给出判断。建议先补充记录或咨询专业人员。"

    val DISCLAIMER = "以上是训练过程支持信息，不构成医学诊断。"
}
