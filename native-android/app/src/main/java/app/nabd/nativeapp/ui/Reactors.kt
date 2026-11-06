package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.FeedPost
import app.nabd.nativeapp.data.Person

/**
 * Who reacted, read from the votes the vault really keeps for the post: one row
 * per account, with the vector of the reaction it chose.
 */
@Composable internal fun ReactorsSheet(post: FeedPost, people: List<Person>, me: Person, close: () -> Unit, onProfile: (Person) -> Unit) {
    val rows = (post.voters.entries.mapNotNull { (id, kind) ->
        val person = if (id == me.id) me else people.find { it.id == id } ?: return@mapNotNull null
        person to kind
    }).sortedByDescending { it.first.name }
    GlassWindow(close = close) {
        Text("المتفاعلون", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Text("${rows.size} حساب تفاعل مع هاد المنشور.", color = Muted, fontSize = 12.sp)
        if (rows.isEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            Text("ما تفاعل تا شي حساب بعد.", color = Muted, fontSize = 13.sp)
        } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 4.dp)) {
            items(rows, key = { it.first.id + it.second }) { (person, kind) ->
                Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Raised)
                    .pressClick { onProfile(person) }.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(person, 40)
                    Column(Modifier.weight(1f)) {
                        PersonName(person, size = 14)
                        Text(reactionLabel(kind), color = Muted, fontSize = 11.5.sp)
                    }
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Accent.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                        ReactionGlyph(kind, size = 20.dp)
                    }
                }
            }
        }
        PillButton("إغلاق", close, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
    }
}
