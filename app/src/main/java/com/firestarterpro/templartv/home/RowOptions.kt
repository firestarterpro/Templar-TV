package com.firestarterpro.templartv.home

/**
 * The small bit of per-row options Templar writes into `options_json`. Only "inline_provider_rows" is
 * used so far, so this reads and writes just that key and leaves any other keys as they were.
 */
object RowOptions {
    private const val INLINE = "inline_provider_rows"
    private val inlineRegex = Regex("\"$INLINE\"\\s*:\\s*(true|false)")

    fun inlineProviderRows(json: String): Boolean =
        inlineRegex.find(json)?.groupValues?.get(1) == "true"

    fun withInlineProviderRows(json: String, on: Boolean): String {
        val value = "\"$INLINE\":$on"
        val trimmed = json.trim()
        return when {
            trimmed.isEmpty() || trimmed == "{}" -> "{$value}"
            inlineRegex.containsMatchIn(trimmed) -> inlineRegex.replace(trimmed, value)
            trimmed.startsWith("{") && trimmed.endsWith("}") -> trimmed.dropLast(1).trimEnd().let { "$it,$value}" }
            else -> "{$value}"
        }
    }
}
