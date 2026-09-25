package com.xingmou.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsentEntityTest {
    @Test fun consentUsesChildAndPurposeAsStableBusinessKey() {
        val consent = ConsentEntity("consent-1", "child-1", "remote_ai", "granted", 1L, null)
        assertEquals("child-1", consent.childId)
        assertEquals("remote_ai", consent.purpose)
        assertEquals("granted", consent.status)
    }
}
