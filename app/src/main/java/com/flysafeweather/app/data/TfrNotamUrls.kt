package com.flysafeweather.app.data

/** Builds FAA TFR website URLs (tfr3 detail pages; legacy save_pages XML for in-app fetch). */
object TfrNotamUrls {
    private val NOTAM_ID_PATTERN = Regex("""(\d+)\s*/\s*(\d+)""")

    /** Normalizes raw NOTAM text to FAA list/detail id form, e.g. "5/7910". */
    fun normalizeNotamId(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val match = NOTAM_ID_PATTERN.find(trimmed) ?: return null
        return "${match.groupValues[1]}/${match.groupValues[2]}"
    }

    /** Current FAA graphic TFR detail page (SPA). */
    fun detailPageUrl(raw: String): String? =
        normalizeNotamId(raw)?.let { id ->
            "https://tfr.faa.gov/tfr3/?page=detail_${id.replace("/", "_")}"
        }

    /** Legacy XML used for in-app full NOTAM fetch. */
    fun detailXmlUrl(raw: String): String? =
        normalizeNotamId(raw)?.let { id ->
            "https://tfr.faa.gov/save_pages/detail_${id.replace("/", "_")}.xml"
        }

    fun tfrListPageUrl(): String = "https://tfr.faa.gov/tfr3/?page=list"

    fun tfrMapPageUrl(): String = "https://tfr.faa.gov/tfr3/"
}
