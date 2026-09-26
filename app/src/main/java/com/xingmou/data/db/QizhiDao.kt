package com.xingmou.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM ability_profiles WHERE childId = :childId ORDER BY createdAt DESC")
    suspend fun allForChild(childId: String): List<AbilityProfileEntity>
}

@Dao
interface AssessmentRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: AssessmentRecordEntity)

    @Query("SELECT * FROM assessment_records WHERE childId = :childId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentForChild(childId: String, limit: Int = 10): List<AssessmentRecordEntity>

    @Query("SELECT * FROM assessment_records WHERE childId = :childId ORDER BY createdAt DESC")
    suspend fun allForChild(childId: String): List<AssessmentRecordEntity>

    @Query("SELECT COALESCE(MAX(version), 0) FROM assessment_records WHERE childId = :childId AND assessmentId = :assessmentId")
    suspend fun latestVersion(childId: String, assessmentId: String): Int
}

@Dao
interface CareRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CareRecordEntity)

    @Query("SELECT * FROM care_records WHERE childId = :childId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForChild(childId: String): CareRecordEntity?

    @Query("SELECT * FROM care_records WHERE childId = :childId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentForChild(childId: String, limit: Int = 20): List<CareRecordEntity>

    @Query("SELECT * FROM care_records WHERE childId = :childId ORDER BY createdAt DESC")
    suspend fun allForChild(childId: String): List<CareRecordEntity>
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

    @Query("SELECT * FROM home_feedback WHERE childId = :childId ORDER BY createdAt DESC")
    suspend fun allForChild(childId: String): List<HomeFeedbackEntity>
}

@Dao
interface TrainingRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TrainingRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<TrainingRecordEntity>)

    @Query("SELECT * FROM training_records WHERE childId = :childId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentForChild(childId: String, limit: Int = 30): List<TrainingRecordEntity>

    @Query("SELECT * FROM training_records WHERE childId = :childId ORDER BY createdAt DESC")
    suspend fun allForChild(childId: String): List<TrainingRecordEntity>

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
    suspend fun allForChild(childId: String): List<PlanVersionEntity>

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
interface DataRightsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequest(request: DataRequestEntity)

    @Query("SELECT * FROM data_requests WHERE childId = :childId ORDER BY requestedAt DESC")
    suspend fun requestsForChild(childId: String): List<DataRequestEntity>

    @Query("SELECT * FROM data_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun findRequest(requestId: String): DataRequestEntity?

    @Query("SELECT COUNT(*) FROM children WHERE childId = :childId")
    suspend fun childCount(childId: String): Int

    @Transaction
    suspend fun executeChildDeletion(request: DataRequestEntity, completedAt: Long): Int {
        require(request.requestType == "DELETE" && request.status == "requested")
        check(childCount(request.childId) == 1) { "child_not_found" }
        upsertRequest(request)
        val deleted = purgeChildData(request.childId)
        check(deleted > 0) { "child_not_found" }
        completeRequest(request.requestId, "completed", completedAt,
            """{"deletedRows":$deleted,"scope":"child"}""")
        return deleted
    }

    @Query("UPDATE data_requests SET status = :status, completedAt = :completedAt, resultJson = :resultJson WHERE requestId = :requestId")
    suspend fun completeRequest(requestId: String, status: String, completedAt: Long, resultJson: String?)

    @Query("SELECT runId FROM agent_runs WHERE childId = :childId")
    suspend fun runIdsForChild(childId: String): List<String>

    @Query("DELETE FROM child_bindings WHERE childId = :childId")
    suspend fun deleteChildBindings(childId: String): Int

    @Query("DELETE FROM home_tasks WHERE childId = :childId")
    suspend fun deleteHomeTasks(childId: String): Int

    @Query("DELETE FROM home_feedback WHERE childId = :childId")
    suspend fun deleteHomeFeedback(childId: String): Int

    @Query("DELETE FROM training_records WHERE childId = :childId")
    suspend fun deleteTrainingRecords(childId: String): Int

    @Query("DELETE FROM ability_profiles WHERE childId = :childId")
    suspend fun deleteAbilityProfiles(childId: String): Int

    @Query("DELETE FROM assessment_records WHERE childId = :childId")
    suspend fun deleteAssessmentRecords(childId: String): Int

    @Query("DELETE FROM care_records WHERE childId = :childId")
    suspend fun deleteCareRecords(childId: String): Int

    @Query("DELETE FROM safety_flags WHERE childId = :childId")
    suspend fun deleteSafetyFlags(childId: String): Int

    @Query("DELETE FROM plan_versions WHERE childId = :childId")
    suspend fun deletePlans(childId: String): Int

    @Query("DELETE FROM conversation_messages WHERE childId = :childId")
    suspend fun deleteConversationMessages(childId: String): Int

    @Query("DELETE FROM consents WHERE childId = :childId")
    suspend fun deleteConsents(childId: String): Int

    @Query("DELETE FROM review_requests WHERE childId = :childId")
    suspend fun deleteReviewRequests(childId: String): Int

    @Query("DELETE FROM memory_items WHERE childId = :childId")
    suspend fun deleteMemoryItems(childId: String): Int

    @Query("DELETE FROM agent_steps WHERE runId IN (:runIds)")
    suspend fun deleteAgentSteps(runIds: List<String>): Int

    @Query("DELETE FROM tool_calls WHERE runId IN (:runIds)")
    suspend fun deleteToolCalls(runIds: List<String>): Int

    @Query("DELETE FROM decision_traces WHERE runId IN (:runIds)")
    suspend fun deleteDecisionTraces(runIds: List<String>): Int

    @Query("DELETE FROM agent_events WHERE childId = :childId")
    suspend fun deleteChildAgentEvents(childId: String): Int

    @Query("DELETE FROM agent_events WHERE runId IN (:runIds)")
    suspend fun deleteRunAgentEvents(runIds: List<String>): Int

    @Query("DELETE FROM agent_runs WHERE childId = :childId")
    suspend fun deleteAgentRuns(childId: String): Int

    @Query("DELETE FROM children WHERE childId = :childId")
    suspend fun deleteChild(childId: String): Int

    /** 删除儿童授权范围内的数据，但保留 data_requests 作为删除结果报告索引。 */
    @Transaction
    suspend fun purgeChildData(childId: String): Int {
        val runIds = runIdsForChild(childId)
        var deleted = 0
        deleted += deleteChildBindings(childId)
        deleted += deleteHomeTasks(childId)
        deleted += deleteHomeFeedback(childId)
        deleted += deleteTrainingRecords(childId)
        deleted += deleteAbilityProfiles(childId)
        deleted += deleteAssessmentRecords(childId)
        deleted += deleteCareRecords(childId)
        deleted += deleteSafetyFlags(childId)
        deleted += deletePlans(childId)
        deleted += deleteConversationMessages(childId)
        deleted += deleteConsents(childId)
        deleted += deleteReviewRequests(childId)
        deleted += deleteMemoryItems(childId)
        if (runIds.isNotEmpty()) {
            deleted += deleteAgentSteps(runIds)
            deleted += deleteToolCalls(runIds)
            deleted += deleteDecisionTraces(runIds)
            deleted += deleteRunAgentEvents(runIds)
        }
        deleted += deleteChildAgentEvents(childId)
        deleted += deleteAgentRuns(childId)
        deleted += deleteChild(childId)
        return deleted
    }
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

    @Query("SELECT * FROM agent_runs WHERE childId = :childId ORDER BY startedAt DESC")
    suspend fun runsForChild(childId: String): List<AgentRunEntity>

    @Query("SELECT * FROM tool_calls WHERE runId IN (SELECT runId FROM agent_runs WHERE childId = :childId)")
    suspend fun toolCallsForChild(childId: String): List<ToolCallEntity>

    @Query("SELECT * FROM agent_events WHERE childId = :childId")
    suspend fun eventsForChild(childId: String): List<AgentEventEntity>

    @Query("SELECT * FROM agent_steps WHERE runId IN (SELECT runId FROM agent_runs WHERE childId = :childId)")
    suspend fun stepsForChild(childId: String): List<AgentStepEntity>

    @Query("SELECT * FROM review_requests WHERE childId = :childId")
    suspend fun reviewsForChild(childId: String): List<ReviewRequestEntity>

    @Query("SELECT * FROM agent_steps WHERE runId = :runId ORDER BY stepIndex ASC")
    suspend fun steps(runId: String): List<AgentStepEntity>

    @Query("SELECT * FROM agent_events WHERE runId = :runId ORDER BY createdAt ASC")
    suspend fun events(runId: String): List<AgentEventEntity>

    @Query("SELECT * FROM tool_calls WHERE runId = :runId ORDER BY createdAt ASC")
    suspend fun toolCalls(runId: String): List<ToolCallEntity>

    @Query("SELECT * FROM decision_traces WHERE runId = :runId ORDER BY stepIndex ASC")
    suspend fun traces(runId: String): List<DecisionTraceEntity>

    @Query("SELECT * FROM decision_traces WHERE runId IN (SELECT runId FROM agent_runs WHERE childId = :childId)")
    suspend fun tracesForChild(childId: String): List<DecisionTraceEntity>

    @Query("UPDATE decision_traces SET humanDecision = :decision WHERE traceId = :traceId AND runId = :runId")
    suspend fun annotateTrace(runId: String, traceId: String, decision: String): Int

    @Query("SELECT * FROM review_requests WHERE runId = :runId ORDER BY createdAt ASC")
    suspend fun reviews(runId: String): List<ReviewRequestEntity>

    @Query("SELECT * FROM review_requests WHERE childId = :childId AND status = 'pending' ORDER BY createdAt DESC LIMIT 1")
    suspend fun pendingReviewForChild(childId: String): ReviewRequestEntity?

    @Query("SELECT * FROM review_requests WHERE targetId = :targetId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestReviewForTarget(targetId: String): ReviewRequestEntity?
}
