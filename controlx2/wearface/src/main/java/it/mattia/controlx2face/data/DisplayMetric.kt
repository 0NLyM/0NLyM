package it.mattia.controlx2face.data

/** The value shown in the Dot Matrix face's tappable readout, cycled by tapping it. */
enum class DisplayMetric {
    GLUCOSE,
    PUMP_BATTERY,
    IOB,
    SENSOR_DAYS;

    fun next(): DisplayMetric = entries[(ordinal + 1) % entries.size]
}
