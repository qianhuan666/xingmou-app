package com.xingmou.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {
    @Test
    fun seedContainsVerifiedKnowledgeAndWhitelistedTasks() {
        val all = SeedData.knowledgeItems + SeedData.taskItems
        assertTrue(SeedData.knowledgeItems.all { it.verificationStatus == "verified" })
        assertEquals(8, SeedData.taskItems.size)
        assertTrue(all.any { it.itemId == "KB-SAFETY-001" })
        assertTrue(all.any { it.itemId == "TASK-1" })
    }
}
