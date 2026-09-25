package com.xingmou.core.consent

/** V0.7A 的本地同意闸门；远程 AI、共享和导出在未明确同意前均保持关闭。 */
enum class ConsentPurpose { TRAINING, REMOTE_AI, SHARING, EXPORT, RESEARCH }

enum class ConsentStatus { GRANTED, REVOKED, NOT_GRANTED }

data class ConsentDecision(val purpose: ConsentPurpose, val status: ConsentStatus, val allowed: Boolean)

class ConsentManager {
    fun check(purpose: ConsentPurpose, status: ConsentStatus): ConsentDecision =
        ConsentDecision(purpose, status, status == ConsentStatus.GRANTED)

    fun canRunLocalTraining(status: ConsentStatus): Boolean = status != ConsentStatus.REVOKED

    fun canUseRemoteAi(status: ConsentStatus): Boolean = status == ConsentStatus.GRANTED

    fun canExport(status: ConsentStatus): Boolean = status == ConsentStatus.GRANTED
}
