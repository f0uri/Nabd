package app.nabd.nativeapp.data

import org.json.JSONObject
import java.time.Instant

/** One comment that is on its way to the vault, or that the vault refused. */
data class PendingComment(val key: String, val post: String, val text: String, val image: String?, val replyTo: String?, val date: String, val failed: Boolean = false)
data class PendingMessage(val key: String, val thread: String, val text: String, val image: String?, val replyTo: String?, val date: String, val failed: Boolean = false)
data class PendingPost(val key: String, val text: String, val image: String?, val date: String, val failed: Boolean = false)
data class PendingStory(val key: String, val text: String, val image: String?, val date: String, val failed: Boolean = false)
data class PendingReelComment(val key: String, val reel: String, val text: String, val date: String, val failed: Boolean = false)

/**
 * Everything the interface already shows but the vault has not confirmed yet.
 * The app never waits for the server: a press is painted at once, the write
 * follows in the background, and a refused write stays on screen so it can be
 * sent again instead of disappearing silently.
 */
data class PendingState(
    val comments: List<PendingComment> = emptyList(),
    val messages: List<PendingMessage> = emptyList(),
    val posts: List<PendingPost> = emptyList(),
    val stories: List<PendingStory> = emptyList(),
    val reactions: Map<String, String?> = emptyMap(),
    val follows: Map<String, Boolean> = emptyMap(),
    val reelComments: List<PendingReelComment> = emptyList(),
    /** Real shares tapped but not yet confirmed, per post id. */
    val shares: Map<String, Int> = emptyMap(),
) {
    fun drop(key: String) = copy(comments = comments.filter { it.key != key }, messages = messages.filter { it.key != key },
        posts = posts.filter { it.key != key }, stories = stories.filter { it.key != key }, reelComments = reelComments.filter { it.key != key })
    fun dropShare(postId: String) = copy(shares = shares - postId)
    fun fail(key: String) = copy(comments = comments.map { if (it.key == key) it.copy(failed = true) else it },
        messages = messages.map { if (it.key == key) it.copy(failed = true) else it },
        posts = posts.map { if (it.key == key) it.copy(failed = true) else it },
        stories = stories.map { if (it.key == key) it.copy(failed = true) else it },
        reelComments = reelComments.map { if (it.key == key) it.copy(failed = true) else it })
    fun has(key: String) = comments.any { it.key == key } || messages.any { it.key == key } || posts.any { it.key == key } ||
        stories.any { it.key == key } || reelComments.any { it.key == key }
}

private fun nowIso(): String = isoDate(System.currentTimeMillis())

/**
 * The snapshot the screens draw: the last one the server confirmed, with the
 * pending presses folded into it. Nothing invents a value that the vault did
 * not produce — a pending row is always marked as pending.
 */
fun Snapshot.optimistic(pending: PendingState): Snapshot {
    val self = me
    val byId = (people + self).associateBy { it.id }
    val following = { id: String -> pending.follows[id] ?: byId[id]?.following ?: false }
    val pendingPosts = pending.posts.map { item ->
        val post = FeedPost(item.key, self, item.text, item.date, item.image, 1f, null, 0, emptyList(), pending = true, failed = item.failed)
        // 2.9.6: a post that is still sending already shows its generated
        // comments growing — it never sits at 0 while the upload travels.
        if (self.username == OFFICIAL_HANDLE) post.copy(comments = FakePeople.commentsForPost(post, System.currentTimeMillis())) else post
    }
    val merged = posts.map { post ->
        val extra = pending.comments.filter { it.post == post.id }.map { item ->
            Comment(item.key, self, item.text, 0, false, item.date, item.replyTo, item.image, emptyMap(), null, pending = true, failed = item.failed)
        }
        val reaction = pending.reactions[post.id]
        val chosen = if (post.id in pending.reactions) reaction else post.myReaction
        val delta = when {
            post.id !in pending.reactions -> 0
            reaction != null && post.myReaction == null -> 1
            reaction == null && post.myReaction != null -> -1
            else -> 0
        }
        post.copy(comments = post.comments + extra, myReaction = chosen, likes = (post.likes + delta).coerceAtLeast(0),
            shares = post.shares + (pending.shares[post.id] ?: 0), pending = false, failed = false)
    }
    val threads = this.threads.map { thread ->
        val extra = pending.messages.filter { it.thread == thread.id }.map { item ->
            Message(item.key, self.id, item.text, false, false, item.image, false, item.replyTo, emptyMap(), item.date, null, pending = true, failed = item.failed)
        }
        thread.copy(messages = thread.messages + extra)
    }
    val stories = if (pending.stories.isEmpty()) this.stories else {
        val mine = pending.stories.map { item -> Story(item.key, self, item.text, item.image, item.date) }
        val group = this.stories.find { it.owner.id == self.id }
        if (group == null) this.stories + StoryGroup(self, mine, false)
        else this.stories.map { if (it.owner.id == self.id) it.copy(items = (it.items + mine).sortedBy { s -> s.date }) else it }
    }
    val reels = this.reels.map { reel ->
        val extra = pending.reelComments.filter { it.reel == reel.key }.map { item -> ReelComment(item.key, self, item.text, item.date, pending = true, failed = item.failed) }
        val chosen = pending.reactions[reel.key]
        val delta = when {
            reel.key !in pending.reactions -> 0
            chosen != null && reel.pulse == null -> 1
            chosen == null && reel.pulse != null -> -1
            else -> 0
        }
        reel.copy(comments = reel.comments + extra, pulse = if (reel.key in pending.reactions) chosen else reel.pulse,
            pulses = (reel.pulses + delta).coerceAtLeast(0))
    }
    val people = this.people.map { person -> person.copy(following = following(person.id), friend = pending.follows[person.id] ?: person.friend) }
    return copy(me = self, posts = pendingPosts + merged, people = people, threads = threads, stories = stories, reels = reels,
        social = social.copy(followers = social.followers.toMutableMap().apply {
            pending.follows.forEach { (id, follow) -> this[id] = (this[id] ?: 0) + if (follow) 1 else -1 }
        }))
}

/** Stable key of one optimistic press, so it can be confirmed or failed later. */
fun pendingKey(prefix: String): String = "$prefix-${newId()}"
