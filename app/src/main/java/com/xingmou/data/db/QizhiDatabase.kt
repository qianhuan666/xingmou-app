package com.xingmou.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        ToolCallEntity::class,
        MemoryItemEntity::class,
        ReviewRequestEntity::class,
        DecisionTraceEntity::class
    ],
    version = 1,
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
                ).build().also {
                    INSTANCE = it
                    DatabaseSeeder.seedAsync(it)
                }
            }
    }
}
