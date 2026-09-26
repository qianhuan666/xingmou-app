package com.xingmou.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xingmou.core.agent.AgentEventCoordinator
import com.xingmou.core.agent.AgentEventProcessor
import com.xingmou.core.agent.RiskDetectedEvent
import com.xingmou.core.agent.RoomAgentEventStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QizhiDatabaseInstrumentedTest {
    private lateinit var database: QizhiDatabase

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, QizhiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun seedAndTrainingRecordRoundTrip() = runBlocking {
        DatabaseSeeder.seed(database)
        assertTrue(database.knowledgeDao().verifiedItems().size >= 11)

        database.trainingRecordDao().insert(
            TrainingRecordEntity(
                recordId = "instrumented-record",
                childId = "child-instrumented",
                domain = "A",
                taskId = "图片配对",
                difficulty = 1,
                supportLevel = "L1",
                reactionMs = 1200,
                errorType = null,
                firstCorrect = true,
                correct = true,
                promptLevel = 1,
                createdAt = 1L
            )
        )
        assertEquals(1, database.trainingRecordDao().countForChild("child-instrumented"))
        assertEquals("图片配对", database.trainingRecordDao().recentForChild("child-instrumented").single().taskId)
    }

    @Test
    fun agentEventIsStoredWithSafetyFlagTransaction() = runBlocking {
        val coordinator = AgentEventCoordinator(AgentEventProcessor(), RoomAgentEventStore(database))
        val decision = coordinator.handle(
            RiskDetectedEvent(
                eventId = "instrumented-risk-event",
                runId = "instrumented-run",
                childId = "child-instrumented",
                occurredAt = 10L,
                text = "孩子呼吸困难"
            ),
            processedAt = 11L
        )

        assertEquals("processed", decision.event.status)
        assertEquals(1, database.agentDao().events("instrumented-run").size)
        assertEquals("SAFETY_STOP", database.safetyFlagDao().observeActive("child-instrumented").first().single().level)
    }

    @Test
    fun homeTaskAndFeedbackAreIsolatedByChild() = runBlocking {
        database.homeTaskDao().upsert(
            HomeTaskEntity(
                taskId = "home-child-a", childId = "child-a", title = "陪练",
                description = "五分钟", status = "pending", updatedAt = 1L
            )
        )
        database.homeFeedbackDao().insert(
            HomeFeedbackEntity(
                feedbackId = "feedback-a", childId = "child-a", taskId = "home-child-a",
                mood = "平稳", fatigue = "较少", note = "完成一次", createdAt = 2L
            )
        )
        assertEquals("child-a", database.homeTaskDao().latestForChild("child-a")?.childId)
        assertEquals(1, database.homeFeedbackDao().recentForChild("child-a").size)
        assertEquals(0, database.homeFeedbackDao().recentForChild("child-b").size)
    }

    @Test
    fun homeTaskKeepsPlanMappingAndFeedbackTimeline() = runBlocking {
        database.homeTaskDao().upsert(
            HomeTaskEntity(
                taskId = "home-plan-v2", childId = "child-a", title = "专业下发：图片配对短练习",
                description = "按方案执行", status = "pending", planId = "plan-v2", planVersion = 2,
                frequency = "每日 1–2 次", durationMinutes = 8, supportLevel = "L2",
                stopConditions = "出现疲劳时暂停", source = "PLAN_V2", demoStep = 2, updatedAt = 3L
            )
        )
        database.homeFeedbackDao().insert(
            HomeFeedbackEntity("feedback-v2", "child-a", "home-plan-v2", "平稳", "较少", "愿意参与", 4L)
        )
        val task = database.homeTaskDao().latestForChild("child-a")
        val feedback = database.homeFeedbackDao().recentForChild("child-a").single()
        assertEquals("plan-v2", task?.planId)
        assertEquals(8, task?.durationMinutes)
        assertEquals("PLAN_V2", task?.source)
        assertEquals(2, task?.demoStep)
        assertEquals("home-plan-v2", feedback.taskId)
    }

    @Test
    fun assessmentRecordsKeepVersionChainPerChild() = runBlocking {
        database.assessmentRecordDao().upsert(
            AssessmentRecordEntity("assessment-1", "child-a", "GESELL", "Gesell", 1, "初评", "2026-09-25", "专业人员转录", "{\"A\":3}", "首次记录", "transcribed", 1L, 1L)
        )
        database.assessmentRecordDao().upsert(
            AssessmentRecordEntity("assessment-2", "child-a", "GESELL", "Gesell", 2, "复评", "2026-10-25", "专业人员转录", "{\"A\":4}", "复评记录", "transcribed", 2L, 2L)
        )
        assertEquals(2, database.assessmentRecordDao().latestVersion("child-a", "GESELL"))
        assertEquals(2, database.assessmentRecordDao().recentForChild("child-a").size)
        assertEquals(0, database.assessmentRecordDao().recentForChild("child-b").size)
    }

    @Test
    fun abilityProfileKeepsAssessmentEvidenceReferences() = runBlocking {
        database.abilityProfileDao().upsert(
            AbilityProfileEntity("profile-1", "child-a", "assessment_linked", "{\"A\":2}", 0.8, "[]", "[\"assessment-1\"]", 1L)
        )
        val profile = database.abilityProfileDao().latestForChild("child-a")
        assertEquals("assessment_linked", profile?.status)
        assertTrue(profile?.assessmentRecordIdsJson?.contains("assessment-1") == true)
    }

    @Test
    fun careWorkflowKeepsStageHistoryPerChild() = runBlocking {
        database.careRecordDao().insert(
            CareRecordEntity("care-1", "child-a", "intake", "接案", "active", "已建立档案", null, null, "professional", 1L, 1L, 1L, "professional", "首次接案已核对")
        )
        database.careRecordDao().insert(
            CareRecordEntity("care-2", "child-a", "goals", "目标", "active", "目标已确认", null, null, "professional", 2L, 2L)
        )
        assertEquals("goals", database.careRecordDao().latestForChild("child-a")?.stage)
        assertEquals("professional", database.careRecordDao().recentForChild("child-a").last().professionalSignature)
        assertEquals(2, database.careRecordDao().recentForChild("child-a").size)
        assertEquals(0, database.careRecordDao().recentForChild("child-b").size)
    }

    @Test
    fun careWorkflowPersistsClosureFollowUpFieldsAndAuditEvent() = runBlocking {
        database.careRecordDao().insert(
            CareRecordEntity(
                recordId = "care-closure",
                childId = "child-a",
                stage = "closure",
                stageLabel = "结案",
                status = "active",
                summary = "结案已签署",
                professionalId = "professional",
                createdAt = 3L,
                updatedAt = 3L,
                professionalSignedAt = 3L,
                professionalSignature = "professional",
                note = "阶段记录完整",
                closureReason = "目标已达到预设的过程指标"
            )
        )
        database.agentDao().upsertEvent(
            AgentEventEntity(
                eventId = "audit-care-1",
                runId = "care-care-closure",
                childId = "child-a",
                eventType = "CARE_STAGE_SIGNED",
                payloadSummary = "stage=closure;professional=professional;closurePresent=true",
                status = "processed",
                createdAt = 3L,
                processedAt = 3L
            )
        )
        val record = database.careRecordDao().latestForChild("child-a")
        val events = database.agentDao().events("care-care-closure")
        assertEquals("目标已达到预设的过程指标", record?.closureReason)
        assertEquals("CARE_STAGE_SIGNED", events.single().eventType)
    }

    @Test
    fun dataRightsPurgeIsChildScopedAndKeepsRequestReport() = runBlocking {
        val now = 20L
        database.childDao().upsert(
            ChildEntity("child-a", "小甲", "学龄期", "SHORT_SENTENCE", "L1", createdAt = now, updatedAt = now)
        )
        database.childDao().upsert(
            ChildEntity("child-b", "小乙", "学龄期", "SHORT_SENTENCE", "L1", createdAt = now, updatedAt = now)
        )
        database.trainingRecordDao().insert(
            TrainingRecordEntity("record-a", "child-a", "A", "图片配对", 1, "L1", 1000L, null, true, true, 1, now)
        )
        database.trainingRecordDao().insert(
            TrainingRecordEntity("record-b", "child-b", "A", "图片配对", 1, "L1", 1000L, null, true, true, 1, now)
        )
        database.dataRightsDao().upsertRequest(
            DataRequestEntity("delete-a", "child-a", "DELETE", "requested", now, requesterUserId = "local-professional")
        )

        val deleted = database.dataRightsDao().purgeChildData("child-a")
        database.dataRightsDao().completeRequest("delete-a", "completed", now + 1, "{\"deleted\":$deleted}")

        assertTrue(deleted >= 2)
        assertEquals(null, database.childDao().findById("child-a"))
        assertEquals(0, database.trainingRecordDao().countForChild("child-a"))
        assertEquals("child-b", database.childDao().findById("child-b")?.childId)
        assertEquals(1, database.trainingRecordDao().countForChild("child-b"))
        assertEquals("completed", database.dataRightsDao().findRequest("delete-a")?.status)
    }
}
