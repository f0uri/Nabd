package app.nabd.nativeapp

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.nabd.nativeapp.data.ThemeStore
import app.nabd.nativeapp.data.passwordHash
import app.nabd.nativeapp.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: NabdViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        // Fixed, non-secret native known-answer proof. Never reads live accounts.
        lifecycleScope.launch(Dispatchers.IO) {
            val passed = runCatching { passwordHash("Nabd\u0000عربية 🌍", "000102030405060708090a0b0c0d0e0f") == "4540121fed790917487ae1ad6b7b4aae789939d49cb522529bfffd44e37296b6" }.getOrDefault(false)
            File(cacheDir, "nabd-kotlin-proof.json").writeText(JSONObject().put("nativeKotlin", true).put("cryptoSelfTest", passed).put("versionCode", BuildConfig.VERSION_CODE).toString())
        }
        val invite = intent?.data?.takeIf { it.scheme == "nabd" && it.host == "invite" }?.lastPathSegment
        setContent {
            val state by vm.ui.collectAsStateWithLifecycle()
            val updates by vm.updates.collectAsStateWithLifecycle()
            val accounts by vm.accounts.collectAsStateWithLifecycle()
            val store = remember { ThemeStore(applicationContext) }
            // The saved appearance lives with the account on the server; the local
            // mirror only paints the screens shown before that account is loaded.
            val chosen = if (state.signedIn) state.snapshot?.settings?.theme ?: store.value ?: "system" else store.value ?: "system"
            val dark = chosen == "dark" || (chosen != "light" && isSystemInDarkTheme())
            SideEffect { if (state.signedIn) store.value = chosen; NabdThemeState.dark = dark }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
            }
            NabdTheme {
                // Blur only the background layer on Android 12+, never dialog text.
                // Older Android uses a solid translucent panel, without claiming blur.
                val backdrop = if (updates.visible && Build.VERSION.SDK_INT >= 31) Modifier.blur(12.dp) else Modifier
                Box(Modifier.fillMaxSize().background(Canvas)) {
                    Box(Modifier.fillMaxSize().then(backdrop)) {
                        if (state.starting) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentLight) }
                        else if (!state.signedIn) AuthScreen(state, vm::login, { name, email, password, code -> vm.register(name, email, password, code) }, invite, accounts, vm::switchAccount)
                        else HomeScreen(state, vm)
                    }
                    UpdateDialog(updates, vm::hideUpdate, vm::downloadUpdate, { vm.installUpdate { intent -> startActivity(intent) } }, { vm.checkUpdates() })
                }
            }
        }
    }
}
