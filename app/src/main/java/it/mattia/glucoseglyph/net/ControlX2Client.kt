package it.mattia.glucoseglyph.net

import it.mattia.glucoseglyph.model.GlucoseReading
import it.mattia.glucoseglyph.model.PUMP_EPOCH_OFFSET_SECONDS
import it.mattia.glucoseglyph.model.Trend
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the HTTP Debug API that ControlX2 exposes on-device (Settings > Debug > HTTP API).
 * See ControlX2's HttpDebugApiService/openapi.json for the underlying contract.
 */
class ControlX2Client(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://$host:$port"
    private val authHeader = Credentials.basic(username, password)

    sealed class FetchResult {
        data class Success(val reading: GlucoseReading) : FetchResult()
        data class Failure(val message: String) : FetchResult()
    }

    /** Verifies host/port/credentials by hitting the unauthenticated-shape index endpoint. */
    fun testConnection(): FetchResult {
        val request = Request.Builder()
            .url(baseUrl + "/")
            .header("Authorization", authHeader)
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    FetchResult.Success(emptyReading())
                } else {
                    FetchResult.Failure(httpErrorMessage(response.code))
                }
            }
        } catch (e: IOException) {
            FetchResult.Failure(e.message ?: "Errore di rete")
        }
    }

    /**
     * Actively asks the pump for a fresh CGM reading (opCode 34 / CURRENT_STATUS,
     * response opCode 35 = CurrentEGVGuiDataResponse) and parses it out of the
     * response array.
     */
    fun fetchLatestReading(): FetchResult {
        val body = JSONArray().put(
            JSONObject()
                .put("cargo", "")
                .put("opCode", 34)
                .put("characteristic", "CURRENT_STATUS")
        ).toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(baseUrl + "/api/pump/messages")
            .header("Authorization", authHeader)
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return FetchResult.Failure(httpErrorMessage(response.code))
                }
                val text = response.body?.string().orEmpty()
                parseEgvResponse(text)
                    ?: FetchResult.Failure("Nessuna lettura CGM nella risposta del pump")
            }
        } catch (e: IOException) {
            FetchResult.Failure(e.message ?: "Errore di rete")
        }
    }

    private fun parseEgvResponse(text: String): FetchResult? {
        val array = try {
            JSONArray(text)
        } catch (e: Exception) {
            return null
        }
        for (i in 0 until array.length()) {
            val message = array.optJSONObject(i) ?: continue
            val name = message.optString("name")
            if (!name.endsWith("CurrentEGVGuiDataResponse")) continue

            val params = message.optJSONObject("params") ?: continue
            val cgmReading = params.optInt("cgmReading", -1)
            if (cgmReading < 0) continue
            val trendRate = params.optInt("trendRate", 0)
            val egvStatus = params.optString("egvStatus", "VALID")
            val pumpTimestampSeconds = params.optLong("bgReadingTimestampSeconds", 0L)

            val readingEpochMillis =
                (pumpTimestampSeconds + PUMP_EPOCH_OFFSET_SECONDS) * 1000L

            return FetchResult.Success(
                GlucoseReading(
                    mgdl = cgmReading,
                    trend = Trend.fromTrendRate(trendRate),
                    readingEpochMillis = readingEpochMillis,
                    receivedEpochMillis = System.currentTimeMillis(),
                    valid = egvStatus == "VALID"
                )
            )
        }
        return null
    }

    private fun emptyReading() = GlucoseReading(
        mgdl = 0,
        trend = Trend.UNKNOWN,
        readingEpochMillis = 0,
        receivedEpochMillis = System.currentTimeMillis(),
        valid = false
    )

    private fun httpErrorMessage(code: Int): String = when (code) {
        401 -> "Credenziali non valide"
        404 -> "API non trovata (versione ControlX2 non compatibile?)"
        else -> "Errore HTTP $code"
    }
}
