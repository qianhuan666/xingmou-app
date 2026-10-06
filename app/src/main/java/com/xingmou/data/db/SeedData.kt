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

    val demoRoleUsers = listOf(
        defaultUser,
        LocalUserEntity("local-parent", DEMO_ORGANIZATION_ID, "本地家长", "parent", "parent", createdAt = 0L, updatedAt = 0L),
        LocalUserEntity("local-child", DEMO_ORGANIZATION_ID, "儿童体验账号", "child", "parent", createdAt = 0L, updatedAt = 0L)
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
        ),
        KnowledgeItemEntity(
            itemId = "KB-EMOTION-001",
            category = "support",
            domain = "F",
            title = "孩子遇到困难时如何引导",
            content = "1. 先蹲下来，平视孩子，轻声说：没关系，我们慢慢来。\n2. 把任务拆成最小步骤，比如拼图只给一块，指着位置说放这里。\n3. 孩子做对一步就立刻表扬：你把这一块放进去了，真棒！\n4. 全部完成后，给孩子选择奖励：贴纸、拥抱或击掌。",
            sourceRef = "LOCAL_REVIEWED_PROTOCOL:EMOTION_V1",
            verificationStatus = "verified",
            accessScope = "all_ports",
            keywordsJson = "[\"困难\",\"不会\",\"挫败\",\"引导\",\"分解\",\"安抚\",\"情绪\"]",
            updatedAt = 0L
        ),
        KnowledgeItemEntity(
            itemId = "KB-ATTENTION-001",
            category = "support",
            domain = "F",
            title = "孩子分心时如何拉回注意力",
            content = "1. 先排除干扰：关掉电视、收起玩具、拉上窗帘。\n2. 用孩子喜欢的物品吸引：看，这个小熊在等你呢！\n3. 任务时间缩短到孩子平时能专注的时长（通常 2-5 分钟）。\n4. 孩子看一眼任务就表扬：你刚才在看图片，很好！\n5. 连续两次分心就暂停，改到孩子状态好的时候再试。",
            sourceRef = "LOCAL_REVIEWED_PROTOCOL:ATTENTION_V1",
            verificationStatus = "verified",
            accessScope = "all_ports",
            keywordsJson = "[\"分心\",\"注意力\",\"走神\",\"拉回\",\"干扰\",\"专注\"]",
            updatedAt = 0L
        ),
        KnowledgeItemEntity(
            itemId = "KB-RESIST-001",
            category = "support",
            domain = "F",
            title = "孩子抗拒训练时怎么办",
            content = "1. 立刻停止，不要说：你必须做。\n2. 观察孩子状态：是累了？任务太难？还是环境不舒服？\n3. 如果太累，改到明天；如果太难，降低难度；如果环境吵，换个安静地方。\n4. 明天再试时，先玩 2 分钟孩子喜欢的游戏，再进入训练。\n5. 连续三天抗拒同一任务，暂停该任务，换其他类型。",
            sourceRef = "LOCAL_REVIEWED_PROTOCOL:RESIST_V1",
            verificationStatus = "verified",
            accessScope = "all_ports",
            keywordsJson = "[\"抗拒\",\"拒绝\",\"不愿意\",\"强迫\",\"不配合\"]",
            updatedAt = 0L
        ),
        KnowledgeItemEntity(
            itemId = "KB-REWARD-001",
            category = "support",
            domain = "F",
            title = "如何正确使用奖励",
            content = "1. 奖励要在孩子做对后 3 秒内给，延迟超过 10 秒效果减半。\n2. 表扬要具体：你说出了苹果这个名字，真清楚！比真棒更有用。\n3. 优先用社会性奖励：拥抱、击掌、一起跳一下。\n4. 物质奖励每周不超过 3 次，且孩子完成较难任务时才用。\n5. 孩子主动完成后，逐渐减少奖励频率，从每次到每三次。",
            sourceRef = "LOCAL_REVIEWED_PROTOCOL:REWARD_V1",
            verificationStatus = "verified",
            accessScope = "all_ports",
            keywordsJson = "[\"奖励\",\"表扬\",\"强化\",\"鼓励\",\"喜欢\"]",
            updatedAt = 0L
        ),
        KnowledgeItemEntity(
            itemId = "KB-LANGUAGE-001",
            category = "support",
            domain = "D",
            title = "孩子语言表达困难时",
            content = "1. 孩子用手指或眼神表达时，立刻回应：你要苹果吗？帮他说出来。\n2. 不要问：这是什么？（太抽象），改问：要苹果还是香蕉？（二选一）\n3. 孩子说出任何声音都回应：你说果了！对，这是苹果。\n4. 每天固定 10 分钟对话时间，只聊孩子正在看的、玩的东西。\n5. 孩子 5 秒没回应，再提示一次；10 秒没回应，帮他回答并表扬尝试。",
            sourceRef = "LOCAL_REVIEWED_PROTOCOL:LANGUAGE_V1",
            verificationStatus = "verified",
            accessScope = "all_ports",
            keywordsJson = "[\"语言\",\"说话\",\"表达\",\"沟通\",\"开口\"]",
            updatedAt = 0L
        ),
        KnowledgeItemEntity(
            itemId = "KB-ROUTINE-001",
            category = "family_support",
            domain = "F",
            title = "如何建立训练日常",
            content = "1. 固定时间：每天晚饭后 7:00-7:15，误差不超过 30 分钟。\n2. 固定地点：同一张桌子、同一盏灯，孩子看到环境就知道要训练了。\n3. 固定流程：打招呼 → 训练 5 分钟 → 奖励 → 结束拥抱。\n4. 用视觉提示：在冰箱上贴训练时间图片，孩子看到就知道快到时间了。\n5. 周末可以休息，但工作日尽量坚持，间断不超过 2 天。",
            sourceRef = "LOCAL_REVIEWED_PROTOCOL:ROUTINE_V1",
            verificationStatus = "verified",
            accessScope = "parent_professional",
            keywordsJson = "[\"日常\",\"规律\",\"固定\",\"时间\",\"流程\",\"习惯\"]",
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
        SeedData.demoRoleUsers.forEach { database.localUserDao().upsert(it) }
        // 默认儿童行承载可变数据（基线 JSON、profileVersion 等），绝不能每次启动 REPLACE 覆盖；
        // 仅在首次播种、档案不存在时插入。
        if (database.childDao().findById(SeedData.defaultChild.childId) == null) {
            database.childDao().upsert(SeedData.defaultChild)
        }
        database.childBindingDao().upsert(SeedData.defaultBinding)
        database.knowledgeDao().insertAll(SeedData.knowledgeItems + SeedData.taskItems)
    }

    fun seedAsync(database: QizhiDatabase) {
        CoroutineScope(Dispatchers.IO).launch { seed(database) }
    }
}
