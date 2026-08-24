package it.mattia.glucoseglyph.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import it.mattia.glucoseglyph.model.AppSettings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = AppSettings(context)
        if (settings.serviceEnabled) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GlucosePollingService::class.java)
            )
        }
    }
}
