package com.xingmou.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(child: ChildEntity)

    @Query("SELECT * FROM children WHERE status = 'active' ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children WHERE childId = :childId LIMIT 1")
    suspend fun findById(childId: String): ChildEntity?
}

@Dao
interface TrainingRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TrainingRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<TrainingRecordEntity>)

    @Query("SELECT * FROM training_records WHERE childId = :childId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentForChild(childId: String, limit: Int = 30): List<TrainingRecordEntity>

    @Query("SELECT COUNT(*) FROM training_records WHERE childId = :childId")
    suspend fun countForChild(childId: String): Int
}

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<KnowledgeItemEntity>)

    @Query("SELECT * FROM knowledge_items WHERE isActive = 1 AND verificationStatus = 'verified' ORDER BY category, itemId")
    suspend fun verifiedItems(): List<KnowledgeItemEntity>

    @Query("SELECT * FROM knowledge_items WHERE isActive = 1 AND verificationStatus = 'verified' AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR keywordsJson LIKE '%' || :query || '%') LIMIT :limit")
    suspend fun search(query: String, limit: Int = 3): List<KnowledgeItemEntity>
}

@Dao
interface SafetyFlagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(flag: SafetyFlagEntity)

    @Query("SELECT * FROM safety_flags WHERE childId = :childId AND status = 'active' ORDER BY createdAt DESC")
    fun observeActive(childId: String): Flow<List<SafetyFlagEntity>>
}

@Dao
interface PlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: PlanVersionEntity)

    @Query("SELECT * FROM plan_versions WHERE childId = :childId ORDER BY version DESC LIMIT 1")
    suspend fun latest(childId: String): PlanVersionEntity?

    @Query("SELECT * FROM plan_versions WHERE childId = :childId ORDER BY version DESC")
    fun observeVersions(childId: String): Flow<List<PlanVersionEntity>>
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ConversationMessageEntity)

    @Query("SELECT * FROM conversation_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun forSession(sessionId: String): List<ConversationMessageEntity>
}

@Dao
interface AgentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: AgentRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: AgentStepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: AgentEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolCall(call: ToolCallEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrace(trace: DecisionTraceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReview(review: ReviewRequestEntity)

    @Query("SELECT * FROM agent_runs WHERE runId = :runId LIMIT 1")
    suspend fun findRun(runId: String): AgentRunEntity?

    @Query("SELECT * FROM agent_steps WHERE runId = :runId ORDER BY stepIndex ASC")
    suspend fun steps(runId: String): List<AgentStepEntity>

    @Query("SELECT * FROM agent_events WHERE runId = :runId ORDER BY createdAt ASC")
    suspend fun events(runId: String): List<AgentEventEntity>
}
