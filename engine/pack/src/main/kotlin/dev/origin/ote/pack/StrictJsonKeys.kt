package dev.origin.ote.pack

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Reject ambiguous duplicate object keys and excessive nesting before decode. */
internal object StrictJsonKeys {
    fun check(text: String) {
        var index = 0
        val json = Json
        fun whitespace() { while (index < text.length && text[index] in " \t\r\n") index++ }
        fun requireAt(condition: Boolean) { if (!condition) throw PackException("Malformed or ambiguous JSON") }
        fun stringToken(): String {
            whitespace()
            requireAt(index < text.length && text[index] == '"')
            val start = index++
            var escaped = false
            while (index < text.length) {
                val character = text[index++]
                if (escaped) { escaped = false; continue }
                if (character == '\\') { escaped = true; continue }
                if (character == '"') return text.substring(start, index)
            }
            throw PackException("Unterminated JSON string")
        }
        lateinit var value: (Int) -> Unit
        value = { depth ->
            requireAt(depth <= 32)
            whitespace()
            requireAt(index < text.length)
            when (text[index]) {
                '{' -> {
                    index++; whitespace()
                    val seen = mutableSetOf<String>()
                    if (index < text.length && text[index] == '}') index++
                    else {
                        while (true) {
                            val key = try { json.decodeFromString<String>(stringToken()) } catch (e: Exception) {
                                throw PackException("Invalid JSON property", e)
                            }
                            requireAt(seen.add(key))
                            whitespace(); requireAt(index < text.length && text[index++] == ':')
                            value(depth + 1)
                            whitespace(); requireAt(index < text.length)
                            val delimiter = text[index++]
                            if (delimiter == '}') break
                            requireAt(delimiter == ',')
                        }
                    }
                }
                '[' -> {
                    index++; whitespace()
                    if (index < text.length && text[index] == ']') index++
                    else {
                        while (true) {
                            value(depth + 1)
                            whitespace(); requireAt(index < text.length)
                            val delimiter = text[index++]
                            if (delimiter == ']') break
                            requireAt(delimiter == ',')
                        }
                    }
                }
                '"' -> { stringToken(); Unit }
                else -> {
                    val start = index
                    while (index < text.length && text[index] !in ",]} \t\r\n") index++
                    requireAt(index > start)
                    // Grammar and finite numeric rules are enforced by the
                    // kotlinx decoder after this structural pre-pass.
                }
            }
        }
        value(0)
        whitespace()
        requireAt(index == text.length)
    }
}
