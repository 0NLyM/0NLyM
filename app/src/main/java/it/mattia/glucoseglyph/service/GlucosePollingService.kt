package it.mattia.glucoseglyph.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import it.mattia.glucoseglyph.GlucoseGlyphApp
import it.mattia.glucoseglyph.R
import it.mattia.glucoseglyph.model.AppSettings
import it.mattia.glucoseglyph.model.GlucoseState
import it.mattia.glucoseglyph.model.Trend
import it.mattia.glucoseglyph.net.ControlX2Client
import kotlin.concurrent.thread

/**
 * Foreground service that periodically pulls the latest CGM reading from ControlX2's
 * local HTTP Debug API and publishes it via [GlucoseState] (consumed by the Glyph Toy)
 * and [AppSettings] (persisted, so a fresh process still has a last-known value).
 */
class GlucosePollingService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var settings: AppSettings
    private var running = false

    private val pollLoop = object : Runnable {
        override fun run() {
            if (!running) return
            pollOnce()
            val intervalMs = settings.pollIntervalSeconds.coerceIn(15, 900) * 1000L
            mainHandler.postDelayed(this, intervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPolling()
            return START_NOT_STICKY
        }

        settings.serviceEnabled = true
        startForeground(NOTIFICATION_ID, buildNotification("In attesa della prima lettura…"))

        if (!running) {
            running = true
            mainHandler.post(pollLoop)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopPolling() {
        running = false
        settings.serviceEnabled = false
        mainHandler.removeCallbacks(pollLoop)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pollOnce() {
        thread(name = "glucose-poll") {
            val client = ControlX2Client(
                host = settings.host,
                port = settings.port,
                username = settings.username,
                password = settings.password
            )
            when (val result = client.fetchLatestReading()) {
                is ControlX2Client.FetchResult.Success -> {
                    val reading = result.reading
                    GlucoseState.update(reading)
                    settings.lastMgdl = reading.mgdl
                    settings.lastTrendRate = trendToRate(reading.trend)
                    settings.lastReadingEpochMillis = reading.readingEpochMillis
                    settings.lastFetchOk = true
                    settings.lastError = null
                    mainHandler.post {
                        updateNotification("${reading.mgdl} mg/dL")
                    }
                }
                is ControlX2Client.FetchResult.Failure -> {
                    GlucoseState.updateError(result.message)
                    settings.lastFetchOk = false
                    settings.lastError = result.message
                    mainHandler.post {
                        updateNotification("Errore: ${result.message}")
                    }
                }
            }
        }
    }

    private fun trendToRate(trend: Trend): Int = when (trend) {
        Trend.DOUBLE_DOWN -> -3
        Trend.SINGLE_DOWN -> -2
        Trend.FORTY_FIVE_DOWN -> -1
        Trend.FLAT -> 0
        Trend.FORTY_FIVE_UP -> 1
        Trend.SINGLE_UP -> 2
        Trend.DOUBLE_UP -> 3
        Trend.UNKNOWN -> 0
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, GlucosePollingService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 0, stopIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, GlucoseGlyphApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Glucose Glyph")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, "Ferma", stopPendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "it.mattia.glucoseglyph.action.STOP"
    }
}

private typealias NotificationManager = android.app.NotificationManager
