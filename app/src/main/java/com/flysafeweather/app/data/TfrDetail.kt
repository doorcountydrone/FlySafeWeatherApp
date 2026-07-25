package com.flysafeweather.app.data

data class TfrDetail(
    val notamKey: String,
    val dateIssued: String? = null,
    val dateEffective: String? = null,
    val dateExpires: String? = null,
    val timezone: String? = null,
    val facilityId: String? = null,
    val facilityCity: String? = null,
    val facilityState: String? = null,
    val tfrTypeCode: String? = null,
    val lowerAltitude: String? = null,
    val upperAltitude: String? = null,
    val lowerAltitudeCode: String? = null,
    val upperAltitudeCode: String? = null,
    val descriptionTraditional: String? = null,
    val descriptionUsns: String? = null,
    val freeFormText: String? = null,
    val pocName: String? = null,
    val pocPhone: String? = null
) {
    val faaDetailUrl: String?
        get() = TfrNotamUrls.detailPageUrl(notamKey)

    fun formattedAltitudes(): String? {
        val lower = formatAltitude(lowerAltitudeCode, lowerAltitude)
        val upper = formatAltitude(upperAltitudeCode, upperAltitude)
        if (lower == null && upper == null) return null
        return "${lower ?: "Surface"} to ${upper ?: "Unlimited"}"
    }

    fun formattedEffectivePeriod(): String? {
        val start = dateEffective?.takeIf { it.isNotBlank() }
        val end = dateExpires?.takeIf { it.isNotBlank() }
        if (start == null && end == null) return null
        val tz = timezone?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        return when {
            start != null && end != null -> "$start to $end$tz"
            start != null -> "From $start$tz"
            else -> "Until $end$tz"
        }
    }

    fun fullDescription(): String = buildString {
        descriptionTraditional?.takeIf { it.isNotBlank() }?.let {
            append(it.trim())
        }
        descriptionUsns?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append("\n\n")
            append(it.trim())
        }
        freeFormText?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append("\n\n")
            append(it.trim())
        }
    }.trim()

    private fun formatAltitude(code: String?, value: String?): String? {
        val c = code?.trim()?.uppercase()
        val v = value?.trim()
        return when {
            c == "SFC" || c == "GND" -> "Surface"
            v.isNullOrBlank() && (c == "UNL" || c == "UNLIMITED") -> "Unlimited"
            !v.isNullOrBlank() && !c.isNullOrBlank() -> "$v $c"
            !v.isNullOrBlank() -> "$v ft"
            !c.isNullOrBlank() -> c
            else -> null
        }
    }
}
