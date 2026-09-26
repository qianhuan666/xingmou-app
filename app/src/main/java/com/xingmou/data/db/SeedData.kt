package com.xingmou.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SeedData {
    const val DEMO_USER_ID = "local-professional"
    const val DEMO_ORGANIZATION_ID = "local-organization"

    val defaultOrganization = OrganizationEntity(
        organizationId = DEMO_ORGANIZATION_ID,
        name = "星眸本地试点机构",
        createdAt = 0L,
        updatedAt = 0L
    )

    val defaultUser = LocalUserEntity(
        userId = DEMO_USER_ID,
        organizationId = DEMO_ORGANIZATION_ID,
        displayName = "本地机构管理员",
        login = "professional",
        role = "admin",
        createdAt = 0L,
        updatedAt = 0L
    )

    val defaultChild = ChildEntity(
        childId = "child-seed",
        alias = "小星",
        ageBand = "学龄期",
        communicationLevel = "SHORT_SENTENCE",
        supportLevel = "L1",
        avatarColor = "coral",
        createdAt = 0L,
        updatedAt = 0L
    )

    val defaultBinding = ChildBindingEntity(
        userId = DEMO_USER_ID,
        childId = defaultChild.childId,
        role = "professional",
        validFrom = 0L
    )

    val knowledgeItems: List<KnowledgeItemEntity> = listOf(
        KnowledgeItemEntity(
            itemId = "KB-SAFETY-001",
            category = "safety",
            domain = null,
            title = "训练中出现明显不适时暂停",
            content = "出现持续哭闹、明显恐惧、强烈拒绝、疲劳或连续失败时，应暂停任务、降低刺激并允许恢复。",
            sourceRef = "LOCAL_REVIEWED_PROTOCOL:SAFETY_V1",
            verificationStatus = "verified",
            accessScope = "all_ports",
            keywordsJson = "[\"暂停\",\"疲劳\",\"连续失败\",\"哭闹\"]",
            updatedAt = 0L
        ),
        KnowledgeItemEntity(
            itemId = "KB-SUPPORT-001",
            category = "support",
            domain = "A",
            title = "最小辅助原则",
            content = "优先等待约五秒，再使用口头提示、视觉或手势提示；身体辅助只能由受过指导的照护者或专业人员在安全和同意前提下实施。",
            sourceRef = "LOCAL_REVIEWED_PROTOCOL:SUPPORT_V1",
            verificationStatus = "verified",
            accessScope = "professional_parent",
            keywordsJson = "[\"提示\",\"辅助\",\"等待\",\"L0\",\"L1\",\"L2\"]",
            updatedAt = 0L
        ),
        KnowledgeItemEntity(
            itemId = "KB-FAMILY-001",
            category = "family_support",
            domain = "F",
            title = "家庭短时高频练习",
            content = "家庭练习优先采用单次约五至十五分钟、每日一至两次的短时安排，以儿童状态为准，出现抗拒或疲劳时停止。",
            sourceRef = "LOCAL_REVIEWED_PROTOCOL:FAMILY_V1",
            verificationStatus = "verified",
            accessScope = "parent_professional",
            keywordsJson = "[\"家庭\",\"短时\",\"高频\",\"五分钟\",\"十五分钟\"]",
            updatedAt = 0L
        )
    )

    val taskItems: List<KnowledgeItemEntity> = listOf(
        "按顺序放图片", "图片配对", "颜色分类", "形状匹配", "大小排序", "指认物品", "模仿动作", "跟读词语"
    ).mapIndexed { index, task ->
        KnowledgeItemEntity(
            itemId = "TASK-${index + 1}",
            category = "task_whitelist",
            domain = "A-F",
            title = task,
            content = "阶段 1 白名单训练任务：$task",
            sourceRef = "LOCAL_TASK_CATALOG_V1",
            verificationStatus = "verified",
            accessScope = "all_ports",
            keywordsJson = "[\"$task\"]",
            updatedAt = 0L
        )
    }
}

class SeedDatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Seed insertion is performed through the generated database instance after creation.
        // The callback is intentionally kept lightweight; DatabaseSeeder is the public entry point.
    }
}

object DatabaseSeeder {
    suspend fun seed(database: QizhiDatabase) {
        database.organizationDao().upsert(SeedData.defaultOrganization)
        database.localUserDao().upsert(SeedData.defaultUser)
        database.childDao().upsert(SeedData.defaultChild)
        database.childBindingDao().upsert(SeedData.defaultBinding)
        database.knowledgeDao().insertAll(SeedData.knowledgeItems + SeedData.taskItems)
    }

    fun seedAsync(database: QizhiDatabase) {
        CoroutineScope(Dispatchers.IO).launch { seed(database) }
    }
}
