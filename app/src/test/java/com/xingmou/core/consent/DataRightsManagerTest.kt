package com.xingmou.core.consent

import com.xingmou.data.db.CareRecordEntity
import com.xingmou.data.db.ChildEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRightsManagerTest {
    private val manager = DataRightsManager()

    @Test
    fun exportRequiresExplicitConsent() {
        assertFalse(manager.checkExport(ConsentStatus.NOT_GRANTED).allowed)
        assertFalse(manager.checkExport(ConsentStatus.REVOKED).allowed)
        assertTrue(manager.checkExport(ConsentStatus.GRANTED).allowed)
    }

    @Test
    fun deleteRequiresChildId() {
        assertFalse(manager.checkDelete("").allowed)
        assertTrue(manager.checkDelete("child-a").allowed)
    }

    @Test
    fun exportOmitsSensitiveChildAndCareFields() {
        val child = ChildEntity(
            childId = "child-a",
            alias = "小星",
            ageBand = "学龄期",
            communicationLevel = "SHORT_SENTENCE",
            supportLevel = "L1",
            diagnosisTranscription = "诊断原文不应出现在导出快照",
            notes = "内部备注不应出现在导出快照",
            baselineJson = "{\"private\":\"baseline\"}",
            createdAt = 1L,
            updatedAt = 1L
        )
        val care = CareRecordEntity(
            recordId = "care-a",
            childId = "child-a",
            stage = "intake",
            stageLabel = "接案",
            status = "active",
            summary = "已建立档案",
            professionalId = "professional",
            createdAt = 1L,
            updatedAt = 1L,
            note = "内部备注不导出"
        )

        val json = manager.buildAuthorizedExport(
            consentStatus = ConsentStatus.GRANTED,
            child = child,
            trainingRecords = emptyList(),
            homeTasks = emptyList(),
            homeFeedback = emptyList(),
            assessments = emptyList(),
            careRecords = listOf(care),
            exportedAt = 2L
        )

        assertTrue(json.contains("\"childId\":\"child-a\""))
        assertTrue(json.contains("\"summary\":\"已建立档案\""))
        assertFalse(json.contains("诊断原文"))
        assertFalse(json.contains("内部备注"))
        assertFalse(json.contains("baseline"))
    }

    @Test(expected = IllegalStateException::class)
    fun revokedConsentCannotBuildExport() {
        manager.buildAuthorizedExport(
            consentStatus = ConsentStatus.REVOKED,
            child = ChildEntity(
                childId = "child-a",
                alias = "小星",
                ageBand = "学龄期",
                communicationLevel = "SHORT_SENTENCE",
                supportLevel = "L1",
                createdAt = 1L,
                updatedAt = 1L
            ),
            trainingRecords = emptyList(),
            homeTasks = emptyList(),
            homeFeedback = emptyList(),
            assessments = emptyList(),
            careRecords = emptyList(),
            exportedAt = 2L
        )
    }
}
