package com.xingmou.core.consent

import com.xingmou.data.db.CareRecordEntity
import com.xingmou.data.db.ChildEntity
import com.xingmou.data.db.AbilityProfileEntity
import com.xingmou.data.db.PlanVersionEntity
import com.xingmou.data.db.ReviewRequestEntity
import com.xingmou.data.db.ConsentEntity
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
        assertFalse(json.contains("已建立档案"))
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

    @Test
    fun csvUsesOnlyAuthorizedSnapshotFieldsAndEscapesCells() {
        val child = ChildEntity(
            childId = "child-a", alias = "小\"星,一", ageBand = "学龄期",
            communicationLevel = "SHORT_SENTENCE", supportLevel = "L1",
            diagnosisTranscription = "绝不导出原文", createdAt = 1L, updatedAt = 1L
        )
        val json = manager.buildAuthorizedExport(ConsentStatus.GRANTED, child,
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 2L)
        val csv = manager.toCsv(json)
        assertTrue(csv.startsWith("recordType,recordIndex,field,value"))
        assertTrue(csv.contains("\"小\"\"星,一\""))
        assertFalse(csv.contains("绝不导出原文"))
    }

    @Test
    fun csvNeutralizesSpreadsheetFormulaPrefix() {
        val json = manager.buildAuthorizedExport(
            ConsentStatus.GRANTED,
            ChildEntity("child-a", "=HYPERLINK(\"bad\")", "学龄期", "SHORT_SENTENCE", "L1",
                createdAt = 1L, updatedAt = 1L),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 2L
        )
        assertTrue(manager.toCsv(json).contains("\"'=HYPERLINK("))
    }

    @Test
    fun exportIncludesAdditionalScopedRecordsWithoutRawEvidenceOrDraft() {
        val child = ChildEntity("child-a", "小星", "学龄期", "SHORT_SENTENCE", "L1", createdAt = 1L, updatedAt = 1L)
        val profile = AbilityProfileEntity("profile-a", "child-a", "active", "{\"A\":2}", 0.8,
            "原始作答不可导出", "[\"assessment-a\"]", 1L)
        val plan = PlanVersionEntity("plan-a", "child-a", 1, "draft", true,
            "{\"observable_goal\":\"完成图片配对\",\"private\":\"内部方案秘密\"}", 1L, 1L)
        val review = ReviewRequestEntity("review-a", "child-a", "run-a", "plan", "plan-a",
            "原始草案不可导出", null, "pending", null, null, 1L, null)
        val consent = ConsentEntity("consent-a", "child-a", "remote_ai", "revoked", 1L, 2L)
        val json = manager.buildAuthorizedExport(ConsentStatus.GRANTED, child,
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 3L,
            listOf(profile), listOf(plan), listOf(review), listOf(consent))
        assertTrue(json.contains("\"schemaVersion\":2"))
        assertTrue(json.contains("完成图片配对"))
        assertTrue(json.contains("assessment-a"))
        assertTrue(json.contains("remote_ai"))
        assertFalse(json.contains("原始作答不可导出"))
        assertFalse(json.contains("内部方案秘密"))
        assertFalse(json.contains("原始草案不可导出"))
        assertTrue(manager.toCsv(json).contains("abilityProfiles"))
    }

    @Test(expected = IllegalStateException::class)
    fun exportRejectsAnotherChildPlan() {
        val child = ChildEntity("child-a", "小星", "学龄期", "SHORT_SENTENCE", "L1", createdAt = 1L, updatedAt = 1L)
        manager.buildAuthorizedExport(ConsentStatus.GRANTED, child,
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 3L,
            plans = listOf(PlanVersionEntity("plan-b", "child-b", 1, "draft", true, "{}", 1L, 1L)))
    }
}
