package com.flysafeweather.app.data

/**
 * Best-effort parsing of altitude hints from FAA TITLE/LEGAL text when detail XML is unavailable.
 */
object TfrTextParser {
    private val surfacePattern = Regex("""\b(SFC|SFCL|SURFACE|GND)\b""", RegexOption.IGNORE_CASE)
    private val unlimitedPattern = Regex("""\b(UNL|UNLIMITED)\b""", RegexOption.IGNORE_CASE)
    private val feetPattern = Regex("""\b(\d{3,6})\s*(?:FEET|FT|FT\.)\b(?:\s*MSL|\s*AGL)?""", RegexOption.IGNORE_CASE)
    private val flPattern = Regex("""\bFL\s*(\d{2,3})\b""", RegexOption.IGNORE_CASE)

    fun parseAltitudesFromText(vararg sources: String): Pair<Int?, Int?> {
        val altitudes = mutableListOf<Int>()
        var hasSurface = false
        var hasUnlimited = false

        sources.filter { it.isNotBlank() }.forEach { text ->
            if (surfacePattern.containsMatchIn(text)) hasSurface = true
            if (unlimitedPattern.containsMatchIn(text)) hasUnlimited = true
            feetPattern.findAll(text).forEach { match ->
                match.groupValues[1].toIntOrNull()?.let { altitudes.add(it) }
            }
            flPattern.findAll(text).forEach { match ->
                match.groupValues[1].toIntOrNull()?.let { fl ->
                    altitudes.add(fl * 100)
                }
            }
        }

        if (altitudes.isEmpty()) {
            return when {
                hasSurface && hasUnlimited -> 0 to null
                else -> null to null
            }
        }

        val min = if (hasSurface) 0 else altitudes.minOrNull()
        val max = if (hasUnlimited) null else altitudes.maxOrNull()
        return min to max
    }

    fun formatAltitudeRange(minAltitude: Int?, maxAltitude: Int?): String {
        val lower = when (minAltitude) {
            null -> "Unknown"
            0 -> "Surface"
            else -> "$minAltitude ft"
        }
        val upper = maxAltitude?.let { "$it ft" } ?: "Unlimited"
        return "$lower to $upper"
    }
}
