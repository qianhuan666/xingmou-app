package com.xingmou.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChildDataModelTest {
    @Test fun defaultChildIsExplicitlySeededAndNotViewModelHardcoded() {
        assertEquals("child-seed", SeedData.defaultChild.childId)
        assertEquals("local-professional", SeedData.defaultBinding.userId)
        assertEquals("professional", SeedData.defaultBinding.role)
        assertNull(SeedData.defaultChild.diagnosisTranscription)
    }
}
