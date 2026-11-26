package app.nabd.nativeapp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatCountTest {
    @Test fun formatsExactAndInstagramStyleCompactCounts() {
        val cases = listOf(
            0L to "0",
            999L to "999",
            8_412L to "8,412",
            9_999L to "9,999",
            10_000L to "10K",
            14_238L to "14.2K",
            999_950L to "1M",
            1_000_000L to "1M",
            1_500_000L to "1.5M",
            670_000_000L to "670M",
        )
        cases.forEach { (value, expected) -> assertEquals("count=$value", expected, formatCount(value)) }
    }
}
