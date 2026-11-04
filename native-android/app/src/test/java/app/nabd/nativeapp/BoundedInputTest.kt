package app.nabd.nativeapp
import app.nabd.nativeapp.data.readBounded
import org.junit.Assert.*
import org.junit.Test
class BoundedInputTest {
    @Test fun shortAndExactBodiesAreAcceptedWithoutRequiringPadding() {
        assertArrayEquals("{}".toByteArray(), readBounded("{}".byteInputStream(), 1024))
        assertArrayEquals("test".toByteArray(), readBounded("test".byteInputStream(), 4))
        assertEquals(0, readBounded("".byteInputStream(), 4).size)
    }
    @Test fun oversizedBodyFailsBeforeUnboundedBuffering() {
        assertTrue(runCatching { readBounded(ByteArray(1025).inputStream(), 1024) }.isFailure)
    }
}
