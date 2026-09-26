package com.xingmou.core.consent

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.xingmou.data.db.AssessmentRecordEntity
import com.xingmou.data.db.ChildEntity
import com.xingmou.data.db.CareRecordEntity
import com.xingmou.data.db.HomeFeedbackEntity
import com.xingmou.data.db.HomeTaskEntity
import com.xingmou.data.db.TrainingRecordEntity

enum class DataRequestType { EXPORT, DELETE }

data class DataRightsDecision(
    val allowed: Boolean,
    val reason: String
)

data class AuthorizedChildExport(
    val schema: String = "xingmou_authorized_child_export",
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val child: ExportedChild,
    val trainingRecords: List<ExportedTrainingRecord>,
    val homeTasks: List<ExportedHomeTask>,
    val homeFeedback: List<ExportedHomeFeedback>,
    val assessments: List<ExportedAssessment>,
    val careRecords: List<ExportedCareRecord>
)

data class ExportedChild(
    val childId: String,
    val alias: String,
    val ageBand: String,
    val communicationLevel: String,
    val supportLevel: String,
    val profileVersion: Int,
    val status: String
)

data class ExportedCareRecord(
    val recordId: String,
    val stage: String,
    val stageLabel: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val professionalSignedAt: Long?
)

data class ExportedTrainingRecord(
    val recordId: String, val domain: String, val taskId: String,
    val difficulty: Int, val supportLevel: String, val reactionMs: Long?,
    val errorType: String?, val firstCorrect: Boolean, val correct: Boolean,
    val promptLevel: Int, val createdAt: Long
)

data class ExportedHomeTask(
    val taskId: String, val title: String, val status: String,
    val dueAt: Long?, val durationMinutes: Int, val supportLevel: String,
    val updatedAt: Long
)

data class ExportedHomeFeedback(
    val feedbackId: String, val taskId: String?, val mood: String,
    val fatigue: String, val createdAt: Long
)

data class ExportedAssessment(
    val recordId: String, val assessmentId: String, val assessmentName: String,
    val version: Int, val assessmentDate: String, val scoresJson: String,
    val status: String, val createdAt: Long
)

/**
 * V1.0 数据权利策略层。
 *
 * 该类只生成当前已授权儿童范围的导出快照，不读取或输出诊断原文、
 * 备注、扫描原件、原始方案 payload、API Key 等不必要敏感字段。
 */
class DataRightsManager(private val gson: Gson = Gson()) {
    /** 与 JSON 同一字段白名单的长表 CSV；每行一个字段，便于表格审阅。 */
    fun toCsv(authorizedJson: String): String {
        val root = JsonParser.parseString(authorizedJson).asJsonObject
        check(root.get("schema")?.asString == "xingmou_authorized_child_export") { "invalid_export_schema" }
        val lines = mutableListOf("recordType,recordIndex,field,value")
        for ((recordType, element) in root.entrySet()) {
            val objects = when {
                element.isJsonObject -> listOf(element.asJsonObject)
                element.isJsonArray -> element.asJsonArray.map { it.asJsonObject }
                else -> {
                    lines += listOf("meta", "0", recordType, element.asString).joinToString(",", transform = ::csvCell)
                    continue
                }
            }
            objects.forEachIndexed { index, obj ->
                obj.entrySet().forEach { (field, value) ->
                    val scalar = if (value.isJsonNull) "" else if (value.isJsonPrimitive) value.asString else value.toString()
                    lines += listOf(recordType, index.toString(), field, scalar).joinToString(",", transform = ::csvCell)
                }
            }
        }
        return lines.joinToString("\r\n", postfix = "\r\n")
    }

    private fun csvCell(value: String): String {
        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'" + value else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    fun checkExport(status: ConsentStatus): DataRightsDecision =
        if (status == ConsentStatus.GRANTED) {
            DataRightsDecision(true, "export_consent_granted")
        } else {
            DataRightsDecision(false, "export_requires_explicit_consent")
        }

    fun checkDelete(childId: String): DataRightsDecision =
        if (childId.isBlank()) {
            DataRightsDecision(false, "child_id_required")
        } else {
            DataRightsDecision(true, "child_delete_request_accepted")
        }

    fun buildAuthorizedExport(
        consentStatus: ConsentStatus,
        child: ChildEntity,
        trainingRecords: List<TrainingRecordEntity>,
        homeTasks: List<HomeTaskEntity>,
        homeFeedback: List<HomeFeedbackEntity>,
        assessments: List<AssessmentRecordEntity>,
        careRecords: List<CareRecordEntity>,
        exportedAt: Long
    ): String {
        val decision = checkExport(consentStatus)
        check(decision.allowed) { decision.reason }
        check(trainingRecords.all { it.childId == child.childId }) { "export_child_scope_mismatch" }
        check(homeTasks.all { it.childId == child.childId }) { "export_child_scope_mismatch" }
        check(homeFeedback.all { it.childId == child.childId }) { "export_child_scope_mismatch" }
        check(assessments.all { it.childId == child.childId }) { "export_child_scope_mismatch" }
        check(careRecords.all { it.childId == child.childId }) { "export_child_scope_mismatch" }

        return gson.toJson(
            AuthorizedChildExport(
                exportedAt = exportedAt,
                child = ExportedChild(
                    childId = child.childId,
                    alias = child.alias,
                    ageBand = child.ageBand,
                    communicationLevel = child.communicationLevel,
                    supportLevel = child.supportLevel,
                    profileVersion = child.profileVersion,
                    status = child.status
                ),
                trainingRecords = trainingRecords.map {
                    ExportedTrainingRecord(it.recordId, it.domain, it.taskId, it.difficulty,
                        it.supportLevel, it.reactionMs, it.errorType, it.firstCorrect,
                        it.correct, it.promptLevel, it.createdAt)
                },
                homeTasks = homeTasks.map {
                    ExportedHomeTask(it.taskId, it.title, it.status, it.dueAt,
                        it.durationMinutes, it.supportLevel, it.updatedAt)
                },
                homeFeedback = homeFeedback.map {
                    ExportedHomeFeedback(it.feedbackId, it.taskId, it.mood, it.fatigue, it.createdAt)
                },
                assessments = assessments.map {
                    ExportedAssessment(it.recordId, it.assessmentId, it.assessmentName,
                        it.version, it.assessmentDate, it.scoresJson, it.status, it.createdAt)
                },
                careRecords = careRecords.map {
                    ExportedCareRecord(
                        recordId = it.recordId,
                        stage = it.stage,
                        stageLabel = it.stageLabel,
                        status = it.status,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        professionalSignedAt = it.professionalSignedAt
                    )
                }
            )
        )
    }
}
