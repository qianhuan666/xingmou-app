package com.xingmou.core.rule

/**
 * 难度控制（对应提示词第八节硬规则第 4 条：单次 ±1 级）。
 *
 * 难度只能逐级调整；高风险或证据不足时只能保持、降级或暂停。
 * 兴趣素材可变化，但不得改变能力记录或绕过训练序列。
 */
object DifficultyController {

    /** 难度上限 */
    const val MAX_LEVEL = 5
    const val MIN_LEVEL = 1

    /**
     * 计算下一轮难度。
     * @param current 当前难度
     * @param delta 期望变化：+1 上调一级，-1 下调一级，0 保持
     * @param evidenceSufficient 数据是否充分（有效记录 >= 3 条）
     * @param highRisk 是否处于高风险状态
     */
    fun nextLevel(current: Int, delta: Int, evidenceSufficient: Boolean, highRisk: Boolean): Int {
        // 高风险或证据不足：只能保持或降级，禁止上调
        val clampedDelta = if (highRisk || !evidenceSufficient) minOf(delta, 0) else delta
        // 单次最多 ±1 级
        val safeDelta = clampedDelta.coerceIn(-1, 1)
        return (current + safeDelta).coerceIn(MIN_LEVEL, MAX_LEVEL)
    }

    /** 判断一次调整请求是否合法（禁止"升两级"这类越级请求） */
    fun isValidRequest(from: Int, to: Int): Boolean =
        kotlin.math.abs(to - from) <= 1 && to in MIN_LEVEL..MAX_LEVEL
}
