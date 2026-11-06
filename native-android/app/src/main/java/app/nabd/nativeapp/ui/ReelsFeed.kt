package app.nabd.nativeapp.ui

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.PixabayVideo
import app.nabd.nativeapp.data.REEL_SOURCE_PIXABAY
import app.nabd.nativeapp.data.ReelComment
import app.nabd.nativeapp.data.ReelItem
import app.nabd.nativeapp.data.ReelLibrary
import app.nabd.nativeapp.data.ReelPreview
import app.nabd.nativeapp.data.findReel
import app.nabd.nativeapp.data.pixabayReelKey
import android.content.Intent

private val REEL_STAGE = Color(0xFF05070D)

/** One page of the tab: a reel published inside نبض, or a clip from Pixabay. */
private data class ReelPage(val item: ReelItem, val clip: PixabayVideo? = null)

/**
 * The reels tab: one clip per screen, snapped vertically, playing only the one
 * that is in front of the eyes. Every number on it is a real نبض number — a clip
 * nobody pulsed shows zero, never a borrowed figure.
 */
@Composable internal fun ReelsScreen(
    me: Person, reels: List<ReelItem>, clips: List<PixabayVideo>, pulses: Map<String, Int>,
    comments: Map<String, List<ReelComment>>, myPulses: Map<String, String>, busy: Boolean, loading: Boolean,
    modifier: Modifier = Modifier,
    onPulse: (ReelItem) -> Unit, onComment: (ReelItem, String) -> Unit, onShare: (ReelItem) -> Unit,
    onFollow: (Person) -> Unit, onProfile: (Person) -> Unit, onPublish: () -> Unit, onRefresh: () -> Unit
) {
    val pages = remember(reels, clips, pulses, comments, myPulses) {
        val discovery = clips.map { clip ->
            val key = pixabayReelKey(clip.id)
            ReelPage(ReelItem(key, REEL_SOURCE_PIXABAY, clip.page, clip.video, "", clip.author.ifBlank { "Pixabay" }, null, "",
                "", pulses[key] ?: 0, myPulses[key], comments[key].orEmpty()), clip)
        }
        reels.map { ReelPage(it) } + discovery
    }
    var active by remember { mutableStateOf(true) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watch = LifecycleEventObserver { _, event ->
            active = when (event) { Lifecycle.Event.ON_RESUME -> true; Lifecycle.Event.ON_PAUSE -> false; else -> active }
        }
        owner.lifecycle.addObserver(watch)
        onDispose { owner.lifecycle.removeObserver(watch) }
    }
    Box(modifier.fillMaxSize().background(REEL_STAGE).testTag("reels-screen")) {
        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) ShimmerBlock(Modifier.fillMaxSize(), 0)
                else EmptyState(Icons.Outlined.LocalFireDepartment, "ما كايناش ريلزات دابا",
                    "الريلزات اللي كينشروها الناس غيبانو هنا، ومعاهم مقاطع عمودية قصيرة من Pixabay.",
                    modifier = Modifier.padding(horizontal = 20.dp))
            }
        } else {
            val pager = rememberPagerState { pages.size }
            VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { pages[it].item.key }) { index ->
                ReelStage(page = pages[index], me = me, playing = active && index == pager.currentPage, busy = busy,
                    onPulse = onPulse, onComment = onComment, onShare = onShare, onFollow = onFollow, onProfile = onProfile)
            }
        }
        Row(Modifier.padding(top = 34.dp, start = 14.dp, end = 14.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("ريلز جديد", onPublish, brush = null, height = 38.dp, fontSize = 12, icon = Icons.Outlined.Add)
            GlassCircle(Icons.Outlined.Refresh, "تحديث الريلزات", onRefresh, enabled = !busy, size = 38.dp)
        }
    }
}

/**
 * One clip, streamed straight from Pixabay with the framework player: no copy
 * is ever made, the sound stays off until the finger asks for it, and the clip
 * loops for as long as it is in front of the eyes.
 */
@Composable private fun ClipPlayer(clip: PixabayVideo, playing: Boolean, muted: Boolean, modifier: Modifier = Modifier) {
    var failed by remember(clip.id) { mutableStateOf(false) }
    var ready by remember(clip.id) { mutableStateOf(false) }
    var attempt by remember(clip.id) { mutableIntStateOf(0) }
    var player by remember(clip.id) { mutableStateOf<MediaPlayer?>(null) }
    var view by remember(clip.id) { mutableStateOf<VideoView?>(null) }
    LaunchedEffect(muted, player) { val level = if (muted) 0f else 1f; player?.setVolume(level, level) }
    LaunchedEffect(playing, ready, view, failed) {
        val target = view ?: return@LaunchedEffect
        if (failed) return@LaunchedEffect
        if (playing && ready) target.start() else target.pause()
    }
    Box(modifier.fillMaxSize().background(REEL_STAGE)) {
        key(attempt) {
            AndroidView(factory = { context ->
                VideoView(context).apply {
                    setVideoURI(Uri.parse(clip.video))
                    setOnPreparedListener { media ->
                        media.isLooping = true
                        val level = if (muted) 0f else 1f
                        media.setVolume(level, level)
                        player = media
                        ready = true
                    }
                    setOnErrorListener { _, _, _ -> failed = true; true }
                }
            }, modifier = Modifier.fillMaxSize(), update = { created -> view = created })
        }
        DisposableEffect(clip.id, attempt) { onDispose { view?.stopPlayback() } }
        if (!ready && !failed) ShimmerBlock(Modifier.matchParentSize(), 0)
        if (failed) Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تعذر تشغيل هذا المقطع", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                PillButton("إعادة المحاولة", { failed = false; ready = false; attempt++ }, brush = null, height = 40.dp, fontSize = 13, icon = Icons.Outlined.Refresh)
            }
        }
    }
}

@Composable private fun ReelStage(page: ReelPage, me: Person, playing: Boolean, busy: Boolean,
    onPulse: (ReelItem) -> Unit, onComment: (ReelItem, String) -> Unit, onShare: (ReelItem) -> Unit,
    onFollow: (Person) -> Unit, onProfile: (Person) -> Unit) {
    val item = page.item
    var muted by remember(item.key) { mutableStateOf(true) }
    var commentsOpen by remember(item.key) { mutableStateOf(false) }
    var sharing by remember(item.key) { mutableStateOf(false) }
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(REEL_STAGE)) {
        when {
            page.clip != null -> ClipPlayer(page.clip, playing, muted)
            item.video.startsWith("https://") -> ClipPlayer(PixabayVideo(item.key.substringAfter(':'), item.ref, item.video, 0, item.authorName, ""), playing, muted)
            findReel(item.ref) != null -> findReel(item.ref)?.let { ref -> ReelPlayer(ref, Modifier.fillMaxSize()) }
            else -> ReelPoster(item)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color.Transparent, Color(0x99000000)))))
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RailAction(Icons.Outlined.LocalFireDepartment, item.pulses, "نبضة", item.pulse != null) { onPulse(item) }
            RailAction(Icons.Outlined.ModeComment, item.comments.size, "تعليق", false) { commentsOpen = true }
            RailAction(Icons.AutoMirrored.Outlined.Send, 0, "مشاركة", false) { sharing = true }
            GlassCircle(if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, "الصوت", { muted = !muted }, size = 40.dp, tint = Color.White)
        }
        ReelCaption(item, me, busy, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 86.dp, bottom = 108.dp), onFollow, onProfile) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.ref)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        }
    }
    if (commentsOpen) ReelCommentsSheet(item, me, busy, close = { commentsOpen = false }, send = { onComment(item, it) })
    if (sharing) ReelShareSheet(item, close = { sharing = false }, open = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.ref)).addCategory(Intent.CATEGORY_BROWSABLE)) } })
}

/** One action of the side rail: a glass circle and the real count beneath it. */
@Composable private fun RailAction(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, label: String,
    active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        GlassCircle(icon, label, onClick, size = 44.dp, tint = if (active) Color(0xFFFF4D6D) else Color.White, filled = false)
        Text(if (count > 0) "$count" else "", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
    }
}

/** The lower glass card: who published the reel, what it says, and how to follow. */
@Composable private fun ReelCaption(item: ReelItem, me: Person, busy: Boolean, modifier: Modifier = Modifier,
    onFollow: (Person) -> Unit, onProfile: (Person) -> Unit, onOpen: () -> Unit) {
    val author = item.author
    Box(modifier) {
        GlassCard(modifier = Modifier, radius = 22.dp) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (author != null) Box(Modifier.pressClick { onProfile(author) }, contentAlignment = Alignment.Center) { Avatar(author, 40) }
                    else Box(Modifier.size(40.dp).clip(CircleShape).background(Accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                        Text(item.authorName.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "P", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.authorName, color = TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (item.source == REEL_SOURCE_PIXABAY) "مقطع من Pixabay" else relativePostDate(item.date),
                            color = Muted, fontSize = 11.sp, maxLines = 1)
                    }
                    if (author != null && author.id != me.id) FollowPill(author.following, busy, Modifier) { onFollow(author) }
                    else if (item.source == REEL_SOURCE_PIXABAY) PillButton("المصدر", onOpen, brush = null, height = 34.dp, fontSize = 11)
                }
                if (item.caption.isNotBlank()) Text(item.caption, color = TextMain, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A published reel whose video lives on another service: the cover and one open button. */
@Composable private fun ReelPoster(item: ReelItem) {
    Box(Modifier.fillMaxSize().background(REEL_STAGE), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.cover.startsWith("https://")) NabdImage(item.cover, "غلاف الريلز", Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(22.dp)))
            else Box(Modifier.size(76.dp).clip(CircleShape).background(Accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                PlayGlyph(Modifier.size(30.dp), Accent)
            }
            Text(item.authorName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

/** Comments of one reel, in the same glass sheet the posts use. */
@Composable private fun ReelCommentsSheet(item: ReelItem, me: Person, busy: Boolean, close: () -> Unit, send: (String) -> Unit) {
    var text by remember(item.key) { mutableStateOf("") }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(WindowDim), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(CardFill).border(1.dp, GlassEdge, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("التعليقات", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("${item.comments.size} تعليق", color = Muted, fontSize = 12.sp)
                }
                if (item.comments.isEmpty()) EmptyState(Icons.Outlined.ModeComment, "ما كايناش تعليقات", "كن أول واحد يعلّق على هاد الريلز.",
                    modifier = Modifier.padding(horizontal = 16.dp))
                else LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(item.comments, key = { it.id }) { comment ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Raised).padding(12.dp)
                            .then(if (comment.pending) Modifier.alpha(.7f) else Modifier), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Avatar(comment.author, 26)
                                PersonName(comment.author, size = 13)
                                Text(if (comment.failed) "تعذّر الإرسال" else relativePostDate(comment.date), color = if (comment.failed) AccentLight else Muted, fontSize = 10.5.sp)
                            }
                            Text(comment.text, color = TextMain, fontSize = 13.5.sp, lineHeight = 19.sp)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NabdField(text, { text = it }, "اكتب تعليقاً…", Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send))
                    GlassCircle(Icons.AutoMirrored.Outlined.Send, "إرسال",
                        { if (text.isNotBlank()) { send(text); text = "" } }, enabled = text.isNotBlank(), size = 44.dp, tint = Accent)
                }
            }
        }
    }
}

/** Sharing a reel outside the app, with the link itself as the message. */
@Composable private fun ReelShareSheet(item: ReelItem, close: () -> Unit, open: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(WindowDim), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                GlassCard(radius = 26.dp) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("مشاركة الريلز", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("الفيديو كيبقى عند الخدمة اللي نشراتو؛ نبض كيشارك الرابط فقط.", color = Muted, fontSize = 12.5.sp, lineHeight = 19.sp)
                        PillButton("فتح الرابط", { open(); close() }, modifier = Modifier.fillMaxWidth(), height = 46.dp, fontSize = 14)
                        PillButton("إغلاق", close, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
                    }
                }
            }
        }
    }
}

/**
 * Publishing a reel: the link travels, the video stays with the service that
 * owns it. The bar is honest about what it is — a vault write, not a file upload.
 */
@Composable internal fun ReelComposer(busy: Boolean, close: () -> Unit, publish: (String, String) -> Unit) {
    var link by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ReelPreview?>(null) }
    var looking by remember { mutableStateOf(false) }
    val ref = remember(link) { findReel(link) }
    LaunchedEffect(link) {
        val target = findReel(link)
        preview = null
        if (target != null) {
            looking = true
            preview = runCatching { ReelLibrary.previews.forId(target.id) }.getOrNull()
            looking = false
        }
    }
    Dialog(onDismissRequest = { if (!busy) close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(WindowDim), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                GlassCard(radius = 26.dp) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("ريلز جديد", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text("الصق رابط ريلز من إنستغرام أو تيك توك أو يوتيوب. الفيديو كيبقى عند الخدمة اللي نشراتو، ونبض ما كيرفع حتى ملف.",
                            color = Muted, fontSize = 12.5.sp, lineHeight = 19.sp)
                        NabdField(link, { link = it }, "رابط الريلز", Modifier.fillMaxWidth())
                        if (looking) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("جارٍ قراءة الرابط…", color = Muted, fontSize = 12.sp)
                        }
                        if (ref != null && preview != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (preview?.thumbnail != null) NabdImage(preview!!.thumbnail!!, "غلاف الريلز",
                                Modifier.size(width = 46.dp, height = 66.dp).clip(RoundedCornerShape(12.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(preview?.author ?: "ريلز", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("جاهز للنشر", color = Muted, fontSize = 11.sp)
                            }
                        }
                        if (link.isNotBlank() && ref == null) Text("هاد الرابط ماشي رابط ريلز معروف.", color = AccentLight, fontSize = 12.sp)
                        NabdField(caption, { caption = it }, "وصف (اختياري)", Modifier.fillMaxWidth(), maxLines = 3)
                        if (busy) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("جارٍ النشر…", color = Muted, fontSize = 12.sp)
                            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                        }
                        PillButton("نشر الريلز", { ref?.let { publish(it.raw, caption.trim()) } }, modifier = Modifier.fillMaxWidth(),
                            enabled = ref != null && !busy, height = 46.dp, fontSize = 14, icon = Icons.Outlined.Add, testTag = "reel-publish")
                        PillButton("إغلاق", { if (!busy) close() }, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
                    }
                }
            }
        }
    }
}
