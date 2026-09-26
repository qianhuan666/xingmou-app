package com.xingmou.core.consent

import com.google.gson.Gson
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
    val trainingRecords: List<TrainingRecordEntity>,
    val homeTasks: List<HomeTaskEntity>,
    val homeFeedback: List<HomeFeedbackEntity>,
    val assessments: List<AssessmentRecordEntity>,
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
    val summary: String,
    val createdAt: Long,
    val updatedAt: Long,
    val professionalSignedAt: Long?
)

/**
 * V1.0 数据权利策略层。
 *
 * 该类只生成当前已授权儿童范围的导出快照，不读取或输出诊断原文、
 * 备注、扫描原件、原始方案 payload、API Key 等不必要敏感字段。
 */
class DataRightsManager(private val gson: Gson = Gson()) {
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
                trainingRecords = trainingRecords,
                homeTasks = homeTasks,
                homeFeedback = homeFeedback,
                assessments = assessments,
                careRecords = careRecords.map {
                    ExportedCareRecord(
                        recordId = it.recordId,
                        stage = it.stage,
                        stageLabel = it.stageLabel,
                        status = it.status,
                        summary = it.summary,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        professionalSignedAt = it.professionalSignedAt
                    )
                }
            )
        )
    }
}
