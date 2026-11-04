package app.nabd.nativeapp

import app.nabd.nativeapp.data.isAdminHandle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The owner account must be recognised from every written variant. */
class AdminHandleTest {
    @Test fun acceptsEveryWrittenVariant() {
        assertTrue(isAdminHandle("f0uri"))
        assertTrue(isAdminHandle("@f0uri"))
        assertTrue(isAdminHandle("f0uri@"))
        assertTrue(isAdminHandle("  F0Uri  "))
        assertTrue(isAdminHandle("@F0URI"))
    }
    @Test fun rejectsOtherAccounts() {
        assertFalse(isAdminHandle("f0uri2"))
        assertFalse(isAdminHandle("notf0uri"))
        assertFalse(isAdminHandle(""))
        assertFalse(isAdminHandle(null))
    }
}
