package app.nabd.nativeapp.data

/**
 * A reel of the tab. Reels published by an account of this app carry the
 * account itself; clips that come from discovery carry the name of the account
 * that published them there, and the video keeps living on that service.
 */
data class ReelItem(
    val key: String,
    val source: String,
    val ref: String,
    val video: String,
    val cover: String,
    val authorName: String,
    val author: Person?,
    val caption: String,
    val date: String,
    val pulses: Int,
    val pulse: String?,
    val comments: List<ReelComment>
)

/** One comment on a reel, written by a real account and never invented. */
data class ReelComment(val id: String, val author: Person, val text: String, val date: String,
    val pending: Boolean = false, val failed: Boolean = false)

/** A reel published inside نبض: the link is stored, the video stays with its owner. */
const val REEL_SOURCE_LINK = "link"
/** A discovery clip: only its social layer is ours, the file stays on TikTok. */
const val REEL_SOURCE_TIKTOK = "tiktok"

fun publishedReelKey(id: String) = "nb:$id"
/** The published id behind a key, or null when the reel is a discovery clip. */
fun reelIdOf(key: String) = key.substringAfter(':').takeIf { key.startsWith("nb:") }
