package dev.akhilnarang.smsforwarder.sms

fun normalizeSender(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isBlank()) {
        return ""
    }

    val hasLetters = trimmed.any { it.isLetter() }
    return if (hasLetters) {
        trimmed.uppercase()
    } else {
        buildString(trimmed.length) {
            trimmed.forEachIndexed { index, character ->
                if (character.isDigit()) {
                    append(character)
                } else if (character == '+' && index == 0) {
                    append(character)
                }
            }
        }
    }
}

