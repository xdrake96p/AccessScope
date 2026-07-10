package dev.accessscope.scanner.bridge

import android.os.Process
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BridgeIdsTest {

    @Test
    fun isValidSessionId_acceptsUuid() {
        assertTrue(BridgeIds.isValidSessionId("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
    }

    @Test
    fun isValidSessionId_rejectsPathTraversal() {
        assertFalse(BridgeIds.isValidSessionId("../../../files/logs/accessscope"))
        assertFalse(BridgeIds.isValidSessionId(".."))
    }

    @Test
    fun isValidPackageName_acceptsStandardPackage() {
        assertTrue(BridgeIds.isValidPackageName("com.example.bank"))
    }

    @Test
    fun isValidPackageName_rejectsTraversal() {
        assertFalse(BridgeIds.isValidPackageName("com.example../evil"))
        assertFalse(BridgeIds.isValidPackageName(""))
    }

    @Test
    @Config(sdk = [33])
    fun isCallerAllowed_shellAndSelf() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(BridgeAccessPolicy.isCallerAllowed(context, 2000))
        assertTrue(BridgeAccessPolicy.isCallerAllowed(context, Process.myUid()))
    }

    @Test
    @Config(sdk = [33])
    fun isCallerAllowed_rejectsTypicalAppUid() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(BridgeAccessPolicy.isCallerAllowed(context, 10_123))
    }
}
