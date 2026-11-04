package app.nabd.nativeapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.nabd.nativeapp.ui.AuthScreen
import app.nabd.nativeapp.ui.NabdTheme
import app.nabd.nativeapp.ui.UpdateDialog
import app.nabd.nativeapp.updates.ApkRelease
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class NativeUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nativeAuthValidatesAndCallsRealCallbackWithoutRedirectOrMascot() {
        var submits = 0
        compose.setContent { NabdTheme { AuthScreen(AppState(starting = false), { _, _ -> submits++ }, { _, _, _ -> submits++ }) } }
        compose.onNodeWithTag("native-auth").assertExists()
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
        assertEquals(0, submits)
        compose.onNodeWithTag("identity").performScrollTo().performTextInput("test_user")
        compose.onNodeWithTag("password").performScrollTo().performTextInput("sample-password-only")
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
        assertEquals(1, submits)
    }
    @Test fun updateProgressAndInstallEnablementFollowActualState() {
        val release = ApkRelease(5, "2.0.1", listOf("تحسين سلاسة الواجهة", "تحديث آمن مع الحفاظ على البيانات"), "https://github.com/f0uri/Nabd/releases/download/test/nabd.apk", "a".repeat(64), 1000000)
        val state = mutableStateOf(UpdateState(release = release, visible = true, phase = "downloading", received = 240000, total = 1000000))
        var installs = 0
        compose.setContent { NabdTheme { UpdateDialog(state.value, {}, {}, { installs++ }, {}) } }
        compose.onNodeWithTag("update-install").assertIsNotEnabled()
        compose.onNodeWithTag("update-progress").assertExists()
        compose.runOnIdle { state.value = state.value.copy(phase = "ready", file = File("synthetic-fixture-only")) }
        compose.onNodeWithTag("update-install").assertIsEnabled()
        compose.onNodeWithTag("native-update-dialog").assertIsDisplayed()
        // AGP may uninstall the instrumented application after tests, removing
        // its external files. Shell-owned /data/local/tmp survives that cleanup.
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /data/local/tmp/nabd-native-update-ui.png && test -s /data/local/tmp/nabd-native-update-ui.png && echo captured"
        )
        android.os.ParcelFileDescriptor.AutoCloseInputStream(screenshot).use {
            assertEquals("captured", it.readBytes().toString(Charsets.UTF_8).trim())
        }
        compose.onNodeWithTag("update-install").performClick()
        assertEquals(1, installs)
    }
}
