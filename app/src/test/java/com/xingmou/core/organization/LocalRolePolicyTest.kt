package com.xingmou.core.organization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRolePolicyTest {
    @Test fun adminCanManageAndAudit() {
        assertTrue(LocalRolePolicy.can("admin", LocalCapability.MANAGE_USERS))
        assertTrue(LocalRolePolicy.can("admin", LocalCapability.AUDIT_AGENT))
    }

    @Test fun parentCannotEnterProfessionalGovernance() {
        assertFalse(LocalRolePolicy.can("parent", LocalCapability.REVIEW_PLAN))
        assertTrue(LocalRolePolicy.can("parent", LocalCapability.RECORD_HOME))
    }

    @Test fun unknownRoleIsReadOnly() {
        assertTrue(LocalRolePolicy.can("unknown", LocalCapability.VIEW_CHILD))
        assertFalse(LocalRolePolicy.can("unknown", LocalCapability.DELETE_DATA))
    }
}
