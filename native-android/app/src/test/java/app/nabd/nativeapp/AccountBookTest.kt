package app.nabd.nativeapp

import app.nabd.nativeapp.data.ACCOUNT_LIMIT
import app.nabd.nativeapp.data.SavedAccount
import app.nabd.nativeapp.data.decodeBook
import app.nabd.nativeapp.data.encodeBook
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class AccountBookTest {
    private fun account(id: String, used: Long, name: String = "صاحب $id") =
        SavedAccount(id, name, "user$id", null, "nabd-tg:ns:${"a".repeat(32)}:${"b".repeat(64)}", used)

    @Test fun theBookKeepsOneRowPerAccountAndOrdersTheNewestFirst() {
        val raw = encodeBook(listOf(account("a".repeat(32), 10), account("b".repeat(32), 30), account("a".repeat(32), 20)), 1000)
        val entries = decodeBook(raw)
        assertEquals(2, entries.size)
        assertEquals("b".repeat(32), entries[0].id)
        assertEquals(20, entries[1].used)
    }

    @Test fun theBookNeverExceedsItsLimitAndDropsTheOldestOnly() {
        val many = (1..ACCOUNT_LIMIT + 3).map { account(it.toString().padStart(32, '0'), it.toLong()) }
        val entries = decodeBook(encodeBook(many, 9_000))
        assertEquals(ACCOUNT_LIMIT, entries.size)
        assertTrue(entries.all { it.used > 3 })
    }

    @Test fun malformedRowsAndBrokenJsonNeverCrashTheSwitcher() {
        assertTrue(decodeBook("not json at all").isEmpty())
        val array = JSONArray().put(org.json.JSONObject().put("id", "short").put("token", "x"))
        assertTrue(decodeBook(array.toString()).isEmpty())
        assertTrue(decodeBook("[]").isEmpty())
    }

    @Test fun anAvatarIsStoredAndRestoredUntouched() {
        val avatar = "data:image/png;base64,AAAA"
        val entries = decodeBook(encodeBook(listOf(account("c".repeat(32), 5).copy(avatar = avatar)), 1))
        assertEquals(avatar, entries.single().avatar)
    }
}
