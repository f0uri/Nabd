package app.nabd.nativeapp.data

/** A group of stories the account keeps on its profile after the 24 hours. */
data class StoryHighlight(val id: String, val title: String, val items: List<HighlightItem>)
/** One kept story. Its image is stored with the group so it survives the expiry. */
data class HighlightItem(val id: String, val text: String, val image: String?, val date: String)
data class PollOption(val id: String, val label: String)
/** A real poll: one vote per account, counted from the votes the vault holds. */
data class Poll(val question: String, val options: List<PollOption>, val votes: Map<String, String>, val endsAt: String)
/** A voice note: small AMR audio kept next to the message it belongs to. */
data class VoiceNote(val uri: String, val seconds: Int, val bars: List<Int>)
