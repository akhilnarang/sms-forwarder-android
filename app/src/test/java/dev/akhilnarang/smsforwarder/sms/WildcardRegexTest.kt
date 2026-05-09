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
    fun `wildcard star matches across newlines`() {
        val regex = wildcardRegex("*OTP*")
        assertTrue(regex.containsMatchIn("Your bank\nOTP is 123"))
    }

    @Test
    fun `wildcard star spans across newlines between literals`() {
        val regex = wildcardRegex("HDFC*OTP")
        assertTrue(regex.containsMatchIn("HDFC bank\nyour OTP"))
    }

    @Test
    fun bare_wildcard_matches_anything() {
        val regex = wildcardRegex("*")
        assertTrue(regex.matches(""))
        assertTrue(regex.matches("anything"))
    }

    // Realistic OTP fixtures (PII scrubbed: codes, amounts, and card endings
    // are placeholders; sender IDs follow the standard DLT header format).

    private val hdfcOtpSender = "JM-HDFCBK-S"
    private val hdfcOtpBody =
        "OTP is 000000 for txn of INR 0.00 at MERCHANT on HDFC Bank " +
            "card ending 0000. Valid till 00:00. Do not share OTP for security reasons"

    private val iciciOtpSender = "AD-ICICIO-T"
    private val iciciOtpBody =
        "000000 is One-Time Password for INR 0.00 transaction towards MERCHANT " +
            "using ICICI Bank Credit Card XX0000. OTPs are SECRET. DO NOT disclose"

    @Test
    fun otp_body_pattern_matches_hdfc_message() {
        assertTrue(wildcardRegex("*OTP*").containsMatchIn(hdfcOtpBody))
    }

    @Test
    fun otp_body_pattern_matches_icici_message() {
        // ICICI spells out "One-Time Password" but also says "OTPs" later.
        assertTrue(wildcardRegex("*OTP*").containsMatchIn(iciciOtpBody))
    }

    @Test
    fun one_time_password_pattern_matches_icici_but_not_hdfc() {
        val regex = wildcardRegex("*One-Time Password*")
        assertTrue(regex.containsMatchIn(iciciOtpBody))
        assertFalse(regex.containsMatchIn(hdfcOtpBody))
    }

    @Test
    fun hdfc_sender_pattern_matches_dlt_header() {
        assertTrue(wildcardRegex("*HDFCBK*").matches(hdfcOtpSender))
        assertFalse(wildcardRegex("*HDFCBK*").matches(iciciOtpSender))
    }

    @Test
    fun icici_sender_pattern_matches_dlt_header() {
        assertTrue(wildcardRegex("*ICICI*").matches(iciciOtpSender))
        assertFalse(wildcardRegex("*ICICI*").matches(hdfcOtpSender))
    }

    @Test
    fun bank_body_pattern_matches_both_otp_messages() {
        val regex = wildcardRegex("*Bank*")
        assertTrue(regex.containsMatchIn(hdfcOtpBody))
        assertTrue(regex.containsMatchIn(iciciOtpBody))
    }

    @Test
    fun sender_pattern_with_middle_wildcard_matches_dlt_header() {
        // Header format is <PE/PromoEntity>-<BrandID>-<channel suffix>.
        assertTrue(wildcardRegex("JM-*-S").matches(hdfcOtpSender))
        assertTrue(wildcardRegex("AD-*-T").matches(iciciOtpSender))
        assertFalse(wildcardRegex("JM-*-S").matches(iciciOtpSender))
    }
}
