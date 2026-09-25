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
}
