package app.nabd.nativeapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.ReelLibrary
import app.nabd.nativeapp.data.ReelPreview
import app.nabd.nativeapp.data.ReelRef
import app.nabd.nativeapp.data.reelEmbedFallbackUrl
import app.nabd.nativeapp.data.reelEmbedUrl
import app.nabd.nativeapp.data.reelPageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A reel card never covers more than 72% of the screen, so the bubble stays readable. */
@Composable internal fun reelCardWidth(): Dp = (LocalConfiguration.current.screenWidthDp * 0.72f).dp.coerceAtMost(260.dp)

/** Instagram camera drawn as a vector: rounded frame, lens and the single dot. */
@Composable internal fun InstagramGlyph(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 2.1f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val left = 2f * unit; val top = 2f * unit; val right = 22f * unit; val bottom = 22f * unit; val corner = 6f * unit
        val frame = Path().apply {
            moveTo(left + corner, top)
            lineTo(right - corner, top)
            quadraticBezierTo(right, top, right, top + corner)
            lineTo(right, bottom - corner)
            quadraticBezierTo(right, bottom, right - corner, bottom)
            lineTo(left + corner, bottom)
            quadraticBezierTo(left, bottom, left, bottom - corner)
            lineTo(left, top + corner)
            quadraticBezierTo(left, top, left + corner, top)
            close()
        }
        drawPath(frame, tint, style = stroke)
        drawCircle(color = tint, radius = 4.7f * unit, center = center, style = stroke)
        drawCircle(color = tint, radius = 1.45f * unit, center = Offset(17.3f * unit, 6.7f * unit))
    }
}

/** Solid play triangle drawn as a vector path. */
@Composable internal fun PlayGlyph(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.22f)
            lineTo(size.width * 0.78f, size.height * 0.5f)
            lineTo(size.width * 0.35f, size.height * 0.78f)
            close()
        }
        drawPath(path, tint)
    }
}

/**
 * Glass reel card: 20dp corners, a soft shadow and, once pressed, the official
 * Instagram embed playing in the very same place. Nothing is downloaded by us.
 */
@Composable internal fun ReelCard(ref: ReelRef, modifier: Modifier = Modifier, playing: Boolean = false,
    onPlay: () -> Unit, onStop: () -> Unit, onExpand: () -> Unit) {
    val preview by produceState<ReelPreview?>(null, ref.id) {
        value = withContext(Dispatchers.IO) { ReelLibrary.previews.forId(ref.id) }
    }
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.nabdShadow(shape, elevation = 10.dp).clip(shape).background(CardFill).border(1.dp, GlassEdge, shape)) {
        Box(Modifier.matchParentSize().glassBlur(18.dp).background(GlassSheen))
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(Raised)) {
            if (playing) {
                ReelPlayer(ref, Modifier.matchParentSize())
            } else {
                if (preview?.thumbnail != null) NabdImage(preview!!.thumbnail, "غلاف الريلز", Modifier.matchParentSize(), ContentScale.Crop)
                else Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFF0866FF), Color(0xFF00A3FF)))), contentAlignment = Alignment.Center) {
                    InstagramGlyph(Modifier.size(38.dp), Color.White.copy(alpha = .5f))
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(74.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD90A1628)))))
                Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InstagramGlyph(Modifier.size(16.dp), Color.White)
                    Text(preview?.author ?: "إنستغرام", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                // Round gradient play button with the vector triangle.
                Box(Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape)
                    .shadow(10.dp, CircleShape, spotColor = BlueShadow, ambientColor = BlueShadow)
                    .background(NabdBlue).pressClick(onClick = onPlay).testTag("reel-play-${ref.id}"), contentAlignment = Alignment.Center) {
                    PlayGlyph(Modifier.size(22.dp), Color.White)
                }
            }
            if (playing) {
                Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(30.dp).clip(CircleShape).background(Color(0x990A1628))
                    .pressClick(onClick = onStop).testTag("reel-close-${ref.id}"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "إيقاف الريلز", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape).background(Color(0x990A1628))
                    .pressClick(onClick = onExpand).testTag("reel-expand-${ref.id}"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Fullscreen, "تكبير الريلز", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Starts the embed's own video with sound. Instagram builds the player after the
 * page has loaded, so the script keeps looking for a few seconds, clicks the real
 * play control and only then gives up. Nothing is ever simulated or invented.
 */
private const val REEL_PLAY_JS = """
(function(){
  var tries = 0;
  function root() {
    try {
      var f = document.querySelector('iframe');
      if (f && f.contentDocument && f.contentDocument.querySelector('video')) return f.contentDocument;
    } catch (e) {}
    return document;
  }
  function clickables(d) {
    var list = d.querySelectorAll('button, [role="button"], a[role="button"], video');
    for (var i = 0; i < list.length; i++) {
      var n = list[i];
      var label = '';
      try { label = (n.getAttribute('aria-label') || '') + ' ' + (n.className || '') + ' ' + (n.id || ''); } catch (e) {}
      if (/play|تشغيل|مشاهدة|video/i.test(label)) { try { n.click(); } catch (e) {} }
    }
  }
  try { if (window.__nabdReel) { clearInterval(window.__nabdReel); } } catch (e) {}
  window.__nabdReel = setInterval(function() {
    tries++;
    var d = root();
    var v = d.querySelector('video');
    if (v) {
      try { v.muted = false; v.playsInline = true; v.volume = 1; } catch (e) {}
      try { var p = v.play(); if (p && p.catch) { p.catch(function(){ try { v.muted = true; var q = v.play(); if (q && q.catch) q.catch(function(){}); } catch (e) {} }); } } catch (e) {}
    }
    clickables(d);
    if (tries >= 30) { try { clearInterval(window.__nabdReel); window.__nabdReel = 0; } catch (e) {} }
  }, 250);
})()
"""
/** Pauses the video and stops the retry loop, so a hidden player costs nothing. */
private const val REEL_PAUSE_JS = """
(function(){
  try { if (window.__nabdReel) { clearInterval(window.__nabdReel); window.__nabdReel = 0; } } catch (e) {}
  var d = document;
  try { var f = document.querySelector('iframe'); if (f && f.contentDocument && f.contentDocument.querySelector('video')) d = f.contentDocument; } catch (e) {}
  var v = d.querySelector('video'); if (v) { try { v.pause(); } catch (e) {} }
})()
"""

/**
 * The embed page of Instagram only. A navigation that would leave Instagram, or
 * leave the embed itself and wander into the full site, is dropped instead of
 * being handed to a browser: the user never leaves نبض.
 */
private class ReelClient(private val fallback: String) : WebViewClient() {
    private var triedFallback = false

    private fun inside(request: WebResourceRequest): Boolean {
        if (request.url.scheme != "https") return false
        val host = request.url.host.orEmpty()
        return host == "instagram.com" || host.endsWith(".instagram.com") ||
            host.endsWith(".cdninstagram.com") || host.endsWith(".fbcdn.net")
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !inside(request)

    override fun onPageFinished(view: WebView, url: String) {
        if (url.startsWith("https://")) view.evaluateJavascript(REEL_PLAY_JS, null)
    }

    private fun retry(view: WebView) {
        if (triedFallback) return
        triedFallback = true
        view.post { runCatching { view.loadUrl(fallback) } }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) retry(view)
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) retry(view)
    }
}

/**
 * One WebView per reel, owned by the composition: it pauses with the screen and
 * is destroyed as soon as the card scrolls away or the chat is left, so nothing
 * keeps streaming in the background.
 */
@SuppressLint("SetJavaScriptEnabled")
private class ReelWebView(context: Context, ref: ReelRef, private val onProgress: (Int) -> Unit) {
    private val primary = reelEmbedUrl(ref.id)
    val view = WebView(context).apply web@{
        // Instagram's embed needs its own cookies: without them the player stops
        // loading silently instead of showing anything.
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@web, true)
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        setBackgroundColor(AndroidColor.parseColor("#05070D"))
        webViewClient = ReelClient(reelEmbedFallbackUrl(ref.id))
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(target: WebView, value: Int) { onProgress(value) }
        }
        loadUrl(primary)
    }

    fun pause() { runCatching { view.onPause(); view.evaluateJavascript(REEL_PAUSE_JS, null) } }

    fun resume() { runCatching { view.onResume(); view.evaluateJavascript(REEL_PLAY_JS, null) } }

    fun stop() { runCatching { view.stopLoading(); view.evaluateJavascript(REEL_PAUSE_JS, null); view.loadUrl("about:blank"); view.onPause(); view.destroy() } }
}

/**
 * The official Instagram embed, inline. The video is streamed by Instagram from
 * Instagram: the author name, the sound and the view stay on the original account.
 */
/** Last resort only: the public page of the reel, in the device browser. */
private fun openReelExternally(context: Context, ref: ReelRef) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reelPageUrl(ref.id))).addCategory(Intent.CATEGORY_BROWSABLE)) }
}

@Composable internal fun ReelPlayer(ref: ReelRef, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var progress by remember(ref.id) { mutableIntStateOf(0) }
    var attempt by remember(ref.id) { mutableIntStateOf(0) }
    var stalled by remember(ref.id, attempt) { mutableStateOf(false) }
    val player = remember(ref.id, attempt) { runCatching { ReelWebView(context, ref) { progress = it } }.getOrNull() }
    // Instagram sometimes stops loading in silence (consent page, protected audio).
    // After eight seconds the card says so instead of staying black for ever.
    LaunchedEffect(ref.id, attempt) {
        stalled = false
        delay(8000)
        if (progress < 60) stalled = true
    }
    DisposableEffect(player, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player?.pause()
                Lifecycle.Event.ON_RESUME -> player?.resume()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player?.stop() }
    }
    Box(modifier.background(Color(0xFF05070D))) {
        if (player != null) AndroidView(factory = { player.view }, modifier = Modifier.matchParentSize())
        if (player == null) Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Text("ما قدرناش نعرض هاد الريلز.", color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        } else if (stalled && progress < 60) Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayGlyph(Modifier.size(26.dp), Color(0xB3FFFFFF))
                Text("تعذر تشغيل هذا الريلز", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                PillButton("إعادة المحاولة", { attempt++; progress = 0; stalled = false }, height = 40.dp, fontSize = 13,
                    icon = Icons.Outlined.Refresh, testTag = "reel-retry-${ref.id}")
                PillButton("فتح في إنستغرام", { openReelExternally(context, ref) }, height = 40.dp, fontSize = 13,
                    brush = null, icon = Icons.Outlined.OpenInNew, testTag = "reel-external-${ref.id}")
            }
        } else if (progress < 45) Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Text("جارٍ تحميل الريلز…", color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

/** Full-screen reel inside the app: a dark stage, the same embed and one close button. */
@Composable internal fun ReelViewer(ref: ReelRef, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF05070D)).testTag("reel-fullscreen"), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxHeight(0.9f).aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
                ReelPlayer(ref, Modifier.matchParentSize())
            }
            Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(42.dp).clip(CircleShape).background(Color(0x59FFFFFF))
                .pressClick(onClick = onClose).testTag("reel-fullscreen-close"), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "إغلاق الريلز", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Glass friend picker used to forward a reel as a real message, inside the app. */
@Composable internal fun ReelSendSheet(friends: List<Person>, busy: Boolean, close: () -> Unit, send: (Person) -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = remember(friends, query) { friends.filter { query.isBlank() || it.name.contains(query, true) || it.username.contains(query, true) } }
    GlassWindow(close = { if (!busy) close() }, tag = "reel-send") {
        Text("إرسال الريلز كرسالة", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
        NabdField(query, { query = it }, "قلب على صديق…", Modifier.fillMaxWidth(), enabled = !busy)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { person ->
                val press = rememberPress()
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Raised)
                    .pressClick(press = press, enabled = !busy, onClick = { send(person) })
                    .testTag("reel-send-${person.id}").padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(person, 44)
                    Column(Modifier.weight(1f)) {
                        PersonName(person, size = 15)
                        Text("@${person.username}", color = Muted, fontSize = 11.5.sp, maxLines = 1)
                    }
                    Icon(Icons.AutoMirrored.Outlined.Send, null, tint = Accent, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (list.isEmpty()) Text("ما لقينا تا صديق بهاد الاسم.", color = Muted, fontSize = 13.sp)
        PillButton("إغلاق", { close() }, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
    }
}
