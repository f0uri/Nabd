package app.nabd.nativeapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.nabd.nativeapp.data.*
import app.nabd.nativeapp.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Actual native widgets, synthetic explicitly-labelled account. No VM/network. */
class FeedVisualTest {
    @get:Rule val compose = createComposeRule()
    private fun photo(): String {
        val bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.drawColor(Color.rgb(40,73,64))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=56f; textAlign=Paint.Align.CENTER }
        canvas.drawText("ن",48f,66f,paint)
        val bytes=ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes); bitmap.recycle()
        return "data:image/png;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)
    }
    private fun render(scale: Float) {
        val person=Person("a".repeat(32),"حساب اختبار","visual_test","",photo(),false)
        val post=FeedPost("b".repeat(32),person,"هنا نشارك لحظاتنا، ونبقى قريبين من الناس اللي كيعنيو لينا.",isoDate(System.currentTimeMillis()-7200000),null,1f,"love",3,emptyList())
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                NabdTheme {
                    Scaffold(containerColor=app.nabd.nativeapp.ui.Canvas,
                        topBar={ HomeHeader(false,null,false,"",{},{},{},{},{}) },
                        bottomBar={ HomeDock("home") {} }) { padding ->
                        FeedContent(Snapshot(person,listOf(post),listOf(person),emptyList()),emptyList(),false,"all","",Modifier.padding(padding),{},{},{},{},{},{},{},{},{ _,_ -> },{})
                    }
                }
            }
        }
        compose.waitUntil(10000) { compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"image:loaded")).fetchSemanticsNodes().size>=2 }
    }
    private fun capture(name: String) {
        val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        val saved=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /data/local/tmp/$name")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(saved).use {
            val signature=ByteArray(8); java.io.DataInputStream(it).readFully(signature)
            assertArrayEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10),signature)
        }
    }
    @Test fun realFeedComponentsRenderInlineAvatarsAndCaptureCompactLayout() {
        render(1f)
        compose.onNodeWithTag("post-${"b".repeat(32)}").assertExists()
        compose.onNodeWithTag("home-dock").assertIsDisplayed()
        compose.onNodeWithText("تحديث المنشورات").assertDoesNotExist() // icon, not a loose action row
        compose.onNodeWithText("التفاعلات").assertDoesNotExist() // retained in contextual menu
        capture("native-feed-ui.png")
    }
    @Test fun largerSystemTextKeepsFeedScrollableAndDockUsable() {
        render(1.3f)
        compose.onNodeWithTag("native-feed").performScrollToNode(hasTestTag("post-${"b".repeat(32)}"))
        compose.onNodeWithTag("home-dock").assertIsDisplayed()
        compose.onNodeWithText("مشاركة",substring=false).assertExists()
        capture("native-feed-large-text.png")
    }
    @Test fun invalidInlinePhotoKeepsVisibleFallbackInsteadOfBlankCircle() {
        compose.setContent { NabdTheme { NabdImage("data:image/jpeg;base64,YQ==","test image",Modifier.size(48.dp).testTag("broken-image")) } }
        compose.onNodeWithTag("broken-image").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"image:fallback"))
    }
}
