package com.xingmou.core.perception

/**
 * 语音识别文本与目标词的模糊匹配。
 * 归一化：转小写、去空白与中英文标点后做包含匹配。
 */
object KeywordMatcher {

    private val noise = Regex(
        "[\\s\\p{Punct}，。！？、；：“”‘’（）【】《》…—·,.!?;:()\\[\\]{}<>\"'/\\\\|~`@#\$%^&*_+=]+"
    )

    fun normalize(text: String): String = text.lowercase().replace(noise, "")

    /** 返回命中的原始关键词（词表中的写法），未命中返回 null。 */
    fun matches(text: String, keywords: List<String>): String? {
        val normalized = normalize(text)
        if (normalized.isBlank()) return null
        for (keyword in keywords) {
            val target = normalize(keyword)
            if (target.isNotEmpty() && normalized.contains(target)) return keyword
        }
        return null
    }
}
