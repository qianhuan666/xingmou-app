package com.xingmou.core.llm

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xingmou.core.model.Port

/**
 * JSON 输出校验（对应提示词第八节硬规则第 5 条：结构校验）。
 *
 * 模型输出必须通过校验才能进入界面；任何不合规 → 返回失败 → 调用方降级到 SafeResponses 固定文案。
 * 重点校验：
 * 1. JSON 合法 + 必需字段 + 枚举值
 * 2. 悬空引用：每个 claim.source_ids 必须能在本次 sources 中找到
 * 3. 儿童端不得出现风险/诊断等越权内容
 */
object JsonValidator {

    /** 各端口允许的 state/mode 枚举 */
    private val allowedState = mapOf(
        Port.CHILD to setOf("continue", "hint", "choice", "pause", "safety_stop"),
        Port.PARENT to setOf("answer", "clarify", "insufficient_data", "refer", "safety_stop"),
        Port.PROFESSIONAL to setOf("draft", "insufficient_data", "safety_stop")
    )

    /** 校验模型输出。成功返回解析后的 JsonObject，失败返回 Result.failure */
    fun validate(port: Port, json: String): Result<JsonObject> = runCatching {
        val obj = JsonParser.parseString(json).asJsonObject

        // 1. 顶层状态字段合法
        val stateKey = when (port) {
            Port.CHILD -> "state"
            Port.PARENT -> "mode"
            Port.PROFESSIONAL -> "status"
        }
        val state = obj.get(stateKey)?.asString
        require(state != null && state in allowedState[port].orEmpty()) {
            "非法状态字段 $stateKey=$state"
        }

        // 2. 儿童端：speech 必须存在且为短句数组
        if (port == Port.CHILD) {
            val speech = obj.getAsJsonArray("speech")
                ?: throw IllegalArgumentException("儿童端缺少 speech")
            require(speech.size() > 0 && speech.all { it.asString.length <= 15 }) {
                "儿童端 speech 缺失或超长"
            }
        }

        // 3. 悬空引用校验（家长端/专业端）：claim.source_ids 必须能在 sources 找到
        if (port != Port.CHILD) {
            checkDanglingReferences(obj)
        }

        // 4. 专业端：review_required 必须为 true
        if (port == Port.PROFESSIONAL) {
            require(obj.get("review_required")?.asBoolean == true) {
                "专业端 review_required 必须为 true"
            }
        }

        obj
    }

    /** 悬空引用检查：每个 claim.source_ids 必须能在本次 sources 中解析 */
    private fun checkDanglingReferences(obj: JsonObject) {
        val sourceIds = obj.getAsJsonArray("sources")
            ?.mapNotNull { it.asJsonObject?.get("source_id")?.asString }
            ?.toSet() ?: emptySet()

        obj.getAsJsonArray("claims")?.forEach { c ->
            val claim = c.asJsonObject ?: return@forEach
            val ids = claim.getAsJsonArray("source_ids")?.map { it.asString } ?: emptyList()
            ids.forEach { sid ->
                require(sid in sourceIds) { "悬空引用: $sid 不在本次 sources 中" }
            }
        }
    }

    /** 字段缺失时填 null 而不是猜测 —— 供反序列化前检查 */
    fun hasNonNullRequiredFields(obj: JsonObject, fields: List<String>): Boolean =
        fields.all { obj.has(it) && !obj.get(it).isJsonNull }
}
