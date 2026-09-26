package com.xingmou.core.organization

enum class LocalRole(val label: String) {
    ADMIN("机构管理员"), PROFESSIONAL("专业人员"), PARENT("家长"), VIEWER("只读查看")
}

enum class LocalCapability {
    VIEW_CHILD, TRAIN_CHILD, RECORD_HOME, REVIEW_PLAN, SIGN_PLAN, MANAGE_USERS, MANAGE_ORGANIZATION,
    EXPORT_DATA, DELETE_DATA, AUDIT_AGENT
}

object LocalRolePolicy {
    fun normalize(role: String?): LocalRole = runCatching {
        LocalRole.valueOf(role.orEmpty().trim().uppercase())
    }.getOrDefault(LocalRole.VIEWER)

    fun can(role: String?, capability: LocalCapability): Boolean = when (normalize(role)) {
        LocalRole.ADMIN -> true
        LocalRole.PROFESSIONAL -> capability in setOf(
            LocalCapability.VIEW_CHILD, LocalCapability.TRAIN_CHILD, LocalCapability.RECORD_HOME,
            LocalCapability.REVIEW_PLAN, LocalCapability.SIGN_PLAN, LocalCapability.EXPORT_DATA,
            LocalCapability.DELETE_DATA, LocalCapability.AUDIT_AGENT
        )
        LocalRole.PARENT -> capability in setOf(LocalCapability.VIEW_CHILD, LocalCapability.TRAIN_CHILD, LocalCapability.RECORD_HOME)
        LocalRole.VIEWER -> capability == LocalCapability.VIEW_CHILD
    }
}
