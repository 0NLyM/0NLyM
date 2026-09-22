package it.mattia.controlx2face.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Best-effort current temperature for the Dot Matrix face's weather widget. There is no system
 * weather data source a programmatic androidx.wear.watchface face can bind to (that only exists
 * as a [WEATHER.*] expression in the declarative Watch Face Format), so this fetches it directly
 * instead: IP-based geolocation -- no location permission needed, since it's resolved server-side
 * from the request's own source address, not read from the device -- followed by a free, keyless
 * weather lookup. Both failure-tolerant: any error leaves whatever FacePrefs already has alone,
 * since a stale reading from earlier is more useful on a wrist than blanking to "-°".
 */
object WeatherFetcher {
    private const val TAG = "WeatherFetcher"
    private const val GEO_IP_URL = "https://ipapi.co/json/"
    private const val TIMEOUT_MS = 8_000

    fun fetchAndStore(context: Context) {
        try {
            val (lat, lon) = fetchLocation() ?: return
            val celsius = fetchTemperature(lat, lon) ?: return
            FacePrefs(context).setWeatherTempCelsius(celsius)
            Log.d(TAG, "Updated weather: ${celsius}°C at ($lat, $lon)")
        } catch (e: Exception) {
            Log.w(TAG, "Weather update failed", e)
        }
    }

    private fun fetchLocation(): Pair<Double, Double>? {
        val body = get(GEO_IP_URL) ?: return null
        val json = JSONObject(body)
        if (!json.has("latitude") || !json.has("longitude")) return null
        return json.getDouble("latitude") to json.getDouble("longitude")
    }

    private fun fetchTemperature(latitude: Double, longitude: Double): Int? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude&current=temperature_2m"
        val body = get(url) ?: return null
        val current = JSONObject(body).optJSONObject("current") ?: return null
        if (!current.has("temperature_2m")) return null
        return current.getDouble("temperature_2m").roundToInt()
    }

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Drives [WeatherFetcher] on a repeating background timer for as long as the watch face process
 * is alive. A plain daemon [Thread] rather than a coroutine: :wearface doesn't otherwise depend
 * on kotlinx-coroutines-core (the watchface libraries' own suspend functions don't require the
 * app to add it), so this avoids a new dependency for what's a simple "fetch, sleep, repeat" loop.
 */
object WeatherRefreshLoop {
    private const val REFRESH_INTERVAL_MS = 30 * 60_000L

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        Thread({
            while (true) {
                WeatherFetcher.fetchAndStore(appContext)
                try {
                    Thread.sleep(REFRESH_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
        }, "WeatherRefreshLoop").apply {
            isDaemon = true
        }.start()
    }
}
