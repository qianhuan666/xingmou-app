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

    @Query("SELECT * FROM children WHERE status = 'active' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun firstActive(): ChildEntity?

    @Query("UPDATE children SET alias = :alias, ageBand = :ageBand, updatedAt = :updatedAt WHERE childId = :childId")
    suspend fun updateBasicProfile(childId: String, alias: String, ageBand: String, updatedAt: Long)

    @Query("UPDATE children SET baselineJson = :baselineJson, profileVersion = :profileVersion, updatedAt = :updatedAt WHERE childId = :childId")
    suspend fun updateBaseline(childId: String, baselineJson: String, profileVersion: Int, updatedAt: Long)

    @Query("UPDATE children SET status = 'archived', updatedAt = :updatedAt WHERE childId = :childId")
    suspend fun archive(childId: String, updatedAt: Long)
}

@Dao
interface AbilityProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: AbilityProfileEntity)

    @Query("SELECT * FROM ability_profiles WHERE childId = :childId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForChild(childId: String): AbilityProfileEntity?
}

@Dao
interface ChildBindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(binding: ChildBindingEntity)

    @Query("SELECT * FROM child_bindings WHERE userId = :userId AND status = 'active' ORDER BY validFrom DESC")
    suspend fun activeForUser(userId: String): List<ChildBindingEntity>

    @Query("SELECT * FROM child_bindings WHERE userId = :userId AND childId = :childId AND status = 'active' LIMIT 1")
    suspend fun findActive(userId: String, childId: String): ChildBindingEntity?
}

@Dao
interface HomeTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: HomeTaskEntity)

    @Query("SELECT * FROM home_tasks WHERE childId = :childId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestForChild(childId: String): HomeTaskEntity?

    @Query("SELECT * FROM home_tasks WHERE childId = :childId ORDER BY updatedAt DESC")
    suspend fun allForChild(childId: String): List<HomeTaskEntity>

    @Query("UPDATE home_tasks SET status = :status, updatedAt = :updatedAt WHERE taskId = :taskId AND childId = :childId")
    suspend fun updateStatus(childId: String, taskId: String, status: String, updatedAt: Long)

    @Query("UPDATE home_tasks SET demoStep = :demoStep, updatedAt = :updatedAt WHERE taskId = :taskId AND childId = :childId")
    suspend fun updateDemoStep(childId: String, taskId: String, demoStep: Int, updatedAt: Long)
}

@Dao
interface HomeFeedbackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: HomeFeedbackEntity)

    @Query("SELECT * FROM home_feedback WHERE childId = :childId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentForChild(childId: String, limit: Int = 10): List<HomeFeedbackEntity>
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
interface ConsentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(consent: ConsentEntity)

    @Query("SELECT * FROM consents WHERE childId = :childId AND purpose = :purpose LIMIT 1")
    suspend fun find(childId: String, purpose: String): ConsentEntity?

    @Query("SELECT * FROM consents WHERE childId = :childId ORDER BY purpose")
    suspend fun forChild(childId: String): List<ConsentEntity>
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

    @Query("SELECT * FROM review_requests WHERE childId = :childId AND status = 'pending' ORDER BY createdAt DESC LIMIT 1")
    suspend fun pendingReviewForChild(childId: String): ReviewRequestEntity?

    @Query("SELECT * FROM review_requests WHERE targetId = :targetId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestReviewForTarget(targetId: String): ReviewRequestEntity?
}
