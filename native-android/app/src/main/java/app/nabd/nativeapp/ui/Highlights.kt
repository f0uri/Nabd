package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.Story
import app.nabd.nativeapp.data.StoryHighlight

/**
 * Highlights are the stories an account chose to keep: the covers, the titles
 * and the stories inside them all come from the vault, never from a cache.
 */
@Composable internal fun HighlightsCard(highlights: List<StoryHighlight>, mine: Boolean, busy: Boolean,
    stories: List<Story>, onOpen: (StoryHighlight) -> Unit, onCreate: (String, List<String>) -> Unit, onRemove: (String) -> Unit, modifier: Modifier = Modifier) {
    var create by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<StoryHighlight?>(null) }
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (mine) item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)).background(Raised).border(1.dp, Accent.copy(alpha = .5f), RoundedCornerShape(20.dp))
                    .pressClick(enabled = !busy && stories.isNotEmpty(), onClick = { create = true }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, null, tint = Accent, modifier = Modifier.size(26.dp))
                }
                Text("إضافة", color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        items(highlights, key = { it.id }) { highlight ->
            val cover = highlight.items.firstOrNull()
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val shape = RoundedCornerShape(20.dp)
                Box(Modifier.size(68.dp)) {
                    Box(Modifier.matchParentSize().clip(shape).background(Brush.horizontalGradient(listOf(Accent, AccentLight)))
                        .border(1.5.dp, Accent, shape).pressClick(onClick = { onOpen(highlight) }), contentAlignment = Alignment.Center) {
                        if (cover?.image != null) NabdImage(cover.image, highlight.title, Modifier.matchParentSize(), ContentScale.Crop)
                        else Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Text(highlight.items.size.toString(), Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(CircleShape)
                        .background(Color(0x990A1628)).padding(horizontal = 5.dp, vertical = 1.dp),
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    if (mine) Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp).size(22.dp).clip(CircleShape)
                        .background(CardFill).border(1.dp, GlassEdge, CircleShape).pressClick { remove = highlight }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "حذف الألبوم", tint = AccentLight, modifier = Modifier.size(13.dp))
                    }
                }
                Text(highlight.title, color = TextMain, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center,
                    modifier = Modifier.width(72.dp))
            }
        }
    }
    if (create) CreateHighlightWindow(stories, busy, close = { create = false }, create = { title, ids -> create = false; onCreate(title, ids) })
    val target = remove
    if (target != null) GlassConfirm("حذف «${target.title}»؟", "القصص اللي فيه غترجع غير مفاتحة 24 ساعة.", "حذف",
        onConfirm = { onRemove(target.id); remove = null }, close = { remove = null }, busy = busy, danger = true)
}

/** Choose the stories to keep and give the highlight a name. */
@Composable private fun CreateHighlightWindow(stories: List<Story>, busy: Boolean, close: () -> Unit, create: (String, List<String>) -> Unit) {
    var title by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>() }
    var problem by remember { mutableStateOf<String?>(null) }
    GlassWindow(close = { if (!busy) close() }, dismissible = !busy) {
        Text("أبرز القصص", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
        NabdField(title, { title = it.take(40) }, "اسم الألبوم", Modifier.fillMaxWidth(), enabled = !busy, tag = "highlight-title")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stories.forEach { story ->
                val picked = story.id in chosen
                val shape = RoundedCornerShape(16.dp)
                Row(Modifier.fillMaxWidth().clip(shape).background(if (picked) Accent.copy(alpha = .12f) else Raised).border(1.dp, if (picked) Accent else GlassEdge, shape)
                    .pressClick { if (picked) chosen.remove(story.id) else chosen.add(story.id) }.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RadioDot(selected = picked)
                    Text(story.text.ifBlank { "قصة بصورة" }, Modifier.weight(1f), color = TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    if (story.image != null) NabdImage(story.image, "قصة", Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)), ContentScale.Crop)
                }
            }
        }
        if (problem != null) Text(problem!!, color = AccentLight, fontSize = 12.sp)
        PillButton("حفظ", {
            when {
                title.trim().isBlank() -> problem = "دخل اسم الألبوم"
                chosen.isEmpty() -> problem = "اختار قصة وحدة على الأقل"
                else -> create(title.trim(), chosen.toList())
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && stories.isNotEmpty(), testTag = "highlight-save")
        PillButton("إلغاء", { if (!busy) close() }, modifier = Modifier.fillMaxWidth(), enabled = !busy, brush = null, height = 46.dp, fontSize = 14)
        if (stories.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
            Text("ما كاينة تا قصة حيّة دابا.", color = Muted, fontSize = 12.sp)
        }
    }
}
