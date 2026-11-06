package app.nabd.nativeapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.Poll
import java.time.Instant

/**
 * A poll is a real post: the question is the text, the options and every single
 * vote live next to it in the vault. Bars only appear once this account voted,
 * so the counts below are the counts the server holds.
 */
@Composable internal fun PollCard(poll: Poll, myVote: String?, busy: Boolean, onVote: (String) -> Unit, modifier: Modifier = Modifier) {
    val total = poll.votes.size
    val ended = runCatching { poll.endsAt.isNotBlank() && Instant.parse(poll.endsAt).isBefore(Instant.now()) }.getOrDefault(false)
    val revealed = myVote != null || ended
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Raised).border(1.dp, GlassEdge, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Poll, null, tint = Accent, modifier = Modifier.size(18.dp))
            Text("استطلاع رأي", color = Accent, fontSize = 12.5.sp, fontWeight = FontWeight.Black)
            if (ended) Text("مغلق", color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
        Text(poll.question, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Black, lineHeight = 23.sp)
        poll.options.forEach { option ->
            val count = poll.votes.count { it.value == option.id }
            val percent = if (total == 0) 0 else count * 100 / total
            val chosen = myVote == option.id
            if (revealed) {
                val width by animateFloatAsState(targetValue = (if (total == 0) 0f else count.toFloat() / total).coerceIn(0f, 1f),
                    animationSpec = tween(450), label = "poll-${option.id}")
                val pill = RoundedCornerShape(14.dp)
                Box(Modifier.fillMaxWidth().height(40.dp).clip(pill).background(Surface)) {
                    Box(Modifier.fillMaxWidth(width).height(40.dp).clip(pill).background(if (chosen) NabdBlue else Accent.copy(alpha = .18f)))
                    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(option.label, Modifier.weight(1f), color = TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("$percent%", color = if (chosen) Accent else Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Black)
                    }
                }
            } else Row(Modifier.fillMaxWidth().clip(pill(option.id)).background(Surface).border(1.dp, GlassEdge, pill(option.id))
                .pressClick(enabled = !busy, onClick = { onVote(option.id) }).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RadioDot(selected = false)
                Text(option.label, Modifier.weight(1f), color = TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (total == 1) "صوت واحد" else "$total أصوات", color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (!ended && myVote == null) Text("صوّت باش تشوف النتائج", color = Muted, fontSize = 11.5.sp)
        }
    }
}

private fun pill(seed: String) = RoundedCornerShape(14.dp)
