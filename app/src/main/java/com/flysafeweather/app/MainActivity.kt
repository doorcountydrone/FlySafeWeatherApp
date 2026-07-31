package com.flysafeweather.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.lifecycleScope
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import com.flysafeweather.app.data.*
import com.flysafeweather.app.data.update.AppUpdateChecker
import com.flysafeweather.app.data.update.AppUpdateInstaller
import com.flysafeweather.app.data.update.AvailableUpdate
import com.flysafeweather.app.data.update.UpdatePromptStore
import com.flysafeweather.app.ui.*
import com.flysafeweather.app.ui.theme.DoorCountyDroneWeatherAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*
import org.json.JSONObject
import org.json.JSONArray
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileProvider
import com.google.android.gms.maps.model.Tile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import com.flysafeweather.app.ui.TFRMonitor

/** True when a coroutine was cancelled because the composable left the screen. */
private fun Throwable.isComposeCancellation(): Boolean =
    this is CancellationException ||
        message?.contains("left the composition", ignoreCase = true) == true

private const val SPLASH_HOLD_MS = 2_000L
private const val SPLASH_FADE_MS = 500

private val SafeGreen = Color(0xFF4CAF50)  // Material Design Green 500
private val MarginalOrange = Color(0xFFFF9800)  // Material Design Orange 500
private val UnsafeRed = Color(0xFFF44336)  // Material Design Red 500
private val SafeCardColor = Color(0xFFE8F5E9)  // Light green background
private val MarginalCardColor = Color(0xFFFFF3E0)  // Light orange background
private val UnsafeCardColor = Color(0xFFFFEBEE)  // Light red background
private val VfrGreen = Color(0xFF4CAF50)    // Material Green 500
private val MvfrBlue = Color(0xFF2196F3)    // Material Blue 500
private val IfrRed = Color(0xFFF44336)      // Material Red 500
private val LifrPurple = Color(0xFF9C27B0)  // Material Purple 500
private val ClassAColor = Color(0xFFFF0000)  // Red
private val ClassBColor = Color(0xFF0000FF)  // Blue
private val ClassCColor = Color(0xFF800080)  // Purple
private val ClassDColor = Color(0xFF0080FF)  // Light Blue
private val ClassEColor = Color(0xFF808080)  // Gray

@Composable
fun getColorForSafety(safety: FlightSafety): Color {
    return when (safety) {
        FlightSafety.SAFE -> SafeGreen
        FlightSafety.MARGINAL -> MarginalOrange
        FlightSafety.UNSAFE -> UnsafeRed
    }
}

@Composable
fun getColorForFlightCategory(category: String): Color {
    return when (category.uppercase()) {
        "VFR" -> VfrGreen
        "MVFR" -> MvfrBlue
        "IFR" -> IfrRed
        "LIFR" -> LifrPurple
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
fun getColorForVisibility(visibility: Double): Color {
    return when {
        visibility >= 5.0 -> SafeGreen
        visibility >= 3.0 -> MarginalOrange
        else -> UnsafeRed
    }
}

@Composable
fun getColorForWind(speed: Int, gust: Int): Color {
    return when {
        speed <= 10 && gust <= 15 -> SafeGreen
        speed <= 15 && gust <= 20 -> MarginalOrange
        else -> UnsafeRed
    }
}

@Composable
fun getColorForCeiling(layers: List<CloudLayer>): Color {
    val ceiling = layers.find { it.coverage in listOf("BKN", "OVC") }?.heightFeet ?: Int.MAX_VALUE
    return when {
        ceiling > 2000 -> SafeGreen
        ceiling > 1000 -> MarginalOrange
        else -> UnsafeRed
    }
}

fun degreesToCardinal(degrees: Int): String {
    val directions = arrayOf(
        "N", "NNE", "NE", "ENE", 
        "E", "ESE", "SE", "SSE", 
        "S", "SSW", "SW", "WSW", 
        "W", "WNW", "NW", "NNW"
    )
    val index = ((degrees + 11.25) / 22.5).toInt() % 16
    return directions[index]
}

@Composable
private fun WeatherInfoCard(
    title: String,
    value: Any,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)  // Use the condition color with 12% opacity for background
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            when (value) {
                is AnnotatedString -> Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge
                )
                else -> Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TfrMapView(
    currentLocation: Location?,
    searchCenter: LatLng?,
    tfrs: List<TfrData>,
    tfrService: TfrService,
    searchRadiusNm: Int = TfrService.DEFAULT_TFR_RADIUS_NM,
    onBackClick: () -> Unit
) {
    var selectedTfr by remember { mutableStateOf<TfrData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var timeRemaining by remember { mutableStateOf(120) } // 120 second countdown
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var shouldRefresh by remember { mutableStateOf(false) }
    val defaultLocation = LatLng(44.8436, -87.4215) // KSUE coordinates
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            searchCenter ?: currentLocation?.let { LatLng(it.latitude, it.longitude) } ?: defaultLocation,
            10f
        )
    }

    // Countdown timer effect
    LaunchedEffect(Unit) {
        while (timeRemaining > 0 && isLoading) {
            delay(1000)
            timeRemaining--
            if (timeRemaining == 0) {
                shouldRefresh = true
            }
        }
        isLoading = false
    }

    // Effect to track TFR loading
    LaunchedEffect(tfrs) {
        isLoading = tfrs.isEmpty()
        if (!isLoading) {
            timeRemaining = 120  // Updated to match the initial 120-second countdown
            shouldRefresh = false
        }
    }

    // Fit map to local TFRs + the active search point (GPS or manual airport).
    // Nationwide VIP TFRs stay on the map but do not zoom the view away.
    LaunchedEffect(tfrs, searchCenter, currentLocation, searchRadiusNm) {
        val validTfrs = tfrs.filter { tfr ->
            val c = tfr.coordinates
            c.size >= 3 && !(c.isNotEmpty() && c.all { kotlin.math.abs(it.latitude) <= 2.0 && kotlin.math.abs(it.longitude) <= 2.0 })
        }
        val center = searchCenter ?: currentLocation?.let { LatLng(it.latitude, it.longitude) }
        val localTfrs = if (center != null) {
            validTfrs.filter { tfr ->
                tfrService.isWithinRadius(tfr, center.latitude, center.longitude, searchRadiusNm.toDouble())
            }
        } else {
            validTfrs
        }
        delay(400)
        if (localTfrs.isEmpty()) {
            center?.let {
                try {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 10f)
                    )
                } catch (e: Exception) {
                    Log.d("TfrMapView", "Center on location failed: ${e.message}")
                }
            }
            return@LaunchedEffect
        }
        val builder = LatLngBounds.builder()
        center?.let { builder.include(it) }
        localTfrs.forEach { tfr -> tfr.coordinates.forEach { builder.include(it) } }
        try {
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(builder.build(), 120))
        } catch (e: Exception) {
            Log.d("TfrMapView", "Fit bounds failed: ${e.message}")
        }
    }

    // Auto-refresh effect
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            isLoading = true
            timeRemaining = 60
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize(),  // Full screen for TFR map
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = currentLocation != null,
                mapType = mapType
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = true,
                compassEnabled = true,
                zoomControlsEnabled = true
            )
        ) {
            // Draw TFRs (only with valid coordinates so Wisconsin VIP TFRs etc. render correctly)
            tfrs.forEach { tfr ->
                val coords = tfr.coordinates
                val nearNullIsland = coords.isNotEmpty() && coords.all { kotlin.math.abs(it.latitude) <= 2.0 && kotlin.math.abs(it.longitude) <= 2.0 }
                val hasValidCoords = coords.size >= 3 && !nearNullIsland
                if (!hasValidCoords) return@forEach

                val isVipOrPresidential = TfrService.isVipNationwideTfr(tfr)
                val strokeWidth = if (isVipOrPresidential) 4f else 2f
                val fillAlpha = if (isVipOrPresidential) 0.4f else 0.3f
                val strokeAlpha = if (isVipOrPresidential) 0.8f else 0.6f

                // Draw outer circle for VIP and Presidential TFRs
                if (isVipOrPresidential && coords.isNotEmpty()) {
                    Circle(
                        center = coords.first(),
                        radius = 50000.0, // 50km radius
                        fillColor = MaterialTheme.colorScheme.error.copy(alpha = fillAlpha * 0.5f),
                        strokeColor = MaterialTheme.colorScheme.error.copy(alpha = strokeAlpha * 0.5f),
                        strokeWidth = strokeWidth * 0.5f,
                        clickable = true,
                        onClick = {
                            selectedTfr = tfr
                            true
                        }
                    )
                }

                // Draw the main TFR polygon (needs at least 3 points)
                Polygon(
                    points = coords,
                    fillColor = MaterialTheme.colorScheme.error.copy(alpha = fillAlpha),
                    strokeColor = MaterialTheme.colorScheme.error.copy(alpha = strokeAlpha),
                    strokeWidth = strokeWidth,
                    clickable = true,
                    onClick = {
                        selectedTfr = tfr
                        true
                    }
                )
            }
        }

        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Map type toggle button
        IconButton(
            onClick = { mapType = if (mapType == MapType.HYBRID) MapType.NORMAL else MapType.HYBRID },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .size(40.dp)
        ) {
            Icon(
                imageVector = if (mapType == MapType.HYBRID) 
                    Icons.Default.Map 
                else 
                    Icons.Default.Satellite,
                contentDescription = "Toggle Map Type",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        if (!isLoading) {
            val center = searchCenter ?: currentLocation?.let { LatLng(it.latitude, it.longitude) }
            val nearbyCount = if (center != null) {
                tfrs.count { tfr ->
                    tfrService.isWithinRadius(tfr, center.latitude, center.longitude, searchRadiusNm.toDouble())
                }
            } else {
                tfrs.size
            }
            val vipNationwideCount = tfrs.count { tfr ->
                TfrService.isVipNationwideTfr(tfr) && (center == null || !tfrService.isWithinRadius(
                    tfr, center.latitude, center.longitude, searchRadiusNm.toDouble()
                ))
            }
            val summaryText = when {
                tfrs.isEmpty() -> "No TFRs in range"
                vipNationwideCount > 0 ->
                    "$nearbyCount within ${searchRadiusNm} nm · $vipNationwideCount VIP US-wide"
                else -> "$nearbyCount within ${searchRadiusNm} nm"
            }
            Text(
                text = summaryText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Loading indicator with increased top padding
        if (isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp)  // Increased padding to move it below the toggle button
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (timeRemaining > 0) 
                            "Loading TFRs... ${timeRemaining}s" 
                        else 
                            "Refreshing TFRs...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        selectedTfr?.let { tfr ->
            TfrDetailDialog(
                tfr = tfr,
                tfrService = tfrService,
                onDismiss = { selectedTfr = null }
            )
        }
    }
}

@Composable
fun AirspaceMapView(
    currentLocation: Location?,
    airports: List<Airport>,
    onBackClick: () -> Unit
) {
        var selectedAirport by remember { mutableStateOf<Airport?>(null) }
        val cameraPositionState = rememberCameraPositionState()
        var mapType by remember { mutableStateOf(MapType.HYBRID) }
        var showWeatherRadar by remember { mutableStateOf(false) }
        var radarRefreshKey by remember { mutableStateOf(0) } // Force radar refresh
        var radarAnimationFrame by remember { mutableStateOf(0) } // Current animation frame
        var isRadarAnimating by remember { mutableStateOf(true) } // Animation play/pause state
        
        // Aircraft tracking state
        var showAircraft by remember { mutableStateOf(false) }
        var aircraftStates by remember { mutableStateOf<List<AircraftState>>(emptyList()) }
        var aircraftRefreshKey by remember { mutableStateOf(0) }
        var nearbyAircraft by remember { mutableStateOf<List<AircraftState>>(emptyList()) }
    
    // Filter out Class E airspace
    val displayAirports = airports.filter { it.airspaceClass in listOf("B", "C", "D") }
    
    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(it.latitude, it.longitude),
                9f
            )
        }
    }

    // Radar animation - cycle through past 45 minutes
    LaunchedEffect(showWeatherRadar, isRadarAnimating) {
        if (showWeatherRadar && isRadarAnimating) {
            while (showWeatherRadar && isRadarAnimating) {
                delay(2000) // 2 seconds per frame for smooth, visible animation
                radarAnimationFrame = (radarAnimationFrame + 1) % 10 // Cycle through 10 frames (45 minutes in 5-minute intervals)
                android.util.Log.d("RadarAnimation", "Animation frame: $radarAnimationFrame")
            }
        }
    }
    
    // Auto-refresh radar data every 5 minutes
    LaunchedEffect(showWeatherRadar) {
        if (showWeatherRadar) {
            while (showWeatherRadar) {
                delay(300000) // 5 minutes
                if (showWeatherRadar) {
                    radarRefreshKey++ // Force radar data refresh
                    radarAnimationFrame = 0 // Reset animation to start
                    android.util.Log.d("RadarRefresh", "Refreshing radar data (refresh #$radarRefreshKey)")
                }
            }
        }
    }
    
    // Aircraft data fetching
    LaunchedEffect(showAircraft, currentLocation) {
        if (showAircraft && currentLocation != null) {
            Log.d("AircraftTracking", "Starting aircraft tracking for location: ${currentLocation.latitude}, ${currentLocation.longitude}")
            
            // Test API connectivity first
            val aircraftService = AircraftService()
            try {
                val testResult = aircraftService.testOpenSkyAPI()
                Log.d("AircraftTracking", "API Test Result: $testResult")
                
                // If test shows 0 aircraft, try anonymous request to see if it's an auth issue
                if (testResult.contains("0 aircraft found")) {
                    Log.d("AircraftTracking", "Test showed 0 aircraft, trying anonymous request...")
                    try {
                        val anonymousResult = aircraftService.tryAnonymousRequest()
                        anonymousResult.onSuccess { response ->
                            Log.d("AircraftTracking", "Anonymous request succeeded: ${response.states.size} aircraft")
                        }.onFailure { error ->
                            Log.e("AircraftTracking", "Anonymous request failed: $error")
                        }
                    } catch (e: Exception) {
                        Log.e("AircraftTracking", "Anonymous request exception", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("AircraftTracking", "API Test Failed", e)
            }
            
            while (showAircraft) {
                try {
                    Log.d("AircraftTracking", "Fetching aircraft data...")
                    val result = aircraftService.getAircraftStates()
                    result.onSuccess { response ->
                        try {
                            Log.d("AircraftTracking", "Successfully received response with ${response.states.size} aircraft states")
                            val allAircraft = aircraftService.parseAircraftStates(response.states)
                            Log.d("AircraftTracking", "Parsed ${allAircraft.size} aircraft from response")
                            aircraftStates = allAircraft
                            
                            // Filter aircraft within 100 miles
                            val nearby = aircraftService.filterAircraftNearLocation(
                                allAircraft, 
                                currentLocation.latitude, 
                                currentLocation.longitude,
                                100.0
                            )
                            nearbyAircraft = nearby
                            aircraftRefreshKey++
                            
                            Log.d("AircraftTracking", "Found ${nearby.size} aircraft within 100 miles of ${currentLocation.latitude}, ${currentLocation.longitude}")
                            Log.e("AircraftTracking", "NEARBY AIRCRAFT COUNT: ${nearby.size}") // Error level for visibility
                            
                            // Debug: Log first few aircraft details
                            if (nearby.isNotEmpty()) {
                                Log.d("AircraftTracking", "First aircraft: ${nearby.first().callsign} at ${nearby.first().latitude}, ${nearby.first().longitude}")
                            } else {
                                Log.w("AircraftTracking", "No aircraft found nearby. Total aircraft: ${allAircraft.size}")
                                // Log some sample aircraft from the full list for debugging
                                if (allAircraft.isNotEmpty()) {
                                    val sample = allAircraft.take(3)
                                    sample.forEach { aircraft ->
                                        Log.d("AircraftTracking", "Sample aircraft: ${aircraft.callsign} at ${aircraft.latitude}, ${aircraft.longitude}, onGround: ${aircraft.onGround}")
                                    }
                                }
                            }
                            if (nearby.isNotEmpty()) {
                                Log.d("AircraftTracking", "Nearby aircraft: ${nearby.map { "${it.callsign ?: it.icao24} at ${it.latitude}, ${it.longitude}" }}")
                            }
                        } catch (e: Exception) {
                            Log.e("AircraftTracking", "Error processing aircraft data", e)
                            nearbyAircraft = emptyList()
                        }
                    }.onFailure { error ->
                        Log.e("AircraftTracking", "Failed to fetch aircraft data", error)
                        nearbyAircraft = emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("AircraftTracking", "Error in aircraft tracking", e)
                    nearbyAircraft = emptyList()
                }
                
                delay(60000) // Refresh every 60 seconds to respect OpenSky rate limits
            }
        } else {
            Log.d("AircraftTracking", "Aircraft tracking disabled or no location available")
            nearbyAircraft = emptyList()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize(),  // Full screen for Airspace map
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = mapType,
                isMyLocationEnabled = currentLocation != null
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = true,
                compassEnabled = true,
                zoomControlsEnabled = true
            )
        ) {
            // Draw circles for each airport's airspace
            displayAirports.forEach { airport ->
                // Outer circle (with different floor)
                Circle(
                    center = LatLng(airport.latitude, airport.longitude),
                    radius = when (airport.airspaceClass) {
                        "B" -> 30000.0  // 30km radius for Class B
                        "C" -> 20000.0  // 20km radius for Class C
                        "D" -> 8000.0   // 8km radius for Class D
                        else -> 0.0     // No display for other classes
                    },
                    strokeColor = when (airport.airspaceClass) {
                        "B" -> ClassBColor.copy(alpha = 0.9f)
                        "C" -> ClassCColor.copy(alpha = 0.9f)
                        "D" -> ClassDColor.copy(alpha = 0.9f)
                        else -> Color.Transparent
                    },
                    fillColor = when (airport.airspaceClass) {
                        "B" -> ClassBColor.copy(alpha = 0.3f)
                        "C" -> ClassCColor.copy(alpha = 0.3f)
                        "D" -> ClassDColor.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    },
                    strokeWidth = 2f,
                    clickable = true,
                    onClick = {
                        selectedAirport = airport
                        true
                    }
                )

                // Inner circle (surface to ceiling)
                Circle(
                    center = LatLng(airport.latitude, airport.longitude),
                    radius = when (airport.airspaceClass) {
                        "B" -> 15000.0  // 15km radius for Class B inner circle
                        "C" -> 10000.0  // 10km radius for Class C inner circle
                        "D" -> 4000.0   // 4km radius for Class D inner circle
                        else -> 0.0     // No display for other classes
                    },
                    strokeColor = when (airport.airspaceClass) {
                        "B" -> ClassBColor.copy(alpha = 0.9f)
                        "C" -> ClassCColor.copy(alpha = 0.9f)
                        "D" -> ClassDColor.copy(alpha = 0.9f)
                        else -> Color.Transparent
                    },
                    fillColor = when (airport.airspaceClass) {
                        "B" -> ClassBColor.copy(alpha = 0.2f)
                        "C" -> ClassCColor.copy(alpha = 0.2f)
                        "D" -> ClassDColor.copy(alpha = 0.2f)
                        else -> Color.Transparent
                    },
                    strokeWidth = 2f,
                    clickable = true,
                    onClick = {
                        selectedAirport = airport
                        true
                    }
                )
                
                // Airport markers removed - airspace circles are now clickable
            }

            // Weather Radar Overlay with animation
            if (showWeatherRadar) {
                android.util.Log.e("RadarDisplay", "RADAR ENABLED: showWeatherRadar=$showWeatherRadar, frame=$radarAnimationFrame")
                key("radar_${radarRefreshKey}_${radarAnimationFrame}") {
                    TileOverlay(
                        tileProvider = object : TileProvider {
                            override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
                                // Calculate time offset for animation (45 minutes ago to now in 5-minute intervals)
                                val minutesAgo = 45 - (radarAnimationFrame * 5)
                                
                                // Calculate timestamp for historical radar
                                val currentTime = System.currentTimeMillis()
                                val historicalTime = currentTime - (minutesAgo * 60 * 1000)
                                val calendar = java.util.Calendar.getInstance()
                                calendar.timeInMillis = historicalTime
                                
                                // Format timestamp as YYYYMMDDHHMM for IEM radar archive
                                val year = calendar.get(java.util.Calendar.YEAR)
                                val month = String.format("%02d", calendar.get(java.util.Calendar.MONTH) + 1)
                                val day = String.format("%02d", calendar.get(java.util.Calendar.DAY_OF_MONTH))
                                val hour = String.format("%02d", calendar.get(java.util.Calendar.HOUR_OF_DAY))
                                val minute = String.format("%02d", calendar.get(java.util.Calendar.MINUTE))
                                val timestamp = "$year$month$day$hour$minute"
                                
                                // Use IEM radar archive with higher resolution
                                val url = "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/ridge::USCOMP-N0Q-$timestamp/$zoom/$x/$y.png"
                                android.util.Log.d("RadarAnimation", "Loading tile for $minutesAgo min ago (timestamp: $timestamp)")
                                return try {
                                    val connection = java.net.URL(url).openConnection()
                                    connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                                    connection.setRequestProperty("Pragma", "no-cache")
                                    connection.setRequestProperty("Expires", "0")
                                    connection.setRequestProperty("User-Agent", "AndroidWeatherApp/1.0")
                                    connection.connectTimeout = 10000
                                    connection.readTimeout = 10000
                                    val inputStream = connection.getInputStream()
                                    val buffer = inputStream.readBytes()
                                    inputStream.close()
                                    android.util.Log.d("RadarAnimation", "Loaded radar tile for $minutesAgo minutes ago (${buffer.size} bytes)")
                                    // Use 512x512 tiles for better resolution
                                    Tile(512, 512, buffer)
                                } catch (e: Exception) {
                                    android.util.Log.e("RadarRefresh", "Failed to load radar tile: ${e.message}")
                                    null
                                }
                            }
                        },
                        fadeIn = true,
                        transparency = 0.0f  // Fully opaque for better visibility
                    )
                }
            }
            
            // Aircraft markers - with safety checks
            if (showAircraft) {
                Log.d("AircraftRendering", "showAircraft: $showAircraft, nearbyAircraft.size: ${nearbyAircraft.size}")
                val safeAircraft = nearbyAircraft.filter { aircraft ->
                    aircraft.icao24.isNotBlank() && 
                    aircraft.latitude != null && aircraft.longitude != null &&
                    aircraft.latitude in -90.0..90.0 && aircraft.longitude in -180.0..180.0
                }
                
                Log.d("AircraftRendering", "safeAircraft.size: ${safeAircraft.size}")
                if (safeAircraft.isNotEmpty()) {
                    key("aircraft_$aircraftRefreshKey") {
                        safeAircraft.take(50).forEach { aircraft -> // Limit to 50 aircraft to prevent performance issues
                            aircraft.latitude?.let { lat ->
                                aircraft.longitude?.let { lon ->
                                    Marker(
                                        state = MarkerState(
                                            position = LatLng(lat, lon)
                                        ),
                                        title = (aircraft.callsign ?: aircraft.icao24).take(20), // Limit title length
                                        snippet = buildString {
                                            val altitudeFt = aircraft.baroAltitude?.let { (it * 3.28084).toInt() } ?: "N/A"
                                            append("Altitude: $altitudeFt ft\n")
                                            append("Speed: ${aircraft.velocity?.toInt() ?: "N/A"} kts\n")
                                            append("Track: ${aircraft.trueTrack?.toInt() ?: "N/A"}°\n")
                                            append("Country: ${aircraft.originCountry.take(10)}")
                                        },
                                        icon = createRotatedAirplaneIcon(LocalContext.current, aircraft.trueTrack)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Current location is shown as blue dot via isMyLocationEnabled
        }

        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)  // Moved to top
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Map type toggle button
        IconButton(
            onClick = { mapType = if (mapType == MapType.HYBRID) MapType.NORMAL else MapType.HYBRID },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, end = 100.dp)  // Moved left to avoid weather button
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .size(40.dp)
        ) {
            Icon(
                imageVector = if (mapType == MapType.HYBRID) 
                    Icons.Default.Map 
                else 
                    Icons.Default.Satellite,
                contentDescription = "Toggle Map Type",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Weather radar toggle button
        IconButton(
            onClick = { showWeatherRadar = !showWeatherRadar },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 80.dp)  // Moved left to avoid zoom controls
                .background(
                    color = if (showWeatherRadar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Toggle Weather Radar",
                tint = if (showWeatherRadar) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Radar animation controls and time display
        if (showWeatherRadar) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Time indicator with clock time
                val minutesAgo = 45 - (radarAnimationFrame * 5)
                val currentTime = System.currentTimeMillis()
                val radarTime = currentTime - (minutesAgo * 60 * 1000)
                val calendar = java.util.Calendar.getInstance()
                calendar.timeInMillis = radarTime
                val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val minute = String.format("%02d", calendar.get(java.util.Calendar.MINUTE))
                val clockTime = String.format("%02d:%s", hour, minute)
                
                Column {
                    Text(
                        text = if (minutesAgo == 0) "Current" else "-${minutesAgo} min",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = clockTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                // Play/Pause button
                IconButton(
                    onClick = { 
                        isRadarAnimating = !isRadarAnimating
                        if (!isRadarAnimating) {
                            // When pausing, jump to current radar
                            radarAnimationFrame = 9 // Frame 9 = current time (0 minutes ago)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isRadarAnimating) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause Radar Animation",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        // Aircraft tracking toggle button
        IconButton(
            onClick = { 
                showAircraft = !showAircraft
                Log.d("AircraftToggle", "Aircraft tracking toggled to: $showAircraft")
                Log.e("AircraftToggle", "AIRCRAFT TOGGLE PRESSED: $showAircraft") // Error level for visibility
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 136.dp)  // Moved further left to accommodate weather button
                .background(
                    color = if (showAircraft) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.plane),
                contentDescription = "Toggle Aircraft Tracking",
                tint = if (showAircraft) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)  // Make airplane icon bigger
            )
        }

        // Legend - Draggable
        var legendOffset by remember { mutableStateOf(Offset(0f, 120f)) } // Default position
        Column(
            modifier = Modifier
                .offset(
                    x = legendOffset.x.dp,
                    y = legendOffset.y.dp
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        legendOffset += dragAmount
                    }
                }
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                "Airspace Classes (Tap for Airport Info):",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))

            // Weather radar status
            if (showWeatherRadar) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Weather Radar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Weather Radar ON",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Aircraft tracking status
            if (showAircraft) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.plane),
                        contentDescription = "Aircraft",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Aircraft Tracking: ${nearbyAircraft.size} within 100 miles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(ClassBColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Class B",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(ClassCColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Class C",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(ClassDColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Class D",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Airport Info Dialog
        selectedAirport?.let { airport ->
            AlertDialog(
                onDismissRequest = { selectedAirport = null },
                title = { Text(airport.name) },
                text = {
                    Column {
                        Text("ICAO: ${airport.icao}")
                        Text("Class ${airport.airspaceClass} Airspace")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Inner Circle (Surface):", fontWeight = FontWeight.Bold)
                        Text("SFC - ${airport.airspaceCeiling}ft (${(airport.airspaceCeiling * 0.3048).toInt()}m)")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Outer Circle:", fontWeight = FontWeight.Bold)
                        when (airport.airspaceClass) {
                            "B" -> when (airport.icao) {
                                "KORD" -> Text("1900ft - 10000ft (580m - 3048m)")  // Chicago O'Hare
                                "KMSP" -> Text("2300ft - 10000ft (701m - 3048m)")  // Minneapolis
                                "KDTW" -> Text("2000ft - 8000ft (610m - 2438m)")   // Detroit
                                else -> Text("1500ft - ${airport.airspaceCeiling}ft (457m - ${(airport.airspaceCeiling * 0.3048).toInt()}m)")
                            }
                            "C" -> when (airport.icao) {
                                "KGRB" -> Text("1900ft - 4300ft (580m - 1311m)")   // Green Bay
                                "KMKE" -> Text("2300ft - 4800ft (701m - 1463m)")   // Milwaukee
                                "KMSN" -> Text("2500ft - 4300ft (762m - 1311m)")   // Madison
                                else -> Text("1200ft - ${airport.airspaceCeiling}ft (366m - ${(airport.airspaceCeiling * 0.3048).toInt()}m)")
                            }
                            "D" -> Text("SFC - ${airport.airspaceCeiling}ft (SFC - ${(airport.airspaceCeiling * 0.3048).toInt()}m)")
                            else -> Text("SFC - ${airport.airspaceCeiling}ft (SFC - ${(airport.airspaceCeiling * 0.3048).toInt()}m)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Lat: ${airport.latitude}")
                        Text("Long: ${airport.longitude}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedAirport = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    metarService: MetarService,
    locationService: LocationService,
    airportService: AirportService,
    tfrService: TfrService,
    preferencesManager: PreferencesManager,
    kpIndexService: KpIndexService,
    gnssService: GnssService,
    weatherCache: WeatherCache,
    isOnline: Boolean,
    onShowLegal: () -> Unit
) {
    // Get system theme as initial value
    val systemDarkTheme = isSystemInDarkTheme()
    
    // State for theme
    var isDarkTheme by rememberSaveable { 
        mutableStateOf(systemDarkTheme)
    }

    // Load saved theme preference
    LaunchedEffect(Unit) {
        preferencesManager.themePreference.collect { savedTheme ->
            isDarkTheme = savedTheme
        }
    }

    var isManualMode by rememberSaveable { mutableStateOf(false) }
    var manualAirportCode by rememberSaveable { mutableStateOf("") }
    var defaultAirportCode by rememberSaveable { mutableStateOf("") }  // Start empty
    var isEditingDefault by rememberSaveable { mutableStateOf(false) }
    var tempDefaultCode by rememberSaveable { mutableStateOf("") }
    var showWeatherPage by rememberSaveable { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }
    var tfrRadiusNm by remember { mutableIntStateOf(TfrService.DEFAULT_TFR_RADIUS_NM) }
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "?" }
    }

    val weatherData = remember { mutableStateOf<MetarData>(MetarData()) }
    val currentLocation = remember { mutableStateOf<Location?>(null) }
    val nearestAirport = remember { mutableStateOf<Airport?>(null) }
    val isLoading = remember { mutableStateOf<Boolean>(true) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()

    // Load saved default airport and navigate to weather page if exists
    LaunchedEffect(Unit) {
        preferencesManager.defaultAirport.collect { savedAirport ->
            if (savedAirport.isNotEmpty()) {
                defaultAirportCode = savedAirport
                // Fetch initial weather data for saved airport
                try {
                    isLoading.value = true
                    val weather = metarService.fetchMetar(savedAirport)
                    weatherData.value = weather
                    
                    val airport = airportService.findAirport(savedAirport)
                    if (airport != null) {
                        nearestAirport.value = airport
                        showWeatherPage = true  // Automatically show weather page
                    }
                } catch (e: Exception) {
                    errorMessage.value = e.message
                } finally {
                    isLoading.value = false
                }
            }
        }
    }

    // Load saved manual airport code
    LaunchedEffect(Unit) {
        preferencesManager.manualAirport.collect { savedManualAirport ->
            if (savedManualAirport.isNotEmpty()) {
                manualAirportCode = savedManualAirport
            }
        }
    }

    LaunchedEffect(Unit) {
        preferencesManager.tfrRadiusNm.collect { savedRadius ->
            tfrRadiusNm = savedRadius
        }
    }

    // Modify the auto-refresh LaunchedEffect
    LaunchedEffect(defaultAirportCode, isManualMode, manualAirportCode) {
        while (true) {
            try {
                isLoading.value = true
                // Use manualAirportCode if in manual mode, otherwise use defaultAirportCode
                val airportCode = if (isManualMode && manualAirportCode.isNotEmpty()) {
                    manualAirportCode
                } else {
                    defaultAirportCode
                }
                
                if (airportCode.isNotEmpty()) {
                    val weather = metarService.fetchMetar(airportCode)
                    weatherData.value = weather
                    
                    val airport = airportService.findAirport(airportCode)
                    if (airport != null) {
                        nearestAirport.value = airport
                    }
                }
            } catch (e: Exception) {
                Log.e("MainScreen", "Error fetching METAR", e)
                errorMessage.value = e.message
            } finally {
                isLoading.value = false
            }
            delay(120000) // 2 minutes refresh
        }
    }

    // Keep GPS updated while on the weather page (feeds sun times, airspace alerts, TFR map)
    LaunchedEffect(showWeatherPage) {
        if (!showWeatherPage) return@LaunchedEffect
        repeat(8) { attempt ->
            if (locationService.hasLocationPermission()) {
                try {
                    locationService.getCurrentLocation()?.let { loc ->
                        currentLocation.value = loc
                        Log.d("MainScreen", "GPS updated: ${loc.latitude}, ${loc.longitude}")
                        return@LaunchedEffect
                    }
                } catch (e: Exception) {
                    if (e.isComposeCancellation()) throw e
                    Log.e("MainScreen", "GPS attempt ${attempt + 1} failed", e)
                }
            }
            delay(3000)
        }
        while (isActive) {
            if (locationService.hasLocationPermission()) {
                try {
                    locationService.getCurrentLocation()?.let { currentLocation.value = it }
                } catch (e: Exception) {
                    if (e.isComposeCancellation()) throw e
                }
            }
            delay(60_000)
        }
    }

    if (showWeatherPage) {
        val monitoringLocation = resolveLocationForWeather(
            currentLocation.value,
            nearestAirport.value
        )
        Box(modifier = Modifier.fillMaxSize()) {
            WeatherPage(
                weatherData = weatherData,
                nearestAirport = nearestAirport,
                currentLocation = currentLocation,
                isManualMode = isManualMode,
                onManualModeChange = { isManualMode = it },
                manualAirportCode = manualAirportCode,
                onManualAirportCodeChange = { manualAirportCode = it.uppercase() },
                defaultAirportCode = defaultAirportCode,
                onBackClick = { showWeatherPage = false },
                metarService = metarService,
                airportService = airportService,
                locationService = locationService,
                tfrService = tfrService,
                isDarkTheme = isDarkTheme,
                onThemeChange = { newTheme ->
                    isDarkTheme = newTheme
                    scope.launch {
                        preferencesManager.saveThemePreference(newTheme)
                    }
                },
                isLoading = isLoading,
                errorMessage = errorMessage,
                kpIndexService = kpIndexService,
                gnssService = gnssService,
                preferencesManager = preferencesManager,
                weatherCache = weatherCache,
                isOnline = isOnline
            )

            ControlledAirspaceMonitor(
                currentLocation = monitoringLocation,
                onAirspaceWarning = { message ->
                    Log.d("Airspace", "Warning: $message")
                }
            )

            TFRMonitor(
                currentLocation = currentLocation.value,
                tfrService = tfrService,
                radiusNm = tfrRadiusNm,
                onTFRWarning = { message ->
                    Log.d("TFR", "Warning: $message")
                }
            )
        }
    } else {
        // Default airport setup page
        DoorCountyDroneWeatherAppTheme(darkTheme = isDarkTheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Set Your Default Airport",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { showInstructions = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Help,
                                            contentDescription = "Instructions"
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = tempDefaultCode,
                                onValueChange = { newValue -> tempDefaultCode = newValue.uppercase() },
                                label = { Text("Airport Code (e.g., KSUE)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    if (tempDefaultCode.isNotEmpty()) {
                                        scope.launch {
                                            preferencesManager.saveDefaultAirport(tempDefaultCode)
                                            defaultAirportCode = tempDefaultCode
                                            
                                            // Fetch initial weather data
                                            try {
                                                isLoading.value = true
                                                val weather = metarService.fetchMetar(defaultAirportCode)
                                                weatherData.value = weather
                                                
                                                val airport = airportService.findAirport(defaultAirportCode)
                                                if (airport != null) {
                                                    nearestAirport.value = airport
                                                    // Navigate to weather page
                                                    showWeatherPage = true
                                                }
                                            } catch (e: Exception) {
                                                errorMessage.value = e.message
                                            } finally {
                                                isLoading.value = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("SET DEFAULT AIRPORT")
                            }
                        }

                        if (isLoading.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }

                    // Instructions Dialog
                    if (showInstructions) {
                        AlertDialog(
                            onDismissRequest = { showInstructions = false },
                            title = { 
                                Text(
                                    "How to Use the App",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .verticalScroll(rememberScrollState())
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Version $versionName",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    InstructionSection(
                                        title = "Weather Information",
                                        content = """
                                            • Weather data is automatically fetched from the nearest airport
                                            • You can switch to manual mode to select a specific airport
                                            • Set your default airport in the settings
                                            • Color coding indicates flight conditions:
                                              - Green: Safe
                                              - Orange: Marginal
                                              - Red: Unsafe
                                        """.trimIndent()
                                    )
                                    
                                    InstructionSection(
                                        title = "24-Hour Forecast",
                                        content = """
                                            • For US airports (ICAO starting with 'K'):
                                              - Detailed National Weather Service forecast
                                              - Hour-by-hour predictions for temperature, wind, clouds
                                              - Precipitation chances and weather conditions
                                              - All data from official NWS sources
                                            • For international airports:
                                              - Comprehensive global weather forecast
                                              - Same detailed hourly breakdown
                                              - Temperature, wind, cloud cover, and conditions
                                              - Data from reliable international weather services
                                            • Updates automatically with airport changes
                                            • Available in both manual and default airport modes
                                        """.trimIndent()
                                    )
                                    
                                    InstructionSection(
                                        title = "TFR Map",
                                        content = """
                                            • Currently available for US airspace only
                                            • Shows Temporary Flight Restrictions in your area
                                            • Red polygons indicate restricted areas
                                            • Tap a TFR to view details
                                            • Loading can take up to 1 minute
                                            • Choose your search radius: 5, 15, 30, 50, 75, or 100 nm
                                            • The "within X nm" count reflects your selected radius
                                            • In Manual Mode, TFRs are searched and counted around the selected airport
                                            • In default mode, TFRs are searched around your GPS location
                                            • Also shows all VIP/SECURITY TFRs across the US, regardless of radius
                                            • International TFR data not currently available
                                        """.trimIndent()
                                    )
                                    
                                    InstructionSection(
                                        title = "Airspace Map",
                                        content = """
                                            • Currently available for US airspace only
                                            • Shows different classes of airspace
                                            • Color coding for airspace classes:
                                              - Blue: Class B
                                              - Purple: Class C
                                              - Light Blue: Class D
                                            • Tap airspace areas to view airport details
                                            • Clean interface without airport pins - just transparent airspace
                                            • Toggle between satellite and normal view
                                            • International airspace data not currently available
                                            
                                            Weather Radar Features:
                                            • Toggle weather radar overlay with cloud button
                                            • Animated radar shows precipitation from past 45 minutes to current
                                            • 10 frames cycling every 2 seconds (5-minute intervals)
                                            • Play/Pause button to control animation
                                            • Time indicator shows countdown (-45 min to Current) with clock time
                                            • Pause automatically jumps to current radar view
                                            • High-resolution NEXRAD composite radar from NOAA/IEM
                                            • Auto-refresh data every 5 minutes
                                            
                                            Aircraft Tracking Features:
                                            • Toggle aircraft tracking with flight button
                                                    • Shows aircraft within 100 miles of your location
                                            • Real-time ADS-B data from OpenSky Network
                                            • Aircraft markers show callsign, altitude, speed, track
                                            • Auto-refresh every 30 seconds when active
                                            • Legend shows count of nearby aircraft
                                            • Tap aircraft markers for detailed information
                                        """.trimIndent()
                                    )
                                    
                                    InstructionSection(
                                        title = "Flight Risk Assessment",
                                        content = """
                                            • Automatically calculates overall flight risk score (0-100)
                                            • Risk levels are color-coded:
                                              - Green: Low Risk (80-100)
                                              - Orange: Moderate Risk (60-79)
                                              - Red: High Risk (0-59)
                                            • Monitors multiple risk factors:
                                              - Flight Category (VFR, MVFR, IFR, LIFR)
                                              - Temperature and Battery Impact
                                              - Wind Speed Conditions
                                              - Visibility Range
                                              - Cloud Ceiling Heights
                                              - Precipitation
                                              - Temperature-Dewpoint Spread
                                              - Icing Risk (when temp < 35°F and dewpoint spread < 3°F)
                                              - Active TFRs within 5nm
                                              - Controlled Airspace Status
                                              - KP Index (GNSS interference)
                                              - GNSS Satellite Coverage
                                              - Maximum Flight Height (based on visibility, clouds, and TFRs)
                                              - Air Density Impact on Performance
                                            • Each risk factor shows specific values and recommendations
                                            • Updates in real-time as conditions change
                                            • Maximum flight height restrictions:
                                              - No flight if visibility < 3 miles
                                              - 500ft buffer required below clouds
                                              - Never exceeds 400ft AGL (Part 107)
                                              - Considers TFR altitude restrictions
                                            • Air density performance impact:
                                              - Calculates actual air density vs standard (1.225 kg/m³)
                                              - Shows density altitude for performance reference
                                              - Indicates expected reduction in lift capacity
                                              - Warns when significant impact (>20% reduction)
                                              - Considers temperature and pressure effects
                                            • KP Index and GNSS Information:
                                              - KP Index and satellite data are based on your actual location
                                              - These readings are independent of selected airport
                                              - Real-time updates regardless of manual/default airport setting
                                              - Use these for actual interference and positioning accuracy
                                        """.trimIndent()
                                    )
                                    
                                    InstructionSection(
                                        title = "Maximum Flight Height",
                                        content = """
                                            • Automatically calculates safe drone height
                                            • Considers cloud ceiling and visibility
                                            • Accounts for TFR restrictions
                                            • Never exceeds 400 feet AGL (Part 107)
                                            • Icing Warning: If the temperature is below 35°F and the dewpoint is within 2°F of the temperature, a warning will be displayed.
                                        """.trimIndent()
                                    )
                                    
                                    InstructionSection(
                                        title = "GNSS Satellite Information",
                                        content = """
                                            • Shows real-time satellite tracking data
                                            • Displays total satellites in view and used for fix
                                            • Color-coded status: Green (good fix) or Red (no fix)
                                            • Breakdown of satellite constellations:
                                              - GPS (US)
                                              - GLONASS (Russia)
                                              - Galileo (Europe)
                                              - BeiDou (China)
                                            • Minimum of 4 satellites needed for a valid fix
                                            • More satellites generally means better accuracy
                                        """.trimIndent()
                                    )
                                    
                                    InstructionSection(
                                        title = "⚠️ Disclaimer",
                                        content = """
                                            • This app is for informational purposes only
                                            • Always verify weather conditions on-site
                                            • Final responsibility for flight safety rests with the pilot
                                            • Contact ATC when required for controlled airspace
                                            • Follow all FAA regulations and local laws
                                            • Weather data and airspace information may not be current
                                            • Not for use in emergency operations
                                        """.trimIndent()
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showInstructions = false }) {
                                    Text("Got it")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherPage(
    weatherData: MutableState<MetarData>,
    nearestAirport: MutableState<Airport?>,
    currentLocation: MutableState<Location?>,
    isManualMode: Boolean,
    onManualModeChange: (Boolean) -> Unit,
    manualAirportCode: String,
    onManualAirportCodeChange: (String) -> Unit,
    defaultAirportCode: String,
    onBackClick: () -> Unit,
    metarService: MetarService,
    airportService: AirportService,
    locationService: LocationService,
    tfrService: TfrService,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    isLoading: MutableState<Boolean>,
    errorMessage: MutableState<String?>,
    kpIndexService: KpIndexService,
    gnssService: GnssService,
    preferencesManager: PreferencesManager,
    weatherCache: WeatherCache,
    isOnline: Boolean
) {
    // Collect GNSS data
    val gnssData by gnssService.gnssData.collectAsState()
    
    // Add LaunchedEffect to initialize GNSS tracking when page opens
    LaunchedEffect(Unit) {
        Log.d("WeatherPage", "Starting GNSS tracking")
        try {
            gnssService.initializeGnssTracking()
        } catch (e: Exception) {
            Log.e("WeatherPage", "Error starting GNSS tracking", e)
            errorMessage.value = "Error starting GNSS tracking: ${e.message}"
        }
    }

    // Add cleanup when leaving the page
    DisposableEffect(Unit) {
        onDispose {
            Log.d("WeatherPage", "Cleaning up GNSS tracking")
            gnssService.cleanup()
        }
    }

    var showMap by remember { mutableStateOf(false) }
    var tfrs by remember { mutableStateOf<List<TfrData>>(emptyList()) }
    var tfrRadiusNm by remember { mutableIntStateOf(TfrService.DEFAULT_TFR_RADIUS_NM) }
    var showAirspaceMap by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }
    var showChecklist by remember { mutableStateOf(false) }
    var showIcingWarning by remember { mutableStateOf(false) }
    var show24HourForecast by remember { mutableStateOf(false) }
    val airports = remember { mutableStateOf<List<Airport>>(emptyList()) }
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "?" }
    }
    val scope = rememberCoroutineScope()
    var kpIndexData by remember { mutableStateOf<KpIndexData?>(null) }
    var kpIndexError by remember { mutableStateOf<String?>(null) }
    val sunCalculator = remember { SunCalculator() }
    var sunTimes by remember { mutableStateOf<SunCalculator.SunTimes?>(null) }
    var isSunTimesLoading by remember { mutableStateOf(false) }
    var sunTimesError by remember { mutableStateOf<String?>(null) }
    var sunTimesLocationNote by remember { mutableStateOf("") }
    var tfrRadiusToSave by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(tfrRadiusToSave) {
        val radius = tfrRadiusToSave ?: return@LaunchedEffect
        try {
            preferencesManager.saveTfrRadiusNm(radius)
        } catch (e: Exception) {
            if (e.isComposeCancellation()) throw e
            Log.e("WeatherPage", "Error saving TFR radius", e)
        } finally {
            if (tfrRadiusToSave == radius) {
                tfrRadiusToSave = null
            }
        }
    }

    // Check for icing conditions whenever weather data changes
    LaunchedEffect(weatherData.value) {
        val temp = weatherData.value.temperatureF
        val dewpoint = weatherData.value.dewPointF
        showIcingWarning = temp <= 35.0 && abs(temp - dewpoint) <= 2.0
    }

    // Fetch airports
    LaunchedEffect(Unit) {
        try {
            val fetchedAirports = airportService.fetchAllAirports()
            airports.value = fetchedAirports
        } catch (e: Exception) {
            if (e.isComposeCancellation()) throw e
            Log.e("WeatherPage", "Error fetching airports", e)
            errorMessage.value = "Error fetching airports: ${e.message}"
        }
    }

    LaunchedEffect(Unit) {
        preferencesManager.tfrRadiusNm.collect { savedRadius ->
            tfrRadiusNm = savedRadius
        }
    }

    // Manual mode searches around the selected airport. Automatic mode searches
    // around GPS, falling back to the active airport while a GPS fix is unavailable.
    val tfrSearchCenter = when {
        isManualMode && nearestAirport.value != null -> {
            val airport = nearestAirport.value!!
            LatLng(airport.latitude, airport.longitude)
        }
        currentLocation.value != null -> {
            val location = currentLocation.value!!
            LatLng(location.latitude, location.longitude)
        }
        nearestAirport.value != null -> {
            val airport = nearestAirport.value!!
            LatLng(airport.latitude, airport.longitude)
        }
        else -> null
    }

    LaunchedEffect(tfrSearchCenter, tfrRadiusNm) {
        val center = tfrSearchCenter ?: return@LaunchedEffect
        try {
            tfrs = tfrService.fetchTfrs(
                lat = center.latitude,
                lon = center.longitude,
                radiusNm = tfrRadiusNm
            )
        } catch (e: Exception) {
            if (e.isComposeCancellation()) throw e
            Log.e("WeatherPage", "Error fetching TFRs", e)
            errorMessage.value = "Error fetching TFRs: ${e.message}"
        }
    }

    // Sun times — uses GPS when available, otherwise default airport coordinates
    LaunchedEffect(currentLocation.value, nearestAirport.value) {
        val gps = currentLocation.value
        val lat = gps?.latitude ?: nearestAirport.value?.latitude
        val lon = gps?.longitude ?: nearestAirport.value?.longitude

        if (lat == null || lon == null) {
            sunTimes = null
            sunTimesError = if (!locationService.hasLocationPermission()) {
                "Enable location permission in Settings"
            } else {
                null
            }
            sunTimesLocationNote = if (!locationService.hasLocationPermission()) {
                "Enable location permission for GPS sun times"
            } else {
                "Waiting for GPS fix…"
            }
            return@LaunchedEffect
        }

        isSunTimesLoading = true
        sunTimesError = null
        try {
            val times = sunCalculator.calculateSunriseSunset(lat, lon)
            sunTimes = times
            sunTimesLocationNote = when {
                gps != null -> "Based on your GPS location (${times.timeZoneLabel})"
                nearestAirport.value != null ->
                    "No GPS fix yet — using ${nearestAirport.value?.icao} coordinates (${times.timeZoneLabel})"
                else -> times.timeZoneLabel
            }
        } catch (e: Exception) {
            if (e.isComposeCancellation()) throw e
            Log.e("WeatherPage", "Error fetching sun times", e)
            sunTimes = null
            sunTimesError = e.message ?: "Error fetching sun times"
        } finally {
            if (currentCoroutineContext().isActive) {
                isSunTimesLoading = false
            }
        }
    }

    // Add KP Index fetching effect
    LaunchedEffect(Unit) {
        try {
            kpIndexData = kpIndexService.fetchKpIndex()
        } catch (e: Exception) {
            kpIndexError = e.message
        }
    }

    DoorCountyDroneWeatherAppTheme(darkTheme = isDarkTheme) {
        if (showMap) {
            TfrMapView(
                currentLocation = currentLocation.value,
                searchCenter = tfrSearchCenter,
                tfrs = tfrs,
                tfrService = tfrService,
                searchRadiusNm = tfrRadiusNm,
                onBackClick = { showMap = false }
            )
        } else if (showAirspaceMap) {
            AirspaceMapView(
                currentLocation = currentLocation.value,
                airports = airports.value,
                onBackClick = { showAirspaceMap = false }
            )
        } else if (showChecklist) {
            DroneChecklistScreen(onBackClick = { showChecklist = false })
        } else if (show24HourForecast) {
            NwsForecastScreen(
                latitude = nearestAirport.value?.latitude ?: 0.0,
                longitude = nearestAirport.value?.longitude ?: 0.0,
                currentAirport = if (isManualMode) manualAirportCode else defaultAirportCode,
                onBackClick = { show24HourForecast = false }
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Top section with controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Theme toggle and help button in top bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { onThemeChange(!isDarkTheme) }) {
                                        Icon(
                                            imageVector = if (isDarkTheme) {
                                                Icons.Outlined.LightMode
                                            } else {
                                                Icons.Outlined.DarkMode
                                            },
                                            contentDescription = if (isDarkTheme) {
                                                "Switch to Light Mode"
                                            } else {
                                                "Switch to Dark Mode"
                                            }
                                        )
                                    }
                                    IconButton(onClick = { showInstructions = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Help,
                                            contentDescription = "Instructions"
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Manual Mode")
                                    Switch(
                                        checked = isManualMode,
                                        onCheckedChange = { newMode ->
                                            onManualModeChange(newMode)
                                            scope.launch {
                                                try {
                                                if (newMode && manualAirportCode.isNotEmpty()) {
                                                    // Save manual airport code when switching to manual mode
                                                    preferencesManager.saveManualAirport(manualAirportCode)
                                                    try {
                                                        isLoading.value = true
                                                        val weather = metarService.fetchMetar(manualAirportCode)
                                                        weatherData.value = weather
                                                        val airport = airportService.findAirport(manualAirportCode)
                                                        if (airport != null) {
                                                            nearestAirport.value = airport
                                                        }
                                                    } catch (e: Exception) {
                                                        errorMessage.value = e.message
                                                    } finally {
                                                        isLoading.value = false
                                                    }
                                                } else if (!newMode) {
                                                    // Save the current manual code before switching back to automatic
                                                    if (manualAirportCode.isNotEmpty()) {
                                                        preferencesManager.saveManualAirport(manualAirportCode)
                                                    }
                                                    try {
                                                        isLoading.value = true
                                                        val weather = metarService.fetchMetar(defaultAirportCode)
                                                        weatherData.value = weather
                                                        val airport = airportService.findAirport(defaultAirportCode)
                                                        if (airport != null) {
                                                            nearestAirport.value = airport
                                                        }
                                                    } catch (e: Exception) {
                                                        errorMessage.value = e.message
                                                    } finally {
                                                        isLoading.value = false
                                                    }
                                                }
                                                } catch (e: Exception) {
                                                    if (e.isComposeCancellation()) throw e
                                                    Log.e("WeatherPage", "Manual mode change failed", e)
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            // Instructions Dialog
                            if (showInstructions) {
                                AlertDialog(
                                    onDismissRequest = { showInstructions = false },
                                    title = { 
                                        Text(
                                            "How to Use the App and Disclaimer",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    text = {
                                        Column(
                                            modifier = Modifier
                                                .verticalScroll(rememberScrollState())
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Version $versionName",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))

                                            InstructionSection(
                                                title = "Weather Information",
                                                content = """
                                                    • Weather data is automatically fetched from the nearest airport
                                                    • You can switch to manual mode to select a specific airport
                                                    • Set your default airport in the settings
                                                    • Verify and check Raw Metar Data for accuracy
                                                    • Sun Times are based on the current location not the airport if you are flying from a different location
                                                    • Color coding indicates flight conditions:
                                                      - Green: Safe
                                                      - Orange: Marginal
                                                      - Red: Unsafe
                                                """.trimIndent()
                                            )
                                            
                                            InstructionSection(
                                                title = "24-Hour Forecast",
                                                content = """
                                                    • For US airports (ICAO starting with 'K'):
                                                      - Detailed National Weather Service forecast
                                                      - Hour-by-hour predictions for temperature, wind, clouds
                                                      - Precipitation chances and weather conditions
                                                      - All data from official NWS sources
                                                    • For international airports:
                                                      - Comprehensive global weather forecast
                                                      - Same detailed hourly breakdown
                                                      - Temperature, wind, cloud cover, and conditions
                                                      - Data from reliable international weather services
                                                    • Updates automatically with airport changes
                                                    • Available in both manual and default airport modes
                                                """.trimIndent()
                                            )
                                            
                                            InstructionSection(
                                                title = "TFR Map",
                                                content = """
                                                    • Shows Temporary Flight Restrictions in your area
                                                    • Red polygons indicate restricted areas
                                                    • Tap a TFR to view details
                                                    • Loading can take up to 1 minute
                                                    • Choose your search radius: 5, 15, 30, 50, 75, or 100 nm
                                                    • The "within X nm" count reflects your selected radius
                                                    • In Manual Mode, TFRs are searched and counted around the selected airport
                                                    • In default mode, TFRs are searched around your GPS location
                                                    • Also shows all VIP/SECURITY TFRs across the US, regardless of radius
                                                    • International TFR data not currently available
                                                """.trimIndent()
                                            )
                                            
                                            InstructionSection(
                                                title = "Airspace Map",
                                                content = """
                                                    • Shows different classes of airspace
                                                    • Color coding for airspace classes:
                                                      - Blue: Class B
                                                      - Purple: Class C
                                                      - Light Blue: Class D
                                                    • Tap airspace areas to view airport details
                                                    • Clean interface without airport pins - just transparent airspace
                                                    • Toggle between satellite and normal view
                                                    
                                                    Weather Radar Features:
                                                    • Tap the cloud button (top-right) to toggle weather radar
                                                    • Animated radar shows precipitation from past 45 minutes to current
                                                    • 10 frames cycling every 2 seconds (5-minute intervals)
                                                    • Play/Pause button appears below cloud button when radar is on
                                                    • Time indicator shows countdown (-45 min to Current) with clock time
                                                    • Pause button automatically jumps to current radar view
                                                    • High-resolution NEXRAD composite radar from NOAA/IEM
                                                    • Auto-refresh data every 5 minutes
                                                    • Blue button indicates radar is active
                                                    
                                                    Aircraft Tracking Features:
                                                    • Tap the flight button to toggle aircraft tracking
                                                    • Shows aircraft within 100 miles of your location
                                                    • Real-time ADS-B data from OpenSky Network
                                                    • Aircraft markers show callsign, altitude, speed, track
                                                    • Auto-refresh every 30 seconds when active
                                                    • Blue button indicates tracking is active
                                                    • Legend shows count of nearby aircraft
                                                    • Tap aircraft markers for detailed information
                                                    
                                                    Draggable Legend:
                                                    • Airspace Classes card can be moved anywhere on screen
                                                    • Touch and drag the legend to reposition it
                                                    • Move it out of the way or position it where convenient
                                                    • Legend stays where you place it
                                                    
                                                    • International airspace data not currently available
                                                """.trimIndent()
                                            )
                                            
                                            InstructionSection(
                                                title = "Flight Risk Assessment",
                                                content = """
                                                    • Automatically calculates overall flight risk score (0-100)
                                                    • Risk levels are color-coded:
                                                      - Green: Low Risk (80-100)
                                                      - Orange: Moderate Risk (60-79)
                                                      - Red: High Risk (0-59)
                                                    • Monitors multiple risk factors:
                                                      - Flight Category (VFR, MVFR, IFR, LIFR)
                                                      - Temperature and Battery Impact
                                                      - Wind Speed Conditions
                                                      - Visibility Range
                                                      - Cloud Ceiling Heights
                                                      - Precipitation
                                                      - Temperature-Dewpoint Spread
                                                      - Icing Risk (when temp < 35°F and dewpoint spread < 3°F)
                                                      - Active TFRs within 5nm
                                                      - Controlled Airspace Status
                                                      - KP Index (GNSS interference)
                                                      - GNSS Satellite Coverage
                                                      - Maximum Flight Height (based on visibility, clouds, and TFRs)
                                                      - Air Density Impact on Performance
                                                    • Each risk factor shows specific values and recommendations
                                                    • Updates in real-time as conditions change
                                                    • Maximum flight height restrictions:
                                                      - No flight if visibility < 3 miles
                                                      - 500ft buffer required below clouds
                                                      - Never exceeds 400ft AGL (Part 107)
                                                      - Considers TFR altitude restrictions
                                                    • Air density performance impact:
                                                      - Calculates actual air density vs standard (1.225 kg/m³)
                                                      - Shows density altitude for performance reference
                                                      - Indicates expected reduction in lift capacity
                                                      - Warns when significant impact (>20% reduction)
                                                      - Considers temperature and pressure effects
                                                    • KP Index and GNSS Information:
                                                      - KP Index and satellite data are based on your actual location
                                                      - These readings are independent of selected airport
                                                      - Real-time updates regardless of manual/default airport setting
                                                      - Use these for actual interference and positioning accuracy
                                                """.trimIndent()
                                            )
                                            
                                            InstructionSection(
                                                title = "Maximum Flight Height",
                                                content = """
                                                    • Automatically calculates safe drone height
                                                    • Considers cloud ceiling and visibility
                                                    • Accounts for TFR restrictions
                                                    • Never exceeds 400 feet AGL (Part 107)
                                                    • Icing Warning: If the temperature is below 35°F and the dewpoint is within 2°F of the temperature, a warning will be displayed.
                                                """.trimIndent()
                                            )
                                            
                                            InstructionSection(
                                                title = "GNSS Satellite Information",
                                                content = """
                                                    • Shows real-time satellite tracking data
                                                    • Displays total satellites in view and used for fix
                                                    • Color-coded status: Green (good fix) or Red (no fix)
                                                    • Breakdown of satellite constellations:
                                                      - GPS (US)
                                                      - GLONASS (Russia)
                                                      - Galileo (Europe)
                                                      - BeiDou (China)
                                                    • Minimum of 4 satellites needed for a valid fix
                                                    • More satellites generally means better accuracy
                                                """.trimIndent()
                                            )
                                            
                                            InstructionSection(
                                                title = "⚠️ Disclaimer",
                                                content = """
                                                    • This app is for informational purposes only
                                                    • Always verify weather conditions on-site
                                                    • Final responsibility for flight safety rests with the pilot
                                                    • Contact ATC when required for controlled airspace
                                                    • Follow all FAA regulations and local laws
                                                    • Weather data and airspace information may not be current
                                                    • Not for use in emergency operations
                                                """.trimIndent()
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showInstructions = false }) {
                                            Text("Got it")
                                        }
                                    }
                                )
                            }
                        }

                        // Manual input section
                        if (isManualMode) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = manualAirportCode,
                                onValueChange = { newCode ->
                                    // Force uppercase for all characters immediately
                                    val upperCode = newCode.uppercase()
                                    onManualAirportCodeChange(upperCode)
                                },
                                label = { Text("Enter Airport Code") },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val code = manualAirportCode.trim().uppercase()
                                        if (code.length in 3..4) {
                                            scope.launch {
                                                try {
                                                    isLoading.value = true
                                                    val weather = metarService.fetchMetar(code)
                                                    weatherData.value = weather
                                                    val airport = airportService.findAirport(code)
                                                    if (airport != null) {
                                                        nearestAirport.value = airport
                                                    } else if (weather.latitude != null && weather.longitude != null) {
                                                        nearestAirport.value = Airport(
                                                            icao = code,
                                                            name = "$code International Airport",
                                                            latitude = weather.latitude,
                                                            longitude = weather.longitude,
                                                            airspaceClass = "E",
                                                            airspaceFloor = 0,
                                                            airspaceCeiling = 1200
                                                        )
                                                    } else {
                                                        nearestAirport.value = null
                                                    }
                                                    preferencesManager.saveManualAirport(code)
                                                    errorMessage.value = null
                                                } catch (e: Exception) {
                                                    if (e.isComposeCancellation()) throw e
                                                    nearestAirport.value = null
                                                    errorMessage.value = "Airport not found: $code"
                                                } finally {
                                                    if (currentCoroutineContext().isActive) {
                                                        isLoading.value = false
                                                    }
                                                }
                                            }
                                        }
                                    }) {
                                        Icon(imageVector = Icons.Default.Search, contentDescription = "Fetch METAR")
                                    }
                                },
                                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val code = manualAirportCode.trim().uppercase()
                                        if (code.length in 3..4) {
                                            scope.launch {
                                                try {
                                                    isLoading.value = true
                                                    val weather = metarService.fetchMetar(code)
                                                    weatherData.value = weather
                                                    val airport = airportService.findAirport(code)
                                                    if (airport != null) {
                                                        nearestAirport.value = airport
                                                    } else if (weather.latitude != null && weather.longitude != null) {
                                                        nearestAirport.value = Airport(
                                                            icao = code,
                                                            name = "$code International Airport",
                                                            latitude = weather.latitude,
                                                            longitude = weather.longitude,
                                                            airspaceClass = "E",
                                                            airspaceFloor = 0,
                                                            airspaceCeiling = 1200
                                                        )
                                                    } else {
                                                        nearestAirport.value = null
                                                    }
                                                    preferencesManager.saveManualAirport(code)
                                                    errorMessage.value = null
                                                } catch (e: Exception) {
                                                    if (e.isComposeCancellation()) throw e
                                                    nearestAirport.value = null
                                                    errorMessage.value = "Airport not found: $code"
                                                } finally {
                                                    if (currentCoroutineContext().isActive) {
                                                        isLoading.value = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Airport Info Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (isManualMode) {
                                    val stationId = weatherData.value.stationId.ifBlank { manualAirportCode }
                                    val airport = nearestAirport.value
                                    val displayAirport = when {
                                        airport != null && airport.icao.equals(stationId, ignoreCase = true) -> airport
                                        airport != null && airport.icao.equals(manualAirportCode, ignoreCase = true) -> airport
                                        else -> null
                                    }

                                    if (displayAirport != null) {
                                        Text(
                                            "${displayAirport.name} (${displayAirport.icao})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Location: %.4f°N, %.4f°W".format(displayAirport.latitude, displayAirport.longitude),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (displayAirport.airspaceClass != "E" && displayAirport.airspaceClass != "G") {
                                            Text(
                                                "Class ${displayAirport.airspaceClass} Airspace",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = when (displayAirport.airspaceClass) {
                                                    "B" -> ClassBColor
                                                    "C" -> ClassCColor
                                                    "D" -> ClassDColor
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    } else if (stationId.isNotEmpty()) {
                                        Text(
                                            "${stationId.uppercase()} (Manual)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Location data unavailable",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    } else if (manualAirportCode.isNotEmpty()) {
                                        Text(
                                            "Airport not found: $manualAirportCode",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Red
                                        )
                                    }
                                } else {
                                    nearestAirport.value?.let { airport ->
                                        Text(
                                            "${airport.name} (${airport.icao})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Location: %.4f°N, %.4f°W".format(airport.latitude, airport.longitude),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (airport.airspaceClass != "E" && airport.airspaceClass != "G") {
                                            Text(
                                                "Class ${airport.airspaceClass} Airspace",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = when (airport.airspaceClass) {
                                                    "B" -> ClassBColor
                                                    "C" -> ClassCColor
                                                    "D" -> ClassDColor
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Weather cards in 2 columns
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 8.dp)
                        ) {
                            // Temperature card
                            item {
                                WeatherInfoCard(
                                    title = "Temperature",
                                    value = "%.1f°F / %.1f°C".format(
                                        weatherData.value.temperatureF,
                                        weatherData.value.temperature
                                    ),
                                    color = getColorForTemperature(weatherData.value.temperatureF)
                                )
                            }
                            // Dewpoint card
                            item {
                                WeatherInfoCard(
                                    title = "Dewpoint",
                                    value = "%.1f°F / %.1f°C".format(
                                        weatherData.value.dewPointF,
                                        weatherData.value.dewPoint
                                    ),
                                    color = getColorForSafety(weatherData.value.flightSafety)
                                )
                            }
                            // Humidity card
                            item {
                                val humidity = calculateRelativeHumidity(
                                    weatherData.value.temperature,
                                    weatherData.value.dewPoint
                                )
                                WeatherInfoCard(
                                    title = "Relative Humidity",
                                    value = "%.1f%%".format(humidity),
                                    color = getColorForHumidity(humidity)
                                )
                            }
                            // Flight Category card
                            item {
                                WeatherInfoCard(
                                    title = "Flight Category",
                                    value = weatherData.value.flightCategory.name,
                                    color = getColorForFlightCategory(weatherData.value.flightCategory.name)
                                )
                            }
                            // Wind card
                            item {
                                WeatherInfoCard(
                                    title = "Wind",
                                    value = if (weatherData.value.windGust > 0) {
                                        // METAR wind speeds are always in knots, convert to m/s
                                        val windSpeedKts = weatherData.value.windSpeed
                                        val windSpeedMps = (windSpeedKts / 1.94384).toInt()
                                        
                                        val gustKts = weatherData.value.windGust
                                        val gustMps = (gustKts / 1.94384).toInt()
                                        
                                        "${windSpeedKts}kt (${windSpeedMps}m/s) gusting ${gustKts}kt (${gustMps}m/s) from ${weatherData.value.windDirection}° (${degreesToCardinal(weatherData.value.windDirection)})"
                                    } else {
                                        // METAR wind speed is always in knots, convert to m/s
                                        val windSpeedKts = weatherData.value.windSpeed
                                        val windSpeedMps = (windSpeedKts / 1.94384).toInt()
                                        
                                        "${windSpeedKts}kt (${windSpeedMps}m/s) from ${weatherData.value.windDirection}° (${degreesToCardinal(weatherData.value.windDirection)})"
                                    },
                                    color = getColorForWind(weatherData.value.windSpeed, weatherData.value.windGust)
                                )
                            }
                            // Visibility card
                            item {
                                WeatherInfoCard(
                                    title = "Visibility",
                                    value = "${weatherData.value.visibility} miles / ${(weatherData.value.visibility * 1.60934).toInt()} km",
                                    color = getColorForVisibility(weatherData.value.visibility)
                                )
                            }
                            // Cloud Conditions card
                            item {
                                WeatherInfoCard(
                                    title = "Cloud Conditions",
                                    value = weatherData.value.cloudLayersText,
                                    color = getColorForCeiling(weatherData.value.cloudLayers)
                                )
                            }
                            // Maximum Flight Height card
                            item {
                                val ceilingHeight = weatherData.value.cloudLayers.find { it.coverage in listOf("BKN", "OVC") }?.heightFeet
                                val visibility = weatherData.value.visibility
                                val maxHeight = if (visibility < 3.0) {
                                    0
                                } else {
                                    maxOf(0, minOf(
                                        400,  // Part 107 max altitude
                                        ceilingHeight?.minus(500) ?: 400,  // 500ft buffer below clouds
                                        tfrs.fold(400) { acc, tfr ->  // Check TFR restrictions
                                            val tfrMin = tfr.minAltitude ?: 0
                                            if (tfrMin > 0) minOf(acc, tfrMin) else acc
                                        }
                                    ))
                                }
                                WeatherInfoCard(
                                    title = "Maximum Flight Height",
                                    value = "$maxHeight feet / ${(maxHeight * 0.3048).toInt()} meters AGL",
                                    color = when {
                                        maxHeight == 0 -> Color(0xFFF44336)  // Red for unsafe
                                        maxHeight < 200 -> Color(0xFFFFA726)  // Orange for marginal
                                        else -> Color(0xFF4CAF50)  // Green for safe
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            // Precipitation card
                            item {
                                WeatherInfoCard(
                                    title = "Precipitation",
                                    value = if (weatherData.value.precipitation.isNotEmpty()) {
                                        weatherData.value.precipitation.joinToString(", ")
                                    } else {
                                        "None"
                                    },
                                    color = if (weatherData.value.precipitation.isEmpty()) SafeGreen else UnsafeRed
                                )
                            }
                            
                            // KP Index card
                            item {
                                WeatherInfoCard(
                                    title = "KP Index",
                                    value = when {
                                        kpIndexError != null -> "Error: $kpIndexError"
                                        kpIndexData != null -> buildString {
                                            append("%.1f".format(kpIndexData?.kpIndex))
                                            append("\n")
                                            append(getKpDescription(kpIndexData?.kpIndex ?: 0.0))
                                        }
                                        else -> "Loading..."
                                    },
                                    color = getColorForKpIndex(kpIndexData?.kpIndex ?: 0.0)
                                )
                            }
                            
                            // Flight Conditions card (full width)
                            item(span = { GridItemSpan(2) }) {
                                val tempSafety = when {
                                    weatherData.value.temperatureF >= 20.0 -> "Safe"
                                    else -> "Marginal"  // Changed from potentially being "Unsafe"
                                }
                                val overallSafety = if (weatherData.value.flightSafety == FlightSafety.UNSAFE) {
                                    FlightSafety.UNSAFE
                                } else if (tempSafety == "Marginal" || weatherData.value.flightSafety == FlightSafety.MARGINAL) {
                                    FlightSafety.MARGINAL
                                } else {
                                    FlightSafety.SAFE
                                }
                                WeatherInfoCard(
                                    title = "Flight Conditions",
                                    value = overallSafety.toString(),
                                    color = getColorForSafety(overallSafety),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // Auto Flight Risk Analysis card (full width)
                            item(span = { GridItemSpan(2) }) {
                                FlightRiskCard(
                                    metarData = weatherData.value,
                                    tfrs = tfrs,
                                    kpIndexData = kpIndexData,
                                    gnssData = gnssData,
                                    airport = nearestAirport.value,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // GNSS Satellite Card (full width)
                            item(span = { GridItemSpan(2) }) {
                                GnssSatelliteCard(
                                    gnssData = gnssData,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            // Add MSL Altitude card
                            item(span = { GridItemSpan(2) }) {
                                currentLocation.value?.let { location ->
                                    WeatherInfoCard(
                                        title = "Current Elevation (MSL)",
                                        value = buildString {
                                            append(AltitudeCalculator.getAltitudeText(location))
                                            append("\n")
                                            append("Accuracy: ")
                                            append(AltitudeCalculator.getVerticalAccuracyText(location))
                                        },
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            // Sun times card (full width)
                            item(span = { GridItemSpan(2) }) {
                                WeatherInfoCard(
                                    title = "Sun Times (Current Location)",
                                    value = when {
                                        isSunTimesLoading -> "Loading sun times..."
                                        sunTimesError != null -> "Error: $sunTimesError"
                                        sunTimes != null -> buildString {
                                            if (sunTimesLocationNote.isNotBlank()) {
                                                append(sunTimesLocationNote)
                                                append("\n\n")
                                            }
                                            append("Sunrise: ${sunTimes?.sunrise}\n")
                                            append("Sunset: ${sunTimes?.sunset}\n")
                                            append("Civil Twilight Begin: ${sunTimes?.civilTwilightBegin}\n")
                                            append("Civil Twilight End: ${sunTimes?.civilTwilightEnd}")
                                        }
                                        !locationService.hasLocationPermission() ->
                                            "Enable location permission in Settings, or wait for airport data…"
                                        else -> "Waiting for GPS or airport coordinates…"
                                    },
                                    color = if (sunTimesError != null) UnsafeRed else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            // Raw METAR Data card (full width)
                            item(span = { GridItemSpan(2) }) {
                                val rawText = weatherData.value.rawText ?: "No METAR data available"
                                val timeText = try {
                                    val timeRegex = """(\d{2})(\d{2})(\d{2})Z""".toRegex()
                                    val match = timeRegex.find(rawText)
                                    if (match != null) {
                                        val (day, hour, minute) = match.destructured
                                        val now = LocalDateTime.now()
                                        val zuluTime = LocalDateTime.of(
                                            now.year,
                                            now.month,
                                            day.toInt(),
                                            hour.toInt(),
                                            minute.toInt()
                                        )
                                        val localTime = zuluTime.atZone(ZoneOffset.UTC)
                                            .withZoneSameInstant(ZoneId.systemDefault())
                                            .format(DateTimeFormatter.ofPattern("h:mm a z"))
                                        "\n\nLast Updated: $localTime"
                                    } else {
                                        "\n\nLast Updated: Unknown"
                                    }
                                } catch (e: Exception) {
                                    "\n\nLast Updated: Error parsing time"
                                }

                                WeatherInfoCard(
                                    title = "Raw METAR Data",
                                    value = rawText + timeText,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // TFR search radius
                            item(span = { GridItemSpan(2) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "TFR search radius (local TFRs; VIP always shown US-wide)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TfrService.TFR_RADIUS_OPTIONS_NM.chunked(3).forEach { rowOptions ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowOptions.forEach { option ->
                                                FilterChip(
                                                    selected = tfrRadiusNm == option,
                                                    onClick = {
                                                        tfrRadiusNm = option
                                                        tfrRadiusToSave = option
                                                    },
                                                    label = { Text("${option} nm") },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            // Keep row alignment when a chunk has fewer than 3 chips
                                            repeat(3 - rowOptions.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    if (tfrs.isNotEmpty()) {
                                        val lat = tfrSearchCenter?.latitude
                                        val lon = tfrSearchCenter?.longitude
                                        val nearbyCount = if (lat != null && lon != null) {
                                            tfrs.count { tfr ->
                                                tfrService.isWithinRadius(tfr, lat, lon, tfrRadiusNm.toDouble())
                                            }
                                        } else {
                                            tfrs.size
                                        }
                                        val vipNationwideCount = tfrs.count { tfr ->
                                            TfrService.isVipNationwideTfr(tfr) && (lat == null || lon == null ||
                                                !tfrService.isWithinRadius(tfr, lat, lon, tfrRadiusNm.toDouble()))
                                        }
                                        val countText = if (vipNationwideCount > 0) {
                                            "$nearbyCount within ${tfrRadiusNm} nm · $vipNationwideCount VIP US-wide"
                                        } else {
                                            "$nearbyCount within ${tfrRadiusNm} nm"
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = countText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // TFR Map Button (full width)
                            item(span = { GridItemSpan(2) }) {
                                Button(
                                    onClick = { showMap = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = "Show Map")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("View TFR Map")
                                }
                            }

                            // 24-Hour Forecast Button (full width)
                            item(span = { GridItemSpan(2) }) {
                                Button(
                                    onClick = { show24HourForecast = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Cloud, contentDescription = "Show 24-Hour Forecast")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("View 24-Hour Forecast")
                                }
                            }

                            // Airspace Map Button (full width)
                            item(span = { GridItemSpan(2) }) {
                                Button(
                                    onClick = { showAirspaceMap = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = "Show Airspace Map")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("View Airspace Map, Weather Radar & Aircraft")
                                }
                            }

                            // Checklist Button (full width)
                            item(span = { GridItemSpan(2) }) {
                                Button(
                                    onClick = { showChecklist = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Show Checklist")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pre-Flight Checklist")
                                }
                            }

                            // Back Button (full width)
                            item(span = { GridItemSpan(2) }) {
                                Button(
                                    onClick = onBackClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Back to Settings")
                                }
                            }
                        }

                        // Add TFR Monitor
                        TFRMonitor(
                            currentLocation = currentLocation.value,
                            tfrService = tfrService,
                            radiusNm = tfrRadiusNm,
                            onTFRWarning = { message ->
                                Log.d("TFR", "TFR Warning: $message")
                            }
                        )

                        // Icing Warning Dialog
                        if (showIcingWarning) {
                            AlertDialog(
                                onDismissRequest = { showIcingWarning = false },
                                title = { 
                                    Text(
                                        "⚠️ Icing Warning",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                text = {
                                    Text(
                                        "Prop Icing May Occur\nUse Caution!",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = { showIcingWarning = false }) {
                                        Text("Close")
                                    }
                                }
                            )
                        }

                        if (isLoading.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionSection(title: String, content: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun DroneChecklistScreen(onBackClick: () -> Unit) {
    var checkedItems by remember { mutableStateOf(List(15) { false }) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    "Pre-Flight Checklist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                // Empty box for alignment
                Box(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Checklist items in a scrollable column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                ChecklistSection(
                    title = "Weather & Environment",
                    items = listOf(
                        "Check weather conditions and METAR data",
                        "Verify wind speed and direction",
                        "Check for TFRs in the area",
                        "Verify airspace class and restrictions",
                        "Check visibility and cloud clearance"
                    ),
                    checkedItems = checkedItems,
                    onCheckedChange = { index, checked ->
                        checkedItems = checkedItems.toMutableList().apply {
                            this[index] = checked
                        }
                    },
                    startIndex = 0
                )

                ChecklistSection(
                    title = "Drone Equipment",
                    items = listOf(
                        "Inspect drone frame and props for damage",
                        "Check battery levels and condition",
                        "Verify camera/payload is secure",
                        "Test control surfaces and motors",
                        "Calibrate compass if needed"
                    ),
                    checkedItems = checkedItems,
                    onCheckedChange = { index, checked ->
                        checkedItems = checkedItems.toMutableList().apply {
                            this[index] = checked
                        }
                    },
                    startIndex = 5
                )

                ChecklistSection(
                    title = "Documentation & Safety",
                    items = listOf(
                        "Part 107 certificate on hand",
                        "Registration number on drone",
                        "Check for nearby airports/heliports",
                        "First aid kit available",
                        "Emergency procedures reviewed"
                    ),
                    checkedItems = checkedItems,
                    onCheckedChange = { index, checked ->
                        checkedItems = checkedItems.toMutableList().apply {
                            this[index] = checked
                        }
                    },
                    startIndex = 10
                )
            }

            // Reset button at the bottom
            Button(
                onClick = { checkedItems = List(15) { false } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text("Reset Checklist")
            }
        }
    }
}

@Composable
private fun ChecklistSection(
    title: String,
    items: List<String>,
    checkedItems: List<Boolean>,
    onCheckedChange: (Int, Boolean) -> Unit,
    startIndex: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checkedItems[startIndex + index],
                    onCheckedChange = { checked ->
                        onCheckedChange(startIndex + index, checked)
                    }
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun isLocationInControlledAirspace(
    location: Location?,
    airport: Airport
): Boolean {
    if (location == null) return false
    
    val radiusMeters = when (airport.airspaceClass) {
        "B" -> 30000.0  // 30km radius for Class B
        "C" -> 20000.0  // 20km radius for Class C
        "D" -> 8000.0   // 8km radius for Class D
        else -> 0.0
    }
    
    if (radiusMeters == 0.0) return false

    val results = FloatArray(1)
    Location.distanceBetween(
        location.latitude, location.longitude,
        airport.latitude, airport.longitude,
        results
    )
    
    return results[0] <= radiusMeters
}

private fun getColorForTemperature(temperatureF: Double): Color {
    return when {
        temperatureF >= 30.0 -> SafeGreen
        temperatureF >= 20.0 -> MarginalOrange
        else -> UnsafeRed
    }
}

@Composable
private fun getColorForKpIndex(kpIndex: Double): Color {
    return when {
        kpIndex <= 3.0 -> SafeGreen
        kpIndex <= 5.0 -> MarginalOrange
        else -> UnsafeRed
    }
}

private fun getKpDescription(kpIndex: Double): String {
    return when {
        kpIndex <= 2.0 -> "Low Geomagnetic Activity"
        kpIndex <= 3.0 -> "Quiet Geomagnetic Conditions"
        kpIndex <= 5.0 -> "Minor Geomagnetic Disturbance"
        kpIndex <= 7.0 -> "Strong Geomagnetic Storm"
        else -> "Severe Geomagnetic Storm"
    }
}

// Add this function after the other utility functions and before the MainActivity class
private fun calculateRelativeHumidity(tempC: Double, dewPointC: Double): Double {
    // Magnus formula constants for water vapor
    val a = 17.27
    val b = 237.7

    // Calculate vapor pressure and saturated vapor pressure
    val actualVaporPressure = 6.112 * Math.exp((a * dewPointC) / (b + dewPointC))
    val saturatedVaporPressure = 6.112 * Math.exp((a * tempC) / (b + tempC))

    // Calculate relative humidity (as a percentage)
    return (actualVaporPressure / saturatedVaporPressure) * 100.0
}

private fun getColorForHumidity(humidity: Double): Color {
    return when {
        humidity <= 60.0 -> SafeGreen    // Low humidity - good conditions
        humidity <= 80.0 -> MarginalOrange  // Moderate humidity - watch for condensation
        else -> UnsafeRed     // High humidity - risk of condensation and reduced visibility
    }
}

// Aircraft tracking data models
data class AircraftState(
    val icao24: String,
    val callsign: String?,
    val originCountry: String,
    val timePosition: Long?,
    val lastContact: Long,
    val longitude: Double?,
    val latitude: Double?,
    val baroAltitude: Double?,
    val onGround: Boolean,
    val velocity: Double?,
    val trueTrack: Double?,
    val verticalRate: Double?,
    val sensors: List<Int>?,
    val geoAltitude: Double?,
    val squawk: String?,
    val spi: Boolean,
    val positionSource: Int
)

data class AircraftResponse(
    val time: Int,
    val states: List<List<Any?>>
)

// Function to create rotated airplane icon
fun createRotatedAirplaneIcon(context: android.content.Context, heading: Double?): BitmapDescriptor? {
    if (heading == null) return BitmapDescriptorFactory.fromResource(R.drawable.plane)
    
    try {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.plane)
        if (drawable == null) return BitmapDescriptorFactory.fromResource(R.drawable.plane)
        
        // Create a smaller size for the icon (reduce to 60% of original size)
        val scaleFactor = 0.6f
        val scaledWidth = (drawable.intrinsicWidth * scaleFactor).toInt()
        val scaledHeight = (drawable.intrinsicHeight * scaleFactor).toInt()
        
        val bitmap = Bitmap.createBitmap(
            scaledWidth,
            scaledHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, scaledWidth, scaledHeight)
        drawable.draw(canvas)
        
        // Rotate the bitmap based on heading with offset adjustment
        val matrix = Matrix()
        // Subtract 45 degrees offset to align airplane nose with heading direction
        val adjustedHeading = heading.toFloat() - 45f
        matrix.postRotate(adjustedHeading, scaledWidth / 2f, scaledHeight / 2f)
        
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        
        return BitmapDescriptorFactory.fromBitmap(rotatedBitmap)
    } catch (e: Exception) {
        Log.e("AircraftIcon", "Error creating rotated airplane icon", e)
        return BitmapDescriptorFactory.fromResource(R.drawable.plane)
    }
}

// Aircraft API service
class AircraftService {
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0
    private var consecutiveEmptyResponses = 0
    private var lastSuccessfulResponse = 0L
    
    private suspend fun getAccessToken(): String? {
        // Check if we have a valid token
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            val timeLeft = (tokenExpiry - System.currentTimeMillis()) / 1000
            Log.d("AircraftService", "Using cached token, expires in ${timeLeft}s")
            return accessToken
        }
        
        Log.d("AircraftService", "Token expired or missing, refreshing...")
        
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                
                val postData = "grant_type=client_credentials&client_id=mevenson_1@charter.net-api-client&client_secret=a7dMRFbrek2BWLgV4cvhbkKb7VIWh2NF"
                connection.outputStream.use { it.write(postData.toByteArray()) }
                
                val responseCode = connection.responseCode
                Log.d("AircraftService", "Token request response code: $responseCode")
                Log.e("AircraftService", "TOKEN REQUEST CODE: $responseCode") // Error level for visibility
                
                // Log token request headers for debugging
                val tokenHeaders = connection.headerFields
                Log.d("AircraftService", "Token request headers: $tokenHeaders")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (bufferedReader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    
                    bufferedReader.close()
                    inputStream.close()
                    
                    val jsonResponse = response.toString()
                    Log.d("AircraftService", "Token response: $jsonResponse")
                    
                    val jsonObject = org.json.JSONObject(jsonResponse)
                    val token = jsonObject.optString("access_token")
                    val expiresIn = jsonObject.optInt("expires_in", 3600) // Default 1 hour
                    
                    if (token.isNotEmpty()) {
                        accessToken = token
                        tokenExpiry = System.currentTimeMillis() + (expiresIn * 1000L) - 60000 // 1 minute buffer
                        Log.d("AircraftService", "Got access token, expires in ${expiresIn}s")
                        Log.e("AircraftService", "TOKEN SUCCESS: Got access token") // Error level for visibility
                        token
                    } else {
                        Log.e("AircraftService", "No access token in response")
                        null
                    }
                } else {
                    Log.e("AircraftService", "Token request failed: $responseCode")
                    // Log error response for debugging
                    try {
                        val errorStream = connection.errorStream
                        if (errorStream != null) {
                            val errorReader = BufferedReader(InputStreamReader(errorStream))
                            val errorResponse = StringBuilder()
                            var line: String?
                            while (errorReader.readLine().also { line = it } != null) {
                                errorResponse.append(line)
                            }
                            errorReader.close()
                            Log.e("AircraftService", "Token error response: ${errorResponse.toString()}")
                        }
                    } catch (ex: Exception) {
                        Log.e("AircraftService", "Error reading token error response", ex)
                    }
                    null
                }
            } catch (e: Exception) {
                Log.e("AircraftService", "Error getting access token", e)
                null
            }
        }
    }
    
    suspend fun getAircraftStates(): Result<AircraftResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = getAccessToken()
                Log.d("AircraftService", "Token status: ${if (token != null) "Valid" else "Invalid/Expired"}")
                
                val url = URL("https://opensky-network.org/api/states/all")
                Log.d("AircraftService", "Making request to: $url")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "FlySafeWeather/1.0")
                
                // Try authenticated request first if we have a token
                if (token != null) {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    Log.d("AircraftService", "Using authenticated request")
                } else {
                    Log.d("AircraftService", "Using anonymous request (no token)")
                }
                
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                
                val responseCode = connection.responseCode
                Log.d("AircraftService", "HTTP response code: $responseCode")
                Log.e("AircraftService", "API RESPONSE CODE: $responseCode") // Error level for visibility
                
                // Log all response headers for debugging
                val headers = connection.headerFields
                Log.d("AircraftService", "Response headers: $headers")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (bufferedReader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    
                    bufferedReader.close()
                    inputStream.close()
                    
                    val jsonString = response.toString()
                    Log.d("AircraftService", "Received JSON response length: ${jsonString.length}")
                    Log.d("AircraftService", "Response preview: ${jsonString.take(500)}")
                    
                    // Check if response is empty or contains rate limit info
                    if (jsonString.length < 100) {
                        Log.e("AircraftService", "SUSPICIOUS SHORT RESPONSE: $jsonString")
                    }
                    
                    // Check for common error patterns in response
                    if (jsonString.contains("rate limit") || jsonString.contains("quota") || jsonString.contains("limit")) {
                        Log.e("AircraftService", "RATE LIMIT DETECTED IN RESPONSE: $jsonString")
                    }
                    
                    // Log full response when we get 0 aircraft to debug
                    if (jsonString.length < 1000) {
                        Log.d("AircraftService", "FULL RESPONSE (short): $jsonString")
                    } else {
                        Log.d("AircraftService", "FULL RESPONSE (long): ${jsonString.take(2000)}")
                    }
                    
                    val parsedResponse = parseAircraftResponse(jsonString)
                    Log.d("AircraftService", "Parsed ${parsedResponse.states.size} aircraft states")
                    Log.e("AircraftService", "PARSED AIRCRAFT COUNT: ${parsedResponse.states.size}") // Error level for visibility
                    
                    // Track consecutive empty responses
                    if (parsedResponse.states.isEmpty()) {
                        consecutiveEmptyResponses++
                        Log.w("AircraftService", "Empty response #$consecutiveEmptyResponses")
                        
                        // If we've had 3+ consecutive empty responses, try anonymous request
                        if (consecutiveEmptyResponses >= 3 && token != null) {
                            Log.w("AircraftService", "Multiple empty responses, trying anonymous request")
                            return@withContext tryAnonymousRequest()
                        }
                    } else {
                        consecutiveEmptyResponses = 0
                        lastSuccessfulResponse = System.currentTimeMillis()
                    }
                    
                    // Log rate limit headers if available
                    val rateLimitRemaining = connection.getHeaderField("X-Rate-Limit-Remaining")
                    val rateLimitReset = connection.getHeaderField("X-Rate-Limit-Reset")
                    if (rateLimitRemaining != null) {
                        Log.d("AircraftService", "Rate limit remaining: $rateLimitRemaining")
                    }
                    if (rateLimitReset != null) {
                        Log.d("AircraftService", "Rate limit reset: $rateLimitReset")
                    }
                    Result.success(parsedResponse)
                } else {
                    Log.e("AircraftService", "HTTP error: $responseCode")
                    // Log error response for debugging
                    try {
                        val errorStream = connection.errorStream
                        if (errorStream != null) {
                            val errorReader = BufferedReader(InputStreamReader(errorStream))
                            val errorResponse = StringBuilder()
                            var line: String?
                            while (errorReader.readLine().also { line = it } != null) {
                                errorResponse.append(line)
                            }
                            errorReader.close()
                            Log.e("AircraftService", "Error response: ${errorResponse.toString()}")
                        }
                    } catch (ex: Exception) {
                        Log.e("AircraftService", "Error reading error response", ex)
                    }
                    
                    // If authenticated request failed and we have a token, try anonymous request
                    if (token != null && (responseCode == 401 || responseCode == 403)) {
                        Log.d("AircraftService", "Authenticated request failed, trying anonymous request")
                        return@withContext tryAnonymousRequest()
                    }
                    
                    Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                Log.e("AircraftService", "Failed to fetch aircraft data", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun tryAnonymousRequest(): Result<AircraftResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AircraftService", "Attempting anonymous request to OpenSky API")
                val url = URL("https://opensky-network.org/api/states/all")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "FlySafeWeather/1.0")
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                
                val responseCode = connection.responseCode
                Log.d("AircraftService", "Anonymous request response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (bufferedReader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    
                    bufferedReader.close()
                    inputStream.close()
                    
                    val jsonString = response.toString()
                    Log.d("AircraftService", "Anonymous response length: ${jsonString.length}")
                    
                    val parsedResponse = parseAircraftResponse(jsonString)
                    Log.d("AircraftService", "Anonymous request parsed ${parsedResponse.states.size} aircraft states")
                    Result.success(parsedResponse)
                } else {
                    Log.e("AircraftService", "Anonymous request also failed: $responseCode")
                    Result.failure(Exception("Anonymous request failed: HTTP $responseCode"))
                }
            } catch (e: Exception) {
                Log.e("AircraftService", "Anonymous request exception", e)
                Result.failure(e)
            }
        }
    }
    
    // Test function to verify OpenSky API connectivity
    suspend fun testOpenSkyAPI(): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AircraftService", "Testing OpenSky API connectivity...")
                val url = URL("https://opensky-network.org/api/states/all")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "FlySafeWeather/1.0")
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                
                val responseCode = connection.responseCode
                Log.d("AircraftService", "Test API response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }
                    inputStream.close()
                    
                    val jsonObject = org.json.JSONObject(response)
                    
                    // Handle case where states field might be null
                    val aircraftCount = if (jsonObject.isNull("states")) {
                        Log.w("AircraftService", "Test API: States field is null")
                        0
                    } else {
                        val statesArray = jsonObject.getJSONArray("states")
                        statesArray.length()
                    }
                    
                    Log.d("AircraftService", "Test API returned $aircraftCount aircraft")
                    return@withContext "SUCCESS: $aircraftCount aircraft found"
                } else {
                    return@withContext "ERROR: HTTP $responseCode"
                }
            } catch (e: Exception) {
                Log.e("AircraftService", "Test API failed", e)
                return@withContext "EXCEPTION: ${e.message}"
            }
        }
    }
    
    private fun parseAircraftResponse(jsonString: String): AircraftResponse {
        try {
            val jsonObject = JSONObject(jsonString)
            val time = jsonObject.getInt("time")
            
            // Handle case where states field might be null
            val statesArray = if (jsonObject.isNull("states")) {
                Log.w("AircraftService", "States field is null in API response")
                org.json.JSONArray() // Return empty array
            } else {
                jsonObject.getJSONArray("states")
            }
            
            val states = mutableListOf<List<Any?>>()
            for (i in 0 until statesArray.length()) {
                val stateArray = statesArray.getJSONArray(i)
                val stateList = mutableListOf<Any?>()
                for (j in 0 until stateArray.length()) {
                    stateList.add(stateArray.get(j))
                }
                states.add(stateList)
            }
            
            Log.d("AircraftService", "Parsed ${states.size} aircraft states from OpenSky API")
            return AircraftResponse(time, states)
        } catch (e: Exception) {
            Log.e("AircraftService", "Failed to parse aircraft response", e)
            Log.e("AircraftService", "JSON response: $jsonString")
            return AircraftResponse(0, emptyList())
        }
    }
    
    fun parseAircraftStates(states: List<List<Any?>>): List<AircraftState> {
        return states.mapNotNull { stateList ->
            try {
                if (stateList.size >= 17) {
                    val icao24 = stateList[0] as? String
                    val latitude = (stateList[6] as? Number)?.toDouble()
                    val longitude = (stateList[5] as? Number)?.toDouble()
                    
                    // Only include aircraft with valid ICAO24 and position data
                    if (!icao24.isNullOrBlank() && latitude != null && longitude != null) {
                        AircraftState(
                            icao24 = icao24,
                            callsign = stateList[1] as? String,
                            originCountry = stateList[2] as? String ?: "",
                            timePosition = (stateList[3] as? Number)?.toLong(),
                            lastContact = (stateList[4] as? Number)?.toLong() ?: 0L,
                            longitude = longitude,
                            latitude = latitude,
                            baroAltitude = (stateList[7] as? Number)?.toDouble(),
                            onGround = stateList[8] as? Boolean ?: false,
                            velocity = (stateList[9] as? Number)?.toDouble(),
                            trueTrack = (stateList[10] as? Number)?.toDouble(),
                            verticalRate = (stateList[11] as? Number)?.toDouble(),
                            sensors = (stateList[12] as? List<*>)?.mapNotNull { it as? Int },
                            geoAltitude = (stateList[13] as? Number)?.toDouble(),
                            squawk = stateList[14] as? String,
                            spi = stateList[15] as? Boolean ?: false,
                            positionSource = (stateList[16] as? Number)?.toInt() ?: 0
                        )
                    } else null
                } else null
            } catch (e: Exception) {
                Log.e("AircraftService", "Error parsing aircraft state", e)
                null
            }
        }
    }
    
    fun filterAircraftNearLocation(
        aircraft: List<AircraftState>, 
        userLat: Double, 
        userLon: Double, 
        radiusMiles: Double = 15.0
    ): List<AircraftState> {
        Log.d("AircraftService", "Filtering ${aircraft.size} aircraft near location $userLat, $userLon within $radiusMiles miles")
        
        val filtered = aircraft.filterIndexed { index, aircraft ->
            val hasValidPosition = aircraft.latitude != null && aircraft.longitude != null
            val isInFlight = !aircraft.onGround
            val distance = if (hasValidPosition) {
                calculateDistance(userLat, userLon, aircraft.latitude!!, aircraft.longitude!!)
            } else {
                Double.MAX_VALUE
            }
            val isWithinRadius = distance <= radiusMiles
            
            // Reduced logging to prevent memory issues - only log first 5 aircraft
            if (index < 5) {
                Log.d("AircraftService", "Aircraft ${aircraft.callsign ?: aircraft.icao24}: pos=$hasValidPosition, flight=$isInFlight, distance=${if (hasValidPosition) String.format("%.1f", distance) else "N/A"}mi, within=$isWithinRadius")
            }
            
            hasValidPosition && isInFlight && isWithinRadius
        }
        
        Log.d("AircraftService", "Filtered to ${filtered.size} aircraft within radius")
        return filtered
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 3959.0 // Earth radius in miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}

/** Checks GitHub for a newer release on app open and offers to download + install it. */
@Composable
private fun UpdatePromptDialog() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    val updatePromptStore = remember { UpdatePromptStore(context) }

    LaunchedEffect(Unit) {
        val installedCode = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        }.getOrDefault(0)
        val update = withContext(Dispatchers.IO) {
            AppUpdateChecker.check(installedCode)
        } ?: return@LaunchedEffect
        if (updatePromptStore.shouldPrompt(update.versionCode)) {
            availableUpdate = update
        }
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = {
                if (updateBusy) return@AlertDialog
                updatePromptStore.snooze(update.versionCode)
                availableUpdate = null
                updateError = null
            },
            title = { Text("New version available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (updateBusy) {
                            "Downloading FlySafe Weather ${update.versionName}…"
                        } else {
                            "FlySafe Weather ${update.versionName} is ready. " +
                                "Tap Update to download and open the installer."
                        },
                    )
                    updateError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error)
                    }
                    if (updateBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !updateBusy,
                    onClick = {
                        updateError = null
                        if (AppUpdateInstaller.needsInstallPermission(context)) {
                            updateError =
                                "Allow installs from FlySafe Weather in the next screen, then tap Update again."
                            AppUpdateInstaller.openInstallPermissionSettings(context)
                            return@TextButton
                        }
                        updateBusy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                AppUpdateInstaller.downloadAndInstall(context, update.apkUrl)
                            }
                            updateBusy = false
                            when (result) {
                                is AppUpdateInstaller.Result.LaunchedInstaller -> {
                                    availableUpdate = null
                                }
                                is AppUpdateInstaller.Result.NeedsInstallPermission -> {
                                    updateError =
                                        "Allow installs from FlySafe Weather, then tap Update again."
                                    AppUpdateInstaller.openInstallPermissionSettings(context)
                                }
                                is AppUpdateInstaller.Result.Failed -> {
                                    updateError = result.message
                                }
                            }
                        }
                    },
                ) {
                    Text(if (updateBusy) "Downloading…" else "Update")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !updateBusy,
                    onClick = {
                        updatePromptStore.snooze(update.versionCode)
                        availableUpdate = null
                        updateError = null
                    },
                ) {
                    Text("Not now")
                }
            },
        )
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var locationService: LocationService
    private lateinit var metarService: MetarService
    private lateinit var airportService: AirportService
    private lateinit var tfrService: TfrService
    private lateinit var aircraftService: AircraftService
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var kpIndexService: KpIndexService
    private lateinit var gnssService: GnssService
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var weatherCache: WeatherCache
    private lateinit var networkConnectivity: NetworkConnectivity
    private var showLegalScreen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var keepSystemSplash = true
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSystemSplash }
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(280L)
                .withEndAction { provider.remove() }
                .start()
        }
        
        // Initialize all services
        locationService = LocationService(this)
        metarService = MetarService(this)
        airportService = AirportService(this)
        aircraftService = AircraftService()
        tfrService = TfrService(
            context = this,
            clientId = BuildConfig.FAA_CLIENT_ID,
            clientSecret = BuildConfig.FAA_CLIENT_SECRET
        )
        preferencesManager = PreferencesManager(this)
        kpIndexService = KpIndexService(this)
        gnssService = GnssService(this)
        weatherCache = WeatherCache(this)
        networkConnectivity = NetworkConnectivity(this)

        // Request necessary permissions
        requestLocationPermissions()

        setContent {
            val isOnline by networkConnectivity.isNetworkAvailable.collectAsState(initial = true)
            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                // Hand off from the system splash to the full-screen splash artwork.
                keepSystemSplash = false
                delay(SPLASH_HOLD_MS)
                showSplash = false
            }

            DoorCountyDroneWeatherAppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (showLegalScreen) {
                        LegalScreen(onClose = { showLegalScreen = false })
                    } else {
                        Column {
                            // Offline mode indicator
                            if (!isOnline) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Offline Mode - Using cached data",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Main content
                            MainScreen(
                                metarService = metarService,
                                locationService = locationService,
                                airportService = airportService,
                                tfrService = tfrService,
                                preferencesManager = preferencesManager,
                                kpIndexService = kpIndexService,
                                gnssService = gnssService,
                                weatherCache = weatherCache,
                                isOnline = isOnline,
                                onShowLegal = { showLegalScreen = true }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showSplash,
                        enter = EnterTransition.None,
                        exit = fadeOut(animationSpec = tween(SPLASH_FADE_MS)),
                    ) {
                        SplashScreen()
                    }

                    if (!showSplash) {
                        UpdatePromptDialog()
                    }
                }
            }
        }
    }

    // Add to existing functions
    private suspend fun fetchAndCacheData(
        latitude: Double,
        longitude: Double,
        airportCode: String
    ) {
        try {
            if (networkConnectivity.isCurrentlyConnected()) {
                // Try to fetch new data
                metarService.fetchMetar(airportCode)?.let { metar ->
                    weatherCache.cacheMetarData(metar)
                }
                
                val tfrRadius = preferencesManager.tfrRadiusNm.first()
                tfrService.fetchTfrs(latitude, longitude, radiusNm = tfrRadius)?.let { tfrs ->
                    weatherCache.cacheTfrData(tfrs)
                }
                
                kpIndexService.fetchKpIndex()?.let { kpIndex ->
                    weatherCache.cacheKpIndexData(kpIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error fetching data", e)
        }
    }

    private suspend fun getCachedOrFetchData(
        latitude: Double,
        longitude: Double,
        airportCode: String
    ): Triple<MetarData?, List<TfrData>?, KpIndexData?> {
        return if (networkConnectivity.isCurrentlyConnected()) {
            try {
                val metar = metarService.fetchMetar(airportCode)
                val tfrRadius = preferencesManager.tfrRadiusNm.first()
                val tfrs = tfrService.fetchTfrs(latitude, longitude, radiusNm = tfrRadius)
                val kpIndex = kpIndexService.fetchKpIndex()

                // Cache the new data
                metar?.let { weatherCache.cacheMetarData(it) }
                tfrs?.let { weatherCache.cacheTfrData(it) }
                kpIndex?.let { weatherCache.cacheKpIndexData(it) }

                Triple(metar, tfrs, kpIndex)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching data, using cache", e)
                Triple(
                    weatherCache.getCachedMetarData(),
                    weatherCache.getCachedTfrData(),
                    weatherCache.getCachedKpIndexData()
                )
            }
        } else {
            // Offline mode - use cached data
            Triple(
                weatherCache.getCachedMetarData(),
                weatherCache.getCachedTfrData(),
                weatherCache.getCachedKpIndexData()
            )
        }
    }

    private fun requestLocationPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        Log.d("MainActivity", "Checking location permissions")
        if (!hasPermissions(permissions)) {
            Log.d("MainActivity", "Requesting location permissions")
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            Log.d("MainActivity", "Location permissions already granted")
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("MainActivity", "Location permissions granted, reinitializing GNSS service")
                gnssService = GnssService(this)
                lifecycleScope.launch {
                    try {
                        locationService.getCurrentLocation()?.let { loc ->
                            Log.d("MainActivity", "GPS after permission grant: ${loc.latitude}, ${loc.longitude}")
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "GPS fetch after permission failed", e)
                    }
                }
            } else {
                Log.w("MainActivity", "Location permissions denied - GNSS features will not work")
            }
        }
    }

    override fun onDestroy() {
        Log.d("MainActivity", "Activity being destroyed, cleaning up GNSS service")
        super.onDestroy()
        gnssService.cleanup()
    }
}
