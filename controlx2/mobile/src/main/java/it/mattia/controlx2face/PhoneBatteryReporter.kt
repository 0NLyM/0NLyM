package it.mattia.controlx2face

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Watches the phone's own battery level and pushes it to the watch over the Data Layer, so the
 * Dot Matrix face's tap-cycled "phone battery" reading has something to show.
 * ACTION_BATTERY_CHANGED is a sticky broadcast that a manifest-declared receiver is never
 * delivered on modern Android -- it needs a live component holding a dynamic registration, so
 * [register]/[unregister] are called from CommService's onCreate/onDestroy, the app's one
 * long-lived foreground service.
 *
 * Received on the watch by FaceMessageListenerService.onDataChanged in :wearface, which persists
 * it into FacePrefs.
 */
object PhoneBatteryReporter {
    private const val PATH = "/controlx2face/phone-battery"
    private var receiver: BroadcastReceiver? = null

    fun register(context: Context) {
        if (receiver != null) return
        val appContext = context.applicationContext
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return
                push(appContext, level * 100 / scale)
            }
        }
        receiver = batteryReceiver
        // The sticky ACTION_BATTERY_CHANGED intent is delivered immediately on registration too,
        // so this also reports the current level right away rather than waiting for the next change.
        appContext.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun unregister(context: Context) {
        receiver?.let { context.applicationContext.unregisterReceiver(it) }
        receiver = null
    }

    private fun push(context: Context, percent: Int) {
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putInt("percent", percent)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }
}
