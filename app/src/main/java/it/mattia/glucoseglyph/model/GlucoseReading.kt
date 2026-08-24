package it.mattia.glucoseglyph.model

/** Seconds between the Unix epoch and the Tandem pump's own epoch (2008-01-01T00:00:00Z). */
const val PUMP_EPOCH_OFFSET_SECONDS = 1_199_145_600L

enum class Trend {
    DOUBLE_UP, SINGLE_UP, FORTY_FIVE_UP,
    FLAT,
    FORTY_FIVE_DOWN, SINGLE_DOWN, DOUBLE_DOWN,
    UNKNOWN;

    companion object {
        /** Mirrors ControlX2's own XdripSgvPayload.directionFromTrendRate mapping. */
        fun fromTrendRate(trendRate: Int): Trend = when {
            trendRate <= -3 -> DOUBLE_DOWN
            trendRate <= -2 -> SINGLE_DOWN
            trendRate <= -1 -> FORTY_FIVE_DOWN
            trendRate < 1 -> FLAT
            trendRate < 2 -> FORTY_FIVE_UP
            trendRate < 3 -> SINGLE_UP
            else -> DOUBLE_UP
        }
    }
}

data class GlucoseReading(
    val mgdl: Int,
    val trend: Trend,
    /** Real-world Unix epoch millis of the CGM reading itself (pump timestamp, converted). */
    val readingEpochMillis: Long,
    /** Local device time when this app received/parsed the reading. */
    val receivedEpochMillis: Long,
    val valid: Boolean
) {
    fun mmol(): Double = mgdl / 18.0182
}
