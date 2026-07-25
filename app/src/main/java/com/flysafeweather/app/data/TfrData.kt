package com.flysafeweather.app.data

import com.google.android.gms.maps.model.LatLng

enum class TfrType {
    VIP_PRESIDENTIAL,
    SECURITY,
    SPACE_OPERATIONS,
    HAZARDS,
    MILITARY_OPS,
    UAS_OPS,
    NAV_SYS,
    INFRASTRUCTURE,
    SPORTING_EVENT,
    OTHER
}

data class TfrData(
    val coordinates: List<LatLng>,
    val notamKey: String = "",
    val title: String = "",
    val legal: String = "",
    val state: String = "",
    val lastModified: String = "",
    val cnsLocationId: String = "",
    /** Short summary for lists and legacy callers */
    val notamText: String = "",
    val effectiveStart: String = "",
    val effectiveEnd: String = "",
    val minAltitude: Int? = null,
    val maxAltitude: Int? = null,
    val type: TfrType = TfrType.OTHER,
    val reason: String = "Flight Restriction"
) {
    fun summaryAltitudes(): String =
        TfrTextParser.formatAltitudeRange(minAltitude, maxAltitude)

    fun faaDetailUrl(): String? =
        TfrNotamUrls.detailPageUrl(notamKey)
            ?: TfrNotamUrls.detailPageUrl(title)
            ?: TfrNotamUrls.detailPageUrl(legal)
}
