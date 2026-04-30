package dev.akhilnarang.smsforwarder.network

private val HEADER_NAME_REGEX = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

fun isValidHeaderName(value: String): Boolean = value.isNotBlank() && HEADER_NAME_REGEX.matches(value)

fun isValidHeaderValue(value: String): Boolean =
    value.isNotEmpty() && value.all { character ->
        character == '\t' || character in '\u0020'..'\u007E'
    }
