package com.xingmou.core.consent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentManagerTest {
    private val manager = ConsentManager()

    @Test fun remoteAiRequiresExplicitGrant() {
        assertFalse(manager.canUseRemoteAi(ConsentStatus.NOT_GRANTED))
        assertFalse(manager.canUseRemoteAi(ConsentStatus.REVOKED))
        assertTrue(manager.canUseRemoteAi(ConsentStatus.GRANTED))
    }

    @Test fun revokedTrainingConsentStopsNewTrainingAndExport() {
        assertFalse(manager.canExport(ConsentStatus.REVOKED))
        assertFalse(manager.canRunLocalTraining(ConsentStatus.REVOKED))
        assertTrue(manager.canRunLocalTraining(ConsentStatus.GRANTED))
    }
}
