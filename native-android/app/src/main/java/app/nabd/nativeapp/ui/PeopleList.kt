package app.nabd.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Group
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.Person

/**
 * The followers or the followed accounts of one profile: the list is read
 * out of the follow graph of the vault, so every real row is an account
 * that exists and every press opens its profile. When the count grew with
 * generated people, the generated rows follow the real ones; [total] is
 * the full count, so the list can end with how many more there are.
 */
@Composable internal fun PeopleListScreen(title: String, people: List<Person>, modifier: Modifier = Modifier, busy: Boolean = false,
    onBack: () -> Unit, onProfile: (Person) -> Unit, onFollow: (Person, Boolean) -> Unit = { _, _ -> }, onChat: (Person) -> Unit = {},
    total: Int? = null) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassCircle(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "رجوع", onBack, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(title, color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(if (people.isEmpty()) "ما كاين تا حساب" else "${total ?: people.size} حساب", color = Muted, fontSize = 12.sp)
            }
        }
        if (people.isEmpty()) EmptyState(Icons.Outlined.Group, "اللائحة فارغة",
            "ما كاين تا حساب هنا بعد.", modifier = Modifier.padding(horizontal = 12.dp))
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(people, key = { it.id }) { person ->
                NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { onProfile(person) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(person, 46)
                        Column(Modifier.weight(1f)) {
                            PersonName(person, size = 14)
                            Text("@${person.username}", color = Muted, fontSize = 11.5.sp, maxLines = 1)
                        }
                        FollowPill(person.following, busy) { onFollow(person, !person.following) }
                    }
                }
            }
            val shown = total ?: people.size
            if (shown > people.size) item {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center) {
                    Text("و ${shown - people.size} آخرون.", color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
