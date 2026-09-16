package it.mattia.controlx2face.data

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.PumpMessageSerializer

class FaceMessageListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != MessagePaths.FROM_PUMP_RECEIVE_MESSAGE) return

        try {
            val payload = messageEvent.data
            val deserialized = PumpMessageSerializer.fromBytes(payload)
            Log.d(TAG, "onMessageReceived: ${deserialized.javaClass.simpleName}")

            val state = FaceStateHolder.getInstance(this)
            val bridge = FacePumpMessageBridge(FacePrefs(this))
            bridge.processPumpMessage(deserialized)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process pump message", e)
        }
    }

    // The Dot Matrix face has no settings UI of its own (see wearface's AndroidManifest.xml for
    // why the system style editor was dropped), so its red-threshold settings live on the phone
    // and arrive here as a synced DataItem instead of a runtime message.
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                if (event.dataItem.uri.path != THRESHOLDS_PATH) continue

                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val defaults = FaceThresholds.DEFAULT
                val thresholds = FaceThresholds(
                    glucoseLow = map.getInt("glucose_low", defaults.glucoseLow),
                    glucoseHigh = map.getInt("glucose_high", defaults.glucoseHigh),
                    batteryPercent = map.getInt("battery_percent", defaults.batteryPercent),
                    iobUnits = map.getFloat("iob_units", defaults.iobUnits),
                    sensorDays = map.getInt("sensor_days", defaults.sensorDays),
                )
                Log.d(TAG, "onDataChanged: thresholds=$thresholds")
                FacePrefs(this).setThresholds(thresholds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process threshold update", e)
        } finally {
            dataEvents.release()
        }
    }

    companion object {
        private const val TAG = "FaceMessageListener"
        const val THRESHOLDS_PATH = "/controlx2face/thresholds"
    }
}
