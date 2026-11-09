package app.nabd.nativeapp.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.MAX_VOICE_SECONDS
import app.nabd.nativeapp.data.VoiceNote
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * Voice notes stay small enough to travel inside the vault: the microphone is
 * recorded at AMR narrow band, and the result is checked against the shared
 * limit before it is ever handed to the repository.
 */
internal class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private val samples = mutableListOf<Int>()

    val recording: Boolean get() = recorder != null
    val bars: List<Int> get() = samples.toList()

    fun start(): Boolean = runCatching {
        val file = File(context.cacheDir, "voice-${System.currentTimeMillis()}.3gp").also { target = it }
        val engine = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(file.absolutePath)
            setMaxDuration(MAX_VOICE_SECONDS * 1000)
            prepare(); start()
        }
        recorder = engine; samples.clear(); true
    }.getOrDefault(false)

    /** The real loudness of the last slice, scaled to the 0..100 bars of the note. */
    fun sample() {
        val engine = recorder ?: return
        val peak = runCatching { engine.maxAmplitude }.getOrDefault(0)
        val level = (sqrt(peak.toFloat() / 12000f) * 100f).toInt().coerceIn(0, 100)
        samples.add(level)
        if (samples.size > 120) samples.removeAt(0)
    }

    /** Stops and returns the note as a small data URI, or null when nothing was recorded. */
    fun stop(): Triple<String, Int, List<Int>>? {
        val engine = recorder ?: return null
        val file = target ?: return null
        val seconds = (samples.size / 10).coerceIn(1, MAX_VOICE_SECONDS)
        val bars = samples.toList().ifEmpty { listOf(40) }
        return runCatching {
            engine.stop(); engine.release()
            recorder = null
            val bytes = file.readBytes()
            file.delete()
            if (bytes.isEmpty()) null
            // 2.9.6: the MIME must be audio/3gp — the old audio/3gpp was
            // refused by validAudio, so no voice note ever sent.
            else Triple("data:audio/3gp;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP), seconds, bars)
        }.getOrNull().also { recorder = null }
    }

    fun cancel() {
        runCatching { recorder?.stop(); recorder?.release() }
        recorder = null
        runCatching { target?.delete() }
        target = null
        samples.clear()
    }
}

/** One voice note at a time: the player is released as soon as the clip ends. */
internal object VoicePlayer {
    private var player: MediaPlayer? = null
    private var current: String? = null
    private var onEnd: (() -> Unit)? = null

    fun playing(id: String): Boolean = current == id && runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun toggle(context: Context, id: String, note: VoiceNote, onDone: () -> Unit) {
        if (playing(id)) { stop(); onDone(); return }
        stop()
        onEnd = onDone
        val file = runCatching {
            val bytes = android.util.Base64.decode(note.uri.substringAfter("base64,"), android.util.Base64.DEFAULT)
            File(context.cacheDir, "play-$id.3gp").apply { writeBytes(bytes) }
        }.getOrNull() ?: run { onDone(); return }
        val engine = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop(); onDone() }
                setOnErrorListener { _, _, _ -> stop(); onDone(); true }
                prepare(); start()
            }
        }.getOrNull()
        if (engine == null) { onDone(); return }
        player = engine; current = id
    }

    fun stop() {
        runCatching { player?.stop(); player?.release() }
        player = null; current = null
        onEnd = null
    }
}

@Composable internal fun DisposableVoicePlayer() {
    DisposableEffect(Unit) { onDispose { VoicePlayer.stop() } }
}

/**
 * The note itself: a play button, the real duration and the waveform that was
 * measured while the sender was speaking. Bars already heard are blue.
 */
@Composable internal fun VoiceBubble(note: VoiceNote, mine: Boolean, id: String, playing: Boolean, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    var progress by remember(id) { mutableFloatStateOf(0f) }
    LaunchedEffect(id, playing) {
        if (!playing) { progress = 0f; return@LaunchedEffect }
        val slice = (note.seconds * 1000L / 40).coerceAtLeast(25)
        repeat(40) { progress = (it + 1) / 40f; delay(slice) }
        progress = 0f
    }
    val ink = if (mine) Color.White else TextMain
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (mine) Color(0x33FFFFFF) else Accent.copy(alpha = .12f))
            .pressClick(onClick = onPlay).testTag("voice-play-$id"), contentAlignment = Alignment.Center) {
            if (playing) Icon(Icons.Outlined.Stop, null, tint = if (mine) Color.White else Accent, modifier = Modifier.size(16.dp))
            else PlayGlyph(Modifier.size(16.dp), if (mine) Color.White else Accent)
        }
        Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            note.bars.forEachIndexed { index, level ->
                val heard = ((index + 1).toFloat() / note.bars.size.coerceAtLeast(1)) <= progress
                Box(Modifier.width(2.dp).height((6 + level * 22 / 100).dp).clip(RoundedCornerShape(2.dp))
                    .background((if (mine) Color.White else Accent).copy(alpha = if (heard) .95f else .32f)))
            }
        }
        Text("${note.seconds}s", color = ink.copy(alpha = .85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** The bar that replaces the input while a note is being recorded. */
@Composable internal fun VoiceBar(seconds: Int, bars: List<Int>, onCancel: () -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Raised).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(AccentLight.copy(alpha = .14f)).pressClick(onClick = onCancel), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, "إلغاء التسجيل", tint = AccentLight, modifier = Modifier.size(16.dp))
        }
        Row(Modifier.weight(1f).height(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            bars.takeLast(60).forEach { level ->
                Box(Modifier.width(2.dp).height((5 + level * 20 / 100).dp).clip(RoundedCornerShape(2.dp)).background(Accent.copy(alpha = .8f)))
            }
            if (bars.isEmpty()) Box(Modifier.width(2.dp).height(6.dp).background(Accent.copy(alpha = .6f)))
        }
        Text("$seconds ث", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.size(34.dp).clip(CircleShape).background(NabdBlue).pressClick(onClick = onSend).testTag("voice-send"), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Mic, "إرسال التسجيل", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
