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
    version = 2,
    exportSchema = false
)
abstract class QizhiDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun trainingRecordDao(): TrainingRecordDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun safetyFlagDao(): SafetyFlagDao
    abstract fun planDao(): PlanDao
    abstract fun conversationDao(): ConversationDao
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
                ).addMigrations(MIGRATION_1_2).build().also {
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
    }
}
