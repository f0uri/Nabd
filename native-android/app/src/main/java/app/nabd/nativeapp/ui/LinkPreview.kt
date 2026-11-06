package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.LinkCard
import app.nabd.nativeapp.data.LinkPreview
import app.nabd.nativeapp.data.findLink

/**
 * The preview of a link: the page is asked for its own Open Graph tags and the
 * card shows exactly what came back. While the page answers, nothing is drawn,
 * so a slow site never leaves an empty frame in the conversation.
 */
@Composable internal fun LinkPreviewCard(url: String, mine: Boolean, modifier: Modifier = Modifier) {
    var card by remember(url) { mutableStateOf(LinkPreview.cached(url)) }
    LaunchedEffect(url) { if (card == null) card = LinkPreview.fetch(url) }
    val found = card ?: return
    LinkCardBody(found, mine, modifier)
}

@Composable internal fun LinkCardBody(card: LinkCard, mine: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(16.dp)
    val plate = if (mine) Color(0x2BFFFFFF) else Raised
    val edge = if (mine) Color(0x59FFFFFF) else GlassEdge
    Column(modifier.fillMaxWidth().clip(shape).background(plate).border(1.dp, edge, shape)
        .then(if (onClick != null) Modifier.pressClick(onClick = onClick) else Modifier)) {
        if (card.image != null) NabdImage(card.image, card.title, Modifier.fillMaxWidth().heightIn(max = 160.dp).clip(shape), ContentScale.Crop)
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Language, null, tint = if (mine) Color.White.copy(alpha = .85f) else Accent, modifier = Modifier.size(14.dp))
                Text(card.host, color = if (mine) Color.White.copy(alpha = .8f) else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(card.title, color = if (mine) Color.White else TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.Black, maxLines = 2, lineHeight = 19.sp)
            if (card.description.isNotBlank()) Text(card.description, color = if (mine) Color.White.copy(alpha = .85f) else Muted,
                fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3)
        }
    }
}

/** A link inside a message, drawn under the text with the rest of the bubble. */
@Composable internal fun MessageLinkPreview(text: String, mine: Boolean, modifier: Modifier = Modifier) {
    val link = remember(text) { findLink(text) } ?: return
    Box(modifier.padding(top = 6.dp)) { LinkPreviewCard(link, mine) }
}

/** A link inside a post, drawn under the text like the image of the post. */
@Composable internal fun PostLinkPreview(text: String, modifier: Modifier = Modifier, onClick: (String) -> Unit = {}) {
    val link = remember(text) { findLink(text) } ?: return
    var card by remember(link) { mutableStateOf(LinkPreview.cached(link)) }
    LaunchedEffect(link) { if (card == null) card = LinkPreview.fetch(link) }
    val found = card ?: return
    LinkCardBody(found, false, modifier.padding(top = 12.dp)) { onClick(link) }
}
