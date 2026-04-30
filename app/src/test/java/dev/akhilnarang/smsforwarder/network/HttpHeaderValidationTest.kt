package dev.akhilnarang.smsforwarder.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpHeaderValidationTest {
    @Test
    fun accepts_standard_header_name() {
        assertTrue(isValidHeaderName("X-Auth-Token"))
    }

    @Test
    fun rejects_header_name_with_whitespace_or_newline() {
        assertFalse(isValidHeaderName("X Auth"))
        assertFalse(isValidHeaderName("X-Test\nInjected"))
    }

    @Test
    fun accepts_printable_ascii_header_value() {
        assertTrue(isValidHeaderValue("Bearer abc123-._~"))
    }

    @Test
    fun rejects_header_value_with_newline_or_unicode() {
        assertFalse(isValidHeaderValue("line1\nline2"))
        assertFalse(isValidHeaderValue("token-\uD83D\uDE80"))
    }
}
