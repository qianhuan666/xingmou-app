package com.xingmou.core.agent

import com.xingmou.data.db.MemoryItemEntity

data class MemoryDraft(
    val memoryId: String,
    val childId: String?,
    val scope: String,
    val content: String,
    val sourceRef: String?,
    val confidence: String,
    val expiresAt: Long?
)

/** 仅管理脱敏、可撤回的短期记忆；未经确认的推断不会自动升级为长期记忆。 */
class MemoryManager {
    private val memories = linkedMapOf<String, MemoryDraft>()
    private val revoked = mutableSetOf<String>()

    fun save(draft: MemoryDraft, confirmed: Boolean = false): Boolean {
        if (draft.content.isBlank() || containsSensitiveIdentity(draft.content)) return false
        if (!confirmed && draft.confidence.lowercase() !in setOf("observed", "reported")) return false
        memories[draft.memoryId] = draft
        revoked.remove(draft.memoryId)
        return true
    }

    fun active(childId: String? = null): List<MemoryDraft> = memories.values.filter {
        it.memoryId !in revoked && (childId == null || it.childId == childId)
    }

    fun revoke(memoryId: String): Boolean = if (memories.containsKey(memoryId)) revoked.add(memoryId) else false

    fun toEntity(memoryId: String, createdAt: Long): MemoryItemEntity {
        val draft = memories[memoryId] ?: error("找不到记忆：$memoryId")
        return MemoryItemEntity(
            memoryId = draft.memoryId, childId = draft.childId, scope = draft.scope,
            content = draft.content, sourceRef = draft.sourceRef, confidence = draft.confidence,
            status = if (memoryId in revoked) "revoked" else "active", expiresAt = draft.expiresAt, createdAt = createdAt
        )
    }

    private fun containsSensitiveIdentity(text: String): Boolean =
        Regex("\\d{17}[\\dXx]|(?<!\\d)1[3-9]\\d{9}(?!\\d)").containsMatchIn(text)
}
