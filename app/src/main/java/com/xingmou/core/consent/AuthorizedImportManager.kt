package com.xingmou.core.consent

import com.google.gson.Gson
import com.google.gson.JsonParser
import androidx.room.withTransaction
import com.xingmou.data.db.*
import java.util.UUID

/** 授权导出 JSON 的受限恢复，不是无损数据库备份。 */
class AuthorizedImportManager(private val gson: Gson = Gson()) {
    data class Preview(val childId: String, val alias: String, val recordCount: Int)

    fun parse(json: String): AuthorizedChildExport {
        require(json.toByteArray(Charsets.UTF_8).size <= 2_000_000) { "import_file_too_large" }
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("schema")?.asString == "xingmou_authorized_child_export") { "invalid_export_schema" }
        require(root.get("schemaVersion")?.asInt == 2) { "unsupported_export_version" }
        val data = gson.fromJson(root, AuthorizedChildExport::class.java)
        require(data.child.childId.isNotBlank() && data.child.childId.length <= 100) { "invalid_child_id" }
        require(data.child.alias.isNotBlank() && data.child.alias.length <= 24) { "invalid_alias" }
        require(data.child.status == "active") { "inactive_child_not_importable" }
        require(data.trainingRecords.size <= 20_000 && data.homeTasks.size <= 2_000 &&
            data.homeFeedback.size <= 10_000 && data.assessments.size <= 2_000 &&
            data.careRecords.size <= 2_000 && data.abilityProfiles.size <= 2_000 &&
            data.plans.size <= 2_000 && data.reviews.size <= 2_000) { "import_record_limit" }
        val ids = listOf(
            data.trainingRecords.map { it.recordId }, data.homeTasks.map { it.taskId },
            data.homeFeedback.map { it.feedbackId }, data.assessments.map { it.recordId },
            data.careRecords.map { it.recordId }, data.abilityProfiles.map { it.profileId },
            data.plans.map { it.planId }, data.reviews.map { it.reviewId }
        ).flatten()
        require(ids.all { it.isNotBlank() && it.length <= 100 } && ids.size == ids.toSet().size) { "invalid_record_ids" }
        require(data.plans.map { it.version }.all { it > 0 } &&
            data.plans.map { it.version }.distinct().size == data.plans.size) { "invalid_plan_versions" }
        return data
    }

    fun preview(data: AuthorizedChildExport): Preview = Preview(
        data.child.childId, data.child.alias,
        data.trainingRecords.size + data.homeTasks.size + data.homeFeedback.size +
            data.assessments.size + data.careRecords.size + data.abilityProfiles.size +
            data.plans.size + data.reviews.size
    )

    /** 调用方必须在 Room 事务内执行，并先确认同 ID 儿童不存在。 */
    suspend fun restore(data: AuthorizedChildExport, database: QizhiDatabase, localUserId: String): Int = database.withTransaction {
        val childId = data.child.childId
        check(database.childDao().findById(childId) == null) { "import_child_conflict" }
        val now = System.currentTimeMillis()
        database.childDao().upsert(ChildEntity(childId, data.child.alias, data.child.ageBand.take(24),
            data.child.communicationLevel, data.child.supportLevel, profileVersion = data.child.profileVersion,
            createdAt = now, updatedAt = now))
        database.childBindingDao().upsert(ChildBindingEntity(localUserId, childId, "professional", validFrom = now))
        fun freshId(): String = "import-${UUID.randomUUID()}"
        val taskIds = data.homeTasks.associate { it.taskId to freshId() }
        val planIds = data.plans.associate { it.planId to freshId() }
        val assessmentIds = data.assessments.associate { it.recordId to freshId() }
        data.trainingRecords.forEach { item ->
            database.trainingRecordDao().insert(TrainingRecordEntity(freshId(), childId, item.domain.take(40),
                item.taskId.take(100), item.difficulty.coerceIn(1, 10), item.supportLevel.take(20),
                item.reactionMs, item.errorType?.take(80), item.firstCorrect, item.correct,
                item.promptLevel.coerceIn(0, 10), item.createdAt))
        }
        data.homeTasks.forEach { item ->
            database.homeTaskDao().upsert(HomeTaskEntity(taskIds.getValue(item.taskId), childId,
                item.title.take(100), "由授权导出文件恢复；原描述未包含在导出范围内。",
                status = item.status.take(30), dueAt = item.dueAt,
                durationMinutes = item.durationMinutes.coerceIn(1, 60), supportLevel = item.supportLevel.take(20),
                updatedAt = item.updatedAt))
        }
        data.homeFeedback.forEach { item ->
            database.homeFeedbackDao().insert(HomeFeedbackEntity(freshId(), childId,
                item.taskId?.let(taskIds::get), item.mood.take(40), item.fatigue.take(40),
                "", item.createdAt))
        }
        data.assessments.forEach { item ->
            database.assessmentRecordDao().upsert(AssessmentRecordEntity(assessmentIds.getValue(item.recordId),
                childId, item.assessmentId.take(80), item.assessmentName.take(100), item.version,
                "imported", item.assessmentDate.take(40), "授权导出恢复", item.scoresJson.take(10_000),
                "", item.status.take(30), item.createdAt, now))
        }
        data.abilityProfiles.forEach { item ->
            val linked = runCatching { JsonParser.parseString(item.assessmentRecordIdsJson).asJsonArray.map { it.asString } }.getOrDefault(emptyList())
            database.abilityProfileDao().upsert(AbilityProfileEntity(freshId(), childId, item.status.take(30),
                item.scoresJson.take(10_000), item.confidence.coerceIn(0.0, 1.0), "[]",
                gson.toJson(linked.mapNotNull(assessmentIds::get)), item.createdAt))
        }
        data.plans.forEach { item ->
            val payload = gson.toJson(mapOf(
                "priority_domain" to item.priorityDomain?.take(40), "observable_goal" to item.observableGoal?.take(160),
                "task" to item.task?.take(100), "difficulty" to item.difficulty?.coerceIn(1, 10),
                "support_level" to item.supportLevel?.take(20), "frequency" to item.frequency?.take(80),
                "duration" to item.duration?.take(40), "stop_conditions" to item.stopConditions?.take(160)
            ))
            // 恢复为待审核草案，不继承原设备上的已签署/生效状态。
            database.planDao().upsert(PlanVersionEntity(planIds.getValue(item.planId), childId, item.version,
                "draft", true, payload, item.createdAt, now))
        }
        data.careRecords.forEach { item ->
            database.careRecordDao().insert(CareRecordEntity(freshId(), childId, item.stage.take(40),
                item.stageLabel.take(80), "imported", "由授权导出文件恢复，需专业人员复核。",
                professionalId = localUserId, createdAt = item.createdAt, updatedAt = now))
        }
        data.reviews.forEach { item ->
            val mappedPlanId = planIds[item.targetId] ?: return@forEach
            database.agentDao().upsertReview(ReviewRequestEntity(freshId(), childId, null, item.targetType.take(40),
                mappedPlanId, "{}", null, "pending", null, "导入后需重新审核", now, null))
        }
        // 不导入旧同意：远程 AI 与导出都必须在本设备重新明确授权。
        preview(data).recordCount
    }
}
