package com.xingmou.core.consent

import com.xingmou.data.db.ChildEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedImportManagerTest {
    private val export = DataRightsManager().buildAuthorizedExport(
        ConsentStatus.GRANTED,
        ChildEntity("child-import", "小星", "学龄期", "SHORT_SENTENCE", "L1", createdAt = 1L, updatedAt = 1L),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 2L
    )

    @Test fun parsesAndPreviewsAuthorizedExport() {
        val manager = AuthorizedImportManager()
        val data = manager.parse(export)
        val preview = manager.preview(data)
        assertEquals("child-import", preview.childId)
        assertEquals("小星", preview.alias)
        assertEquals(0, preview.recordCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedVersion() {
        AuthorizedImportManager().parse(export.replace("\"schemaVersion\":2", "\"schemaVersion\":1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInactiveChild() {
        AuthorizedImportManager().parse(export.replace("\"status\":\"active\"", "\"status\":\"archived\""))
    }
}
