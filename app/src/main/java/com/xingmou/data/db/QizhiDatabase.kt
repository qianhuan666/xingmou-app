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
        TrainingRecordEntity::class,
        AbilityProfileEntity::class,
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
    version = 3,
    exportSchema = false
)
abstract class QizhiDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun childBindingDao(): ChildBindingDao
    abstract fun trainingRecordDao(): TrainingRecordDao
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also {
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
    }
}
