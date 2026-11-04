package app.nabd.nativeapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
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
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "native-update-ui.png")
        output.outputStream().use { compose.onNodeWithTag("native-update-dialog").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithTag("update-install").performClick()
        assertEquals(1, installs)
    }
}
