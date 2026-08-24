package it.mattia.glucoseglyph

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class GlucoseGlyphApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Monitoraggio glucosio",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Servizio in background che legge il glucosio da ControlX2"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "glucose_polling"
    }
}
