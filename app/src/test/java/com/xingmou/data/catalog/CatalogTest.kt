package com.xingmou.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test fun domainCatalogContainsSixDomains() {
        assertEquals(listOf("A", "B", "C", "D", "E", "F"), DomainCatalog.all.map { it.id })
    }

    @Test fun taskCatalogContainsTwentyTwoUniqueModules() {
        assertEquals(22, TaskCatalog.all.size)
        assertEquals(22, TaskCatalog.all.map { it.id }.toSet().size)
        assertTrue(TaskCatalog.all.all { DomainCatalog.find(it.domain) != null })
    }

    @Test fun professionalCatalogsAreAvailableForSeedMigration() {
        assertEquals(15, AssessmentCatalog.all.size)
        assertEquals(12, RehabilitationMethods.all.size)
        assertTrue(RehabilitationMethods.all.any { it.id == "FAMILY" })
    }

    @Test fun v08QuestionAssetsCoverTwentyTwoModulesAndTwentyLevels() {
        assertEquals(110, QuestionCatalog.moduleQuestionBank.size)
        assertEquals(100, QuestionCatalog.fullCourseQuestions.size)
        assertEquals(22, QuestionCatalog.moduleQuestionBank.map { it.moduleId }.toSet().size)
        assertTrue(QuestionCatalog.moduleQuestionBank.all { it.version == 1 && it.sourceRef.startsWith("LOCAL_COURSE_V0.8_") })
    }
}
