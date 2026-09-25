package com.xingmou.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChildEntity::class,
        ChildBindingEntity::class,
        HomeTaskEntity::class,
        HomeFeedbackEntity::class,
        TrainingRecordEntity::class,
        AbilityProfileEntity::class,
        AssessmentRecordEntity::class,
        SourceEntity::class,
        ClaimEntity::class,
        KnowledgeItemEntity::class,
        SafetyFlagEntity::class,
        PlanVersionEntity::class,
        ConversationMessageEntity::class,
        ConsentEntity::class,
        AgentRunEntity::class,
        AgentStepEntity::class,
        AgentEventEntity::class,
        ToolCallEntity::class,
        MemoryItemEntity::class,
        ReviewRequestEntity::class,
        DecisionTraceEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class QizhiDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun childBindingDao(): ChildBindingDao
    abstract fun homeTaskDao(): HomeTaskDao
    abstract fun homeFeedbackDao(): HomeFeedbackDao
    abstract fun trainingRecordDao(): TrainingRecordDao
    abstract fun abilityProfileDao(): AbilityProfileDao
    abstract fun assessmentRecordDao(): AssessmentRecordDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun safetyFlagDao(): SafetyFlagDao
    abstract fun planDao(): PlanDao
    abstract fun conversationDao(): ConversationDao
    abstract fun consentDao(): ConsentDao
    abstract fun agentDao(): AgentDao

    companion object {
        @Volatile
        private var INSTANCE: QizhiDatabase? = null

        fun getInstance(context: Context): QizhiDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QizhiDatabase::class.java,
                    "qizhi_training.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also {
                    INSTANCE = it
                    DatabaseSeeder.seedAsync(it)
                }
            }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `agent_events` (
                        `eventId` TEXT NOT NULL,
                        `runId` TEXT,
                        `childId` TEXT,
                        `eventType` TEXT NOT NULL,
                        `payloadSummary` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `processedAt` INTEGER,
                        PRIMARY KEY(`eventId`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_events_runId` ON `agent_events` (`runId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_events_childId` ON `agent_events` (`childId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_events_eventType` ON `agent_events` (`eventType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_events_createdAt` ON `agent_events` (`createdAt`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE children ADD COLUMN birthYear INTEGER")
                db.execSQL("ALTER TABLE children ADD COLUMN languageLevel TEXT")
                db.execSQL("ALTER TABLE children ADD COLUMN adlLevel TEXT")
                db.execSQL("ALTER TABLE children ADD COLUMN diagnosisTranscription TEXT")
                db.execSQL("ALTER TABLE children ADD COLUMN diagnosisSource TEXT")
                db.execSQL("ALTER TABLE children ADD COLUMN notes TEXT")
                db.execSQL("ALTER TABLE children ADD COLUMN avatarColor TEXT NOT NULL DEFAULT 'coral'")
                db.execSQL("ALTER TABLE children ADD COLUMN baselineJson TEXT")
                db.execSQL("ALTER TABLE children ADD COLUMN profileVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `child_bindings` (
                        `userId` TEXT NOT NULL,
                        `childId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `validFrom` INTEGER NOT NULL,
                        `validTo` INTEGER,
                        PRIMARY KEY(`userId`, `childId`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_child_bindings_userId` ON `child_bindings` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_child_bindings_childId` ON `child_bindings` (`childId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_child_bindings_userId_childId_status` ON `child_bindings` (`userId`, `childId`, `status`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `home_tasks` (
                        `taskId` TEXT NOT NULL,
                        `childId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `dueAt` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`taskId`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_home_tasks_childId` ON `home_tasks` (`childId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_home_tasks_childId_status` ON `home_tasks` (`childId`, `status`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `home_feedback` (
                        `feedbackId` TEXT NOT NULL,
                        `childId` TEXT NOT NULL,
                        `taskId` TEXT,
                        `mood` TEXT NOT NULL,
                        `fatigue` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`feedbackId`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_home_feedback_childId` ON `home_feedback` (`childId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_home_feedback_childId_createdAt` ON `home_feedback` (`childId`, `createdAt`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE home_tasks ADD COLUMN planId TEXT")
                db.execSQL("ALTER TABLE home_tasks ADD COLUMN planVersion INTEGER")
                db.execSQL("ALTER TABLE home_tasks ADD COLUMN frequency TEXT NOT NULL DEFAULT '按需'")
                db.execSQL("ALTER TABLE home_tasks ADD COLUMN durationMinutes INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE home_tasks ADD COLUMN supportLevel TEXT NOT NULL DEFAULT 'L1'")
                db.execSQL("ALTER TABLE home_tasks ADD COLUMN stopConditions TEXT NOT NULL DEFAULT '出现疲劳、拒绝或风险时暂停'")
                db.execSQL("ALTER TABLE home_tasks ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL_TEMPLATE'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE home_tasks ADD COLUMN demoStep INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `assessment_records` (
                        `recordId` TEXT NOT NULL,
                        `childId` TEXT NOT NULL,
                        `assessmentId` TEXT NOT NULL,
                        `assessmentName` TEXT NOT NULL,
                        `version` INTEGER NOT NULL,
                        `recordType` TEXT NOT NULL,
                        `assessmentDate` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `scoresJson` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`recordId`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assessment_records_childId` ON `assessment_records` (`childId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assessment_records_childId_assessmentId` ON `assessment_records` (`childId`, `assessmentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assessment_records_childId_createdAt` ON `assessment_records` (`childId`, `createdAt`)")
            }
        }
    }
}
