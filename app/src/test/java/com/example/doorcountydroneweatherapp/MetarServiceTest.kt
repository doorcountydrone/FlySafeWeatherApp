package com.flysafeweather.app

import android.content.Context
import android.util.Log
import com.flysafeweather.app.data.MetarData
import com.flysafeweather.app.data.MetarService
import com.flysafeweather.app.data.CloudLayer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

class MetarServiceTest {
    @MockK
    private lateinit var context: Context
    private lateinit var metarService: MetarService
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Mock Android Log class with specific parameter types
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        
        // Create MetarService with mock server URL and ScalarsConverterFactory
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            
        metarService = MetarService(context, retrofit)
        
        // Mock context behavior
        every { context.assets.open(any()) } returns ByteArrayInputStream(ByteArray(0))
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `test successful METAR parsing`() = runTest {
        // Sample METAR XML response
        val sampleMetarXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
                <data>
                    <METAR>
                        <station_id>KSUE</station_id>
                        <temp_c>20.0</temp_c>
                        <dewpoint_c>15.0</dewpoint_c>
                        <wind_speed_kt>10</wind_speed_kt>
                        <wind_gust_kt>15</wind_gust_kt>
                        <wind_dir_degrees>180</wind_dir_degrees>
                        <visibility_statute_mi>10.0</visibility_statute_mi>
                        <altim_in_hg>29.92</altim_in_hg>
                        <sky_condition sky_cover="BKN" cloud_base_ft_agl="3000"/>
                        <raw_text>KSUE 151453Z AUTO 18010G15KT 10SM BKN030 20/15 A2992</raw_text>
                    </METAR>
                </data>
            </response>
        """.trimIndent()

        // Mock the HTTP response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(sampleMetarXml)
        )

        // Test METAR fetch
        val result = metarService.fetchMetar("KSUE")

        // Verify the parsed data
        assertEquals("KSUE", result.stationId)
        assertEquals(20.0, result.temperature, 0.01)
        assertEquals(15.0, result.dewPoint, 0.01)
        assertEquals(10, result.windSpeed)
        assertEquals(15, result.windGust)
        assertEquals(180, result.windDirection)
        assertEquals(10.0, result.visibility, 0.01)
        assertEquals(29.92, result.altimeter, 0.01)
        assertTrue(result.cloudLayers.any { it.coverage == "BKN" && it.heightFeet == 3000 })
    }

    @Test
    fun `test METAR parsing with clear skies`() = runTest {
        val clearSkiesMetarXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
                <data>
                    <METAR>
                        <station_id>KSUE</station_id>
                        <temp_c>20.0</temp_c>
                        <dewpoint_c>15.0</dewpoint_c>
                        <wind_speed_kt>5</wind_speed_kt>
                        <wind_dir_degrees>180</wind_dir_degrees>
                        <visibility_statute_mi>10.0</visibility_statute_mi>
                        <altim_in_hg>29.92</altim_in_hg>
                        <sky_condition sky_cover="CLR"/>
                        <raw_text>KSUE 151453Z AUTO 18005KT 10SM CLR 20/15 A2992</raw_text>
                    </METAR>
                </data>
            </response>
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(clearSkiesMetarXml)
        )

        val result = metarService.fetchMetar("KSUE")

        assertEquals("KSUE", result.stationId)
        assertTrue(result.cloudLayers.any { it.coverage == "CLR" })
        assertEquals(1, result.cloudLayers.size)
    }

    @Test
    fun `test METAR parsing with precipitation`() = runTest {
        val rainMetarXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
                <data>
                    <METAR>
                        <station_id>KSUE</station_id>
                        <temp_c>15.0</temp_c>
                        <dewpoint_c>14.0</dewpoint_c>
                        <wind_speed_kt>10</wind_speed_kt>
                        <wind_dir_degrees>180</wind_dir_degrees>
                        <visibility_statute_mi>5.0</visibility_statute_mi>
                        <altim_in_hg>29.92</altim_in_hg>
                        <sky_condition sky_cover="OVC" cloud_base_ft_agl="1500"/>
                        <raw_text>KSUE 151453Z AUTO 18010KT 5SM -RA OVC015 15/14 A2992</raw_text>
                    </METAR>
                </data>
            </response>
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(rainMetarXml)
        )

        val result = metarService.fetchMetar("KSUE")

        assertEquals("KSUE", result.stationId)
        assertEquals(5.0, result.visibility, 0.01)
        assertTrue(result.cloudLayers.any { it.coverage == "OVC" && it.heightFeet == 1500 })
        assertTrue(result.rawText.contains("-RA"))
    }

    @Test
    fun `test error handling with invalid response`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val result = metarService.fetchMetar("KSUE")

        // Verify default values are used
        assertEquals("KSUE", result.stationId)
        assertEquals(0, result.windSpeed)
        assertEquals(10.0, result.visibility, 0.01)
        assertTrue(result.cloudLayers.any { it.coverage == "CLR" })
    }
} 
