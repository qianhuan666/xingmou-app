package com.xingmou.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "children")
data class ChildEntity(
    @androidx.room.PrimaryKey val childId: String,
    val alias: String,
    val ageBand: String,
    val communicationLevel: String,
    val supportLevel: String,
    val birthYear: Int? = null,
    val languageLevel: String? = null,
    val adlLevel: String? = null,
    val diagnosisTranscription: String? = null,
    val diagnosisSource: String? = null,
    val notes: String? = null,
    val avatarColor: String = "coral",
    val baselineJson: String? = null,
    val profileVersion: Int = 0,
    val status: String = "active",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "child_bindings",
    primaryKeys = ["userId", "childId"],
    indices = [Index("userId"), Index("childId"), Index(value = ["userId", "childId", "status"])]
)
data class ChildBindingEntity(
    val userId: String,
    val childId: String,
    val role: String,
    val status: String = "active",
    val validFrom: Long,
    val validTo: Long? = null
)

@Entity(tableName = "home_tasks", indices = [Index("childId"), Index(value = ["childId", "status"])])
data class HomeTaskEntity(
    @androidx.room.PrimaryKey val taskId: String,
    val childId: String,
    val title: String,
    val description: String,
    val status: String = "pending",
    val dueAt: Long? = null,
    val planId: String? = null,
    val planVersion: Int? = null,
    val frequency: String = "按需",
    val durationMinutes: Int = 5,
    val supportLevel: String = "L1",
    val stopConditions: String = "出现疲劳、拒绝或风险时暂停",
    val source: String = "LOCAL_TEMPLATE",
    val demoStep: Int = 0,
    val updatedAt: Long
)

@Entity(tableName = "home_feedback", indices = [Index("childId"), Index(value = ["childId", "createdAt"])])
data class HomeFeedbackEntity(
    @androidx.room.PrimaryKey val feedbackId: String,
    val childId: String,
    val taskId: String?,
    val mood: String,
    val fatigue: String,
    val note: String,
    val createdAt: Long
)

@Entity(
    tableName = "training_records",
    indices = [Index("childId"), Index(value = ["childId", "createdAt"])]
)
data class TrainingRecordEntity(
    @androidx.room.PrimaryKey val recordId: String,
    val childId: String,
    val domain: String,
    val taskId: String,
    val difficulty: Int,
    val supportLevel: String,
    val reactionMs: Long?,
    val errorType: String?,
    val firstCorrect: Boolean,
    val correct: Boolean,
    val promptLevel: Int,
    val createdAt: Long
)

@Entity(
    tableName = "ability_profiles",
    indices = [Index("childId"), Index(value = ["childId", "status"])]
)
data class AbilityProfileEntity(
    @androidx.room.PrimaryKey val profileId: String,
    val childId: String,
    val status: String,
    val scoresJson: String,
    val confidence: Double,
    val evidenceJson: String,
    val assessmentRecordIdsJson: String = "[]",
    val createdAt: Long
)

@Entity(
    tableName = "assessment_records",
    indices = [Index("childId"), Index(value = ["childId", "assessmentId"]), Index(value = ["childId", "createdAt"])]
)
data class AssessmentRecordEntity(
    @androidx.room.PrimaryKey val recordId: String,
    val childId: String,
    val assessmentId: String,
    val assessmentName: String,
    val version: Int,
    val recordType: String,
    val assessmentDate: String,
    val source: String,
    val scoresJson: String,
    val notes: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "sources")
data class SourceEntity(
    @androidx.room.PrimaryKey val sourceId: String,
    val sourceCategory: String,
    val sourceType: String,
    val title: String,
    val authorsOrOrganization: String?,
    val publicationOrReportDate: String?,
    val verificationStatus: String,
    val privacySafeLabel: String?,
    val createdAt: Long
)

@Entity(
    tableName = "claims",
    indices = [Index("createdAt")]
)
data class ClaimEntity(
    @androidx.room.PrimaryKey val claimId: String,
    val claimType: String,
    val statement: String,
    val certainty: String,
    val sourceIdsJson: String,
    val limitations: String?,
    val recommendedAction: String?,
    val createdAt: Long
)

@Entity(
    tableName = "knowledge_items",
    indices = [Index("category"), Index("domain"), Index("verificationStatus")]
)
data class KnowledgeItemEntity(
    @androidx.room.PrimaryKey val itemId: String,
    val category: String,
    val domain: String?,
    val title: String,
    val content: String,
    val sourceRef: String?,
    val verificationStatus: String,
    val accessScope: String,
    val keywordsJson: String,
    val isActive: Boolean = true,
    val updatedAt: Long
)

@Entity(
    tableName = "safety_flags",
    indices = [Index("childId"), Index(value = ["childId", "status"])]
)
data class SafetyFlagEntity(
    @androidx.room.PrimaryKey val flagId: String,
    val childId: String,
    val riskType: String,
    val level: String,
    val status: String,
    val triggerText: String?,
    val actionTaken: String?,
    val createdAt: Long,
    val resolvedAt: Long?
)

@Entity(
    tableName = "plan_versions",
    indices = [Index("childId"), Index(value = ["childId", "version"], unique = true)]
)
data class PlanVersionEntity(
    @androidx.room.PrimaryKey val planId: String,
    val childId: String,
    val version: Int,
    val status: String,
    val reviewRequired: Boolean,
    val payloadJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "conversation_messages",
    indices = [Index("childId"), Index(value = ["sessionId", "createdAt"])]
)
data class ConversationMessageEntity(
    @androidx.room.PrimaryKey val messageId: String,
    val sessionId: String,
    val childId: String?,
    val port: String,
    val role: String,
    val content: String,
    val createdAt: Long
)

@Entity(
    tableName = "consents",
    indices = [Index("childId"), Index(value = ["childId", "purpose"], unique = true)]
)
data class ConsentEntity(
    @androidx.room.PrimaryKey val consentId: String,
    val childId: String,
    val purpose: String,
    val status: String,
    val grantedAt: Long?,
    val revokedAt: Long?
)

@Entity(
    tableName = "agent_runs",
    indices = [Index("childId"), Index("status"), Index("startedAt")]
)
data class AgentRunEntity(
    @androidx.room.PrimaryKey val runId: String,
    val taskType: String,
    val port: String,
    val childId: String?,
    val state: String,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val errorMessage: String?
)

@Entity(
    tableName = "agent_steps",
    indices = [Index(value = ["runId", "stepIndex"], unique = true)]
)
data class AgentStepEntity(
    @androidx.room.PrimaryKey val stepId: String,
    val runId: String,
    val stepIndex: Int,
    val state: String,
    val actionType: String?,
    val inputSummary: String?,
    val toolResultSummary: String?,
    val errorMessage: String?,
    val retryCount: Int,
    val createdAt: Long
)

@Entity(
    tableName = "agent_events",
    indices = [Index("runId"), Index("childId"), Index("eventType"), Index("createdAt")]
)
data class AgentEventEntity(
    @androidx.room.PrimaryKey val eventId: String,
    val runId: String?,
    val childId: String?,
    val eventType: String,
    val payloadSummary: String,
    val status: String,
    val createdAt: Long,
    val processedAt: Long?
)

@Entity(
    tableName = "tool_calls",
    indices = [Index("runId"), Index("toolName")]
)
data class ToolCallEntity(
    @androidx.room.PrimaryKey val callId: String,
    val runId: String,
    val toolName: String,
    val argumentsSummary: String,
    val authorizationStatus: String,
    val executionStatus: String,
    val idempotencyKey: String,
    val resultSummary: String?,
    val createdAt: Long,
    val finishedAt: Long?
)

@Entity(
    tableName = "memory_items",
    indices = [Index("childId"), Index(value = ["childId", "status"])]
)
data class MemoryItemEntity(
    @androidx.room.PrimaryKey val memoryId: String,
    val childId: String?,
    val scope: String,
    val content: String,
    val sourceRef: String?,
    val confidence: String,
    val status: String,
    val expiresAt: Long?,
    val createdAt: Long
)

@Entity(
    tableName = "review_requests",
    indices = [Index("childId"), Index("status"), Index("createdAt")]
)
data class ReviewRequestEntity(
    @androidx.room.PrimaryKey val reviewId: String,
    val childId: String,
    val runId: String?,
    val targetType: String,
    val targetId: String,
    val draftJson: String,
    val diffJson: String?,
    val status: String,
    val reviewerId: String?,
    val reviewerComment: String?,
    val createdAt: Long,
    val resolvedAt: Long?
)

@Entity(
    tableName = "decision_traces",
    indices = [Index("runId"), Index("createdAt")]
)
data class DecisionTraceEntity(
    @androidx.room.PrimaryKey val traceId: String,
    val runId: String,
    val stepIndex: Int,
    val promptVersion: String?,
    val modelVersion: String?,
    val ruleRefsJson: String,
    val knowledgeRefsJson: String,
    val toolRefsJson: String,
    val humanDecision: String?,
    val createdAt: Long
)
