package it.mattia.controlx2face.data

/**
 * The red-accent thresholds for every value the Dot Matrix face can show, one per
 * [DisplayMetric]. Configured on the phone (the face has no settings UI of its own -- see
 * wearface's AndroidManifest.xml for why) and pushed down via the Data Layer; see
 * [FaceMessageListenerService][it.mattia.controlx2face.data.FaceMessageListenerService].
 *
 * Glucose keeps its existing two-sided range (below [glucoseLow] OR above [glucoseHigh]); the
 * other three are single-sided, red when the live value is below the threshold.
 */
data class FaceThresholds(
    val glucoseLow: Int = 70,
    val glucoseHigh: Int = 180,
    val batteryPercent: Int = 20,
    val iobUnits: Float = 1f,
    val sensorDays: Int = 2,
) {
    companion object {
        val DEFAULT = FaceThresholds()
    }
}
