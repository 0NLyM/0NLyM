package it.mattia.controlx2face

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import it.mattia.glucoseglyph.model.AppSettings

/**
 * Pushes the Dot Matrix watch face's red-accent thresholds (see AppSettings' "Dot Matrix watch
 * face" properties) to the watch over the Data Layer. The watch face has no settings UI of its
 * own -- its wearableConfigurationAction was removed after it broke the face's visibility in the
 * picker on real hardware (see wearface's AndroidManifest.xml) -- so these are configured in the
 * phone's Glyph Toy settings screen and synced down from here.
 *
 * Received on the watch by FaceMessageListenerService.onDataChanged in :wearface, which persists
 * them into FacePrefs.
 */
object FaceThresholdsSync {
    private const val PATH = "/controlx2face/thresholds"
    private const val TAG = "FaceThresholdsSync"

    fun push(context: Context, settings: AppSettings) {
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putInt("glucose_low", settings.glucoseLowThreshold)
            dataMap.putInt("glucose_high", settings.glucoseHighThreshold)
            dataMap.putInt("battery_percent", settings.batteryThreshold)
            dataMap.putFloat("iob_units", settings.iobThreshold)
            dataMap.putInt("sensor_days", settings.sensorDaysThreshold)
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
            .addOnFailureListener { e -> Log.w(TAG, "Failed to push thresholds to watch", e) }
    }
}
