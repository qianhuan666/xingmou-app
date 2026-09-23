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
}
