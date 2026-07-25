package com.flysafeweather.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

/**
 * NOAA-style local solar time calculation (works offline).
 * @see https://gml.noaa.gov/grad/solcalc/calcdetails.html
 */
object SolarTimesCalculator {
    private const val ZENITH_SUN = 90.833
    private const val ZENITH_CIVIL = 96.0
    private val displayFormatter = DateTimeFormatter.ofPattern("h:mm a")

    fun calculate(
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId = ZoneId.systemDefault(),
        date: LocalDate = LocalDate.now(zoneId)
    ): SunCalculator.SunTimes {
        val sunrise = sunTime(latitude, longitude, date, zoneId, ZENITH_SUN, isSunrise = true)
        val sunset = sunTime(latitude, longitude, date, zoneId, ZENITH_SUN, isSunrise = false)
        val civilBegin = sunTime(latitude, longitude, date, zoneId, ZENITH_CIVIL, isSunrise = true)
        val civilEnd = sunTime(latitude, longitude, date, zoneId, ZENITH_CIVIL, isSunrise = false)

        return SunCalculator.SunTimes(
            sunrise = sunrise.format(displayFormatter),
            sunset = sunset.format(displayFormatter),
            civilTwilightBegin = civilBegin.format(displayFormatter),
            civilTwilightEnd = civilEnd.format(displayFormatter),
            timeZoneLabel = zoneId.id
        )
    }

    private fun sunTime(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zoneId: ZoneId,
        zenith: Double,
        isSunrise: Boolean
    ): LocalTime {
        val dayOfYear = date.dayOfYear
        val utHours = calculateUtHours(dayOfYear, latitude, longitude, zenith, isSunrise)
        val offsetSeconds = zoneId.rules.getOffset(date.atStartOfDay()).totalSeconds
        val localHours = normalizeHours(utHours + offsetSeconds / 3600.0)
        val hour = floor(localHours).toInt().coerceIn(0, 23)
        val minute = round((localHours - hour) * 60.0).toInt().coerceIn(0, 59)
        return LocalTime.of(hour, minute)
    }

    private fun calculateUtHours(
        dayOfYear: Int,
        latitude: Double,
        longitude: Double,
        zenith: Double,
        isSunrise: Boolean
    ): Double {
        val d2r = PI / 180.0
        val r2d = 180.0 / PI

        val lngHour = longitude / 15.0
        val t = if (isSunrise) {
            dayOfYear + (6.0 - lngHour) / 24.0
        } else {
            dayOfYear + (18.0 - lngHour) / 24.0
        }

        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(m * d2r)) + (0.020 * sin(2.0 * m * d2r)) + 282.634
        l = normalizeDegrees(l)

        var ra = r2d * atan(0.91764 * tan(l * d2r))
        ra = normalizeDegrees(ra)
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra = (ra + lQuadrant - raQuadrant) / 15.0

        val sinDec = 0.39782 * sin(l * d2r)
        val cosDec = cos(asin(sinDec))

        val cosH = (cos(zenith * d2r) - sinDec * sin(latitude * d2r)) /
            (cosDec * cos(latitude * d2r))

        if (cosH > 1.0) {
            return if (isSunrise) 0.0 else 12.0
        }
        if (cosH < -1.0) {
            return if (isSunrise) 12.0 else 24.0
        }

        val h = if (isSunrise) {
            360.0 - r2d * acos(cosH)
        } else {
            r2d * acos(cosH)
        }
        val hHours = h / 15.0

        val tLocal = hHours + ra - (0.06571 * t) - 6.622
        return normalizeHours(tLocal - lngHour)
    }

    private fun normalizeDegrees(value: Double): Double {
        var v = value
        while (v < 0.0) v += 360.0
        while (v >= 360.0) v -= 360.0
        return v
    }

    private fun normalizeHours(value: Double): Double {
        var v = value
        while (v < 0.0) v += 24.0
        while (v >= 24.0) v -= 24.0
        return v
    }
}
