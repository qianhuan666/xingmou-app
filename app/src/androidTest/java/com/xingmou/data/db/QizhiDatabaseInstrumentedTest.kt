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
}
