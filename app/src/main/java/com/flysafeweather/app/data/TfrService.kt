package com.flysafeweather.app.data

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit
import kotlin.math.*

class TfrService(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val clientId: String,
    @Suppress("UNUSED_PARAMETER") private val clientSecret: String
) {
    private val TAG = "TFR_DEBUG"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Fetches active TFRs from the FAA public GeoServer (replaces deprecated external-api.faa.gov).
     * Returns TFRs within [radiusNm] of the given point, plus all VIP/SECURITY TFRs nationwide.
     */
    suspend fun fetchTfrs(lat: Double, lon: Double, radiusNm: Int = DEFAULT_TFR_RADIUS_NM): List<TfrData> =
        withContext(Dispatchers.IO) {
            try {
                val allTfrs = fetchFromGeoServer()
                Log.d(TAG, "Fetched ${allTfrs.size} TFRs from GeoServer")

                val nearby = allTfrs.filter { tfr ->
                    isWithinRadius(tfr, lat, lon, radiusNm.toDouble())
                }
                val nearbyKeys = nearby.map { dedupeKey(it) }.toSet()
                val vipNationwide = allTfrs.filter { tfr ->
                    dedupeKey(tfr) !in nearbyKeys && isVipNationwideTfr(tfr)
                }

                val combined = (nearby + vipNationwide).distinctBy { dedupeKey(it) }
                Log.d(TAG, "Returning ${combined.size} TFRs (${nearby.size} within ${radiusNm}nm, ${vipNationwide.size} VIP nationwide)")
                combined
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching TFRs", e)
                emptyList()
            }
        }

    companion object {
        const val DEFAULT_TFR_RADIUS_NM = 30
        val TFR_RADIUS_OPTIONS_NM = listOf(5, 15, 30, 50)

        fun isVipNationwideTfr(tfr: TfrData): Boolean =
            tfr.type == TfrType.VIP_PRESIDENTIAL ||
                tfr.type == TfrType.SECURITY ||
                tfr.notamText.uppercase().let { text ->
                    text.contains("VIP") ||
                        text.contains("PRESIDENT") ||
                        text.contains("PRESIDENTIAL") ||
                        text.contains("AIR FORCE ONE") ||
                        text.contains("MARINE ONE") ||
                        text.contains("POTUS") ||
                        text.contains("VPOTUS")
                }
    }

    private fun dedupeKey(tfr: TfrData): String {
        val first = tfr.coordinates.firstOrNull()
        return "${first?.latitude}_${first?.longitude}_${tfr.notamText}_${tfr.type}"
    }

    private fun fetchFromGeoServer(): List<TfrData> {
        val url =
            "https://tfr.faa.gov/geoserver/TFR/ows" +
                "?service=WFS&version=1.0.0&request=GetFeature" +
                "&typeName=TFR:V_TFR_LOC&outputFormat=application/json"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "GeoServer WFS failed: HTTP ${response.code}")
            return emptyList()
        }

        val body = response.body?.string() ?: return emptyList()
        return parseGeoJsonResponse(body)
    }

    private fun parseGeoJsonResponse(json: String): List<TfrData> {
        val tfrs = mutableListOf<TfrData>()
        try {
            val root = JSONObject(json)
            val features = root.optJSONArray("features") ?: return emptyList()
            Log.d(TAG, "Parsing ${features.length()} GeoJSON features")

            for (i in 0 until features.length()) {
                try {
                    parseFeature(features.getJSONObject(i))?.let { tfrs.add(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing feature $i", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GeoJSON response", e)
        }
        return tfrs
    }

    private fun parseFeature(feature: JSONObject): TfrData? {
        val geometry = feature.optJSONObject("geometry") ?: return null
        val properties = feature.optJSONObject("properties") ?: return null

        val coordinateLists = parseGeometry(geometry)
        if (coordinateLists.isEmpty()) return null

        val title = properties.optString("TITLE", "")
        val legal = properties.optString("LEGAL", "")
        val notamKey = properties.optString("NOTAM_KEY", "")
        val modDate = properties.optString("LAST_MODIFICATION_DATETIME", "")
        val state = properties.optString("STATE", "")

        val notamText = buildString {
            if (title.isNotBlank()) append(title)
            if (notamKey.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("NOTAM: $notamKey")
            }
            if (state.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("State: $state")
            }
        }.ifBlank { "TFR $notamKey" }

        val (type, reason) = classifyTfrType("$legal $title")
        val (minAlt, maxAlt) = TfrTextParser.parseAltitudesFromText(legal, title)

        // Use the largest polygon area when multiple rings exist
        val coordinates = coordinateLists.maxByOrNull { it.size } ?: return null
        if (coordinates.size < 3) return null

        return TfrData(
            coordinates = coordinates,
            notamKey = notamKey,
            title = title,
            legal = legal,
            state = state,
            lastModified = modDate,
            cnsLocationId = properties.optString("CNS_LOCATION_ID", ""),
            notamText = notamText,
            effectiveStart = "",
            effectiveEnd = "",
            minAltitude = minAlt,
            maxAltitude = maxAlt,
            type = type,
            reason = reason
        )
    }

    private fun parseGeometry(geometry: JSONObject): List<List<LatLng>> {
        val type = geometry.optString("type", "").uppercase()
        return when (type) {
            "POLYGON" -> {
                val ring = geometry.optJSONArray("coordinates")?.optJSONArray(0)
                ring?.let { listOf(parseRing(it)) } ?: emptyList()
            }
            "MULTIPOLYGON" -> {
                val polygons = geometry.optJSONArray("coordinates") ?: return emptyList()
                val result = mutableListOf<List<LatLng>>()
                for (i in 0 until polygons.length()) {
                    val ring = polygons.optJSONArray(i)?.optJSONArray(0) ?: continue
                    val coords = parseRing(ring)
                    if (coords.size >= 3) result.add(coords)
                }
                result
            }
            else -> emptyList()
        }
    }

    private fun parseRing(ring: JSONArray): List<LatLng> {
        val points = mutableListOf<LatLng>()
        for (i in 0 until ring.length()) {
            val point = ring.optJSONArray(i) ?: continue
            if (point.length() >= 2) {
                val lon = point.getDouble(0)
                val lat = point.getDouble(1)
                points.add(LatLng(lat, lon))
            }
        }
        return points
    }

    fun isWithinRadius(tfr: TfrData, lat: Double, lon: Double, radiusNm: Double): Boolean {
        if (tfr.coordinates.isEmpty()) return false
        val centroid = centroidOf(tfr.coordinates)
        val distanceNm = haversineNm(centroid.latitude, centroid.longitude, lat, lon)
        if (distanceNm <= radiusNm) return true
        // Also check vertices in case centroid is outside radius but polygon extends into it
        return tfr.coordinates.any { point ->
            haversineNm(point.latitude, point.longitude, lat, lon) <= radiusNm
        }
    }

    private fun centroidOf(points: List<LatLng>): LatLng {
        var latSum = 0.0
        var lonSum = 0.0
        for (p in points) {
            latSum += p.latitude
            lonSum += p.longitude
        }
        return LatLng(latSum / points.size, lonSum / points.size)
    }

    private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3440.065 // Earth radius in nautical miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun classifyTfrType(notamText: String): Pair<TfrType, String> {
        val textUpper = notamText.uppercase()

        when {
            textUpper.contains("VIP") -> return TfrType.VIP_PRESIDENTIAL to "VIP Movement"
            textUpper.contains("SECURITY") -> return TfrType.SECURITY to "Security Restriction"
            textUpper.contains("SPACE") -> return TfrType.SPACE_OPERATIONS to "Space Operations"
            textUpper.contains("HAZARD") -> return TfrType.HAZARDS to "Hazardous Operations"
            textUpper.contains("MILITARY") -> return TfrType.MILITARY_OPS to "Military Operations"
            textUpper.contains("UAS") || textUpper.contains("DRONE") -> return TfrType.UAS_OPS to "Drone Operations"
            textUpper.contains("SPORT") || textUpper.contains("STADIUM") -> return TfrType.SPORTING_EVENT to "Sporting Event"
        }

        return TfrType.OTHER to "Flight Restriction"
    }

    /**
     * Fetches full TFR NOTAM detail from FAA save_pages XML (loaded when user opens TFR details).
     */
    suspend fun fetchTfrDetail(vararg rawNotamSources: String): TfrDetail? = withContext(Dispatchers.IO) {
        val normalized = rawNotamSources.firstNotNullOfOrNull { TfrNotamUrls.normalizeNotamId(it) }
            ?: return@withContext null
        val url = TfrNotamUrls.detailXmlUrl(normalized) ?: return@withContext null
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/xml, text/xml, */*")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "TFR detail failed for $normalized: HTTP ${response.code}")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            parseTfrDetailXml(normalized, body)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TFR detail for $normalized", e)
            null
        }
    }

    private fun parseTfrDetailXml(notamKey: String, xml: String): TfrDetail? {
        return try {
            val fields = mutableMapOf<String, String>()
            val parser = android.util.Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var tag = ""
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> tag = parser.name
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty() && tag.isNotEmpty()) {
                            fields[tag] = text
                        }
                    }
                }
                event = parser.next()
            }

            TfrDetail(
                notamKey = notamKey,
                dateIssued = fields["dateIssued"],
                dateEffective = fields["dateEffective"],
                dateExpires = fields["dateExpire"] ?: fields["dateExpires"],
                timezone = fields["codeTimeZone"],
                facilityId = fields["codeFacility"],
                facilityCity = fields["txtNameCity"],
                facilityState = fields["txtNameUSState"],
                tfrTypeCode = fields["codeType"],
                lowerAltitude = fields["valDistVerLower"],
                upperAltitude = fields["valDistVerUpper"],
                lowerAltitudeCode = fields["codeDistVerLower"],
                upperAltitudeCode = fields["codeDistVerUpper"],
                descriptionTraditional = fields["txtDescrTraditional"],
                descriptionUsns = fields["txtDescrUSNS"],
                freeFormText = fields["codeFreeformText"],
                pocName = fields["txtNamePOC"],
                pocPhone = fields["txtAddrPOCPhone"]
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing TFR detail XML for $notamKey", e)
            null
        }
    }
}
