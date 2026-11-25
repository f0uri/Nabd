package app.nabd.nativeapp

import app.nabd.nativeapp.data.TelegramTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramFloodWaitTest {
    @Test fun parsesFloodWaitIntervalsCaseInsensitively() {
        assertEquals(12, TelegramTransport.parseFloodWaitSeconds("FLOOD_WAIT_12"))
        assertEquals(27, TelegramTransport.parseFloodWaitSeconds("flood wait: 27"))
        assertEquals(8, TelegramTransport.parseFloodWaitSeconds("Too Many Requests: retry after 8"))
        assertEquals(3, TelegramTransport.parseFloodWaitSeconds("Please wait 3 seconds"))
    }

    @Test fun unrelatedOrInvalidIntervalsAreIgnored() {
        assertNull(TelegramTransport.parseFloodWaitSeconds("TELEGRAM_INVALID_VAULT"))
        assertNull(TelegramTransport.parseFloodWaitSeconds("FLOOD_WAIT_0"))
        assertNull(TelegramTransport.parseFloodWaitSeconds("FLOOD_WAIT_forever"))
    }
}
