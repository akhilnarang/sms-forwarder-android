package dev.akhilnarang.smsforwarder.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardRegexTest {
    @Test
    fun matches_body_when_wildcards_surround_keyword() {
        val regex = wildcardRegex("*OTP*")
        assertTrue(regex.containsMatchIn("Your OTP is 123456"))
        assertTrue(regex.matches("OTP code 999"))
    }

    @Test
    fun is_case_insensitive() {
        val regex = wildcardRegex("*otp*")
        assertTrue(regex.containsMatchIn("Your OTP is 123456"))
    }

    @Test
    fun matches_sender_with_surrounding_wildcards() {
        val regex = wildcardRegex("*HDFC*")
        assertTrue(regex.matches("VK-HDFCBK"))
        assertTrue(regex.matches("HDFC"))
        assertFalse(regex.matches("ICICI"))
    }

    @Test
    fun literal_pattern_without_wildcards_matches_exactly() {
        val regex = wildcardRegex("HDFC")
        assertTrue(regex.matches("HDFC"))
        assertFalse(regex.matches("HDFCBK"))
    }

    @Test
    fun escapes_regex_metacharacters_in_literal_segments() {
        val regex = wildcardRegex("a.b")
        assertTrue(regex.matches("a.b"))
        assertFalse(regex.matches("axb"))
    }

    @Test
    fun wildcard_at_end_matches_prefix() {
        val regex = wildcardRegex("VM-*")
        assertTrue(regex.matches("VM-OTPSND"))
        assertFalse(regex.matches("VK-OTPSND"))
    }

    @Test
    fun bare_wildcard_matches_anything() {
        val regex = wildcardRegex("*")
        assertTrue(regex.matches(""))
        assertTrue(regex.matches("anything"))
    }
}
