package app.nabd.nativeapp.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads UP TO a bound then requires EOF; Okio readByteArray(n) instead requires
 * exactly n bytes and is not an upper-bound API. Never buffer an unbounded body. */
fun readBounded(input: InputStream, limit: Int): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream(minOf(limit, 65536)); val buffer = ByteArray(8192)
    while (true) {
        val size = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (size < 0) return output.toByteArray()
        require(output.size() + size <= limit) { "RESPONSE_TOO_LARGE" }
        output.write(buffer, 0, size)
    }
}
