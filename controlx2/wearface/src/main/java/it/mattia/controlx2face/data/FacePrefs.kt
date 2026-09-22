package it.mattia.controlx2face.data

import android.content.Context
import android.content.SharedPreferences
import it.mattia.pixelfont.Trend

class FacePrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "controlx2face_prefs",
        Context.MODE_PRIVATE,
    )

    fun getSnapshot(): FaceSnapshot {
        val glucoseMgdl = prefs.getInt("glucose_mgdl", -1).takeIf { it >= 0 }
        val trendOrdinal = prefs.getInt("trend_ordinal", -1).takeIf { it >= 0 }
        val trend = trendOrdinal?.let { Trend.entries.getOrNull(it) }
        val iobUnits = prefs.getFloat("iob_units", -1f).takeIf { it >= 0 }
        val batteryPercent = prefs.getInt("battery_percent", -1).takeIf { it >= 0 }
        val sensorDaysRemaining = prefs.getInt("sensor_days_remaining", -1).takeIf { it >= 0 }
        val lastUpdateSeconds = prefs.getLong("last_update_seconds", 0)
        val unitOrdinal = prefs.getInt("glucose_unit", 0)
        val unit = GlucoseUnit.entries.getOrNull(unitOrdinal) ?: GlucoseUnit.MG_DL

        return FaceSnapshot(
            glucoseMgdl = glucoseMgdl,
            glucoseUnit = unit,
            trend = trend,
            iobUnits = iobUnits,
            batteryPercent = batteryPercent,
            sensorDaysRemaining = sensorDaysRemaining,
            lastUpdateEpochSeconds = lastUpdateSeconds,
        )
    }

    fun setSnapshot(snapshot: FaceSnapshot) {
        prefs.edit().apply {
            if (snapshot.glucoseMgdl != null) {
                putInt("glucose_mgdl", snapshot.glucoseMgdl)
            } else {
                remove("glucose_mgdl")
            }
            if (snapshot.trend != null) {
                putInt("trend_ordinal", snapshot.trend.ordinal)
            } else {
                remove("trend_ordinal")
            }
            if (snapshot.iobUnits != null) {
                putFloat("iob_units", snapshot.iobUnits)
            } else {
                remove("iob_units")
            }
            if (snapshot.batteryPercent != null) {
                putInt("battery_percent", snapshot.batteryPercent)
            } else {
                remove("battery_percent")
            }
            if (snapshot.sensorDaysRemaining != null) {
                putInt("sensor_days_remaining", snapshot.sensorDaysRemaining)
            } else {
                remove("sensor_days_remaining")
            }
            putInt("glucose_unit", snapshot.glucoseUnit.ordinal)
            putLong("last_update_seconds", snapshot.lastUpdateEpochSeconds)
            apply()
        }
    }

    fun getThresholds(): FaceThresholds = FaceThresholds(
        glucoseLow = prefs.getInt("threshold_glucose_low", FaceThresholds.DEFAULT.glucoseLow),
        glucoseHigh = prefs.getInt("threshold_glucose_high", FaceThresholds.DEFAULT.glucoseHigh),
        batteryPercent = prefs.getInt("threshold_battery_percent", FaceThresholds.DEFAULT.batteryPercent),
        iobUnits = prefs.getFloat("threshold_iob_units", FaceThresholds.DEFAULT.iobUnits),
        sensorDays = prefs.getInt("threshold_sensor_days", FaceThresholds.DEFAULT.sensorDays),
    )

    fun setThresholds(thresholds: FaceThresholds) {
        prefs.edit().apply {
            putInt("threshold_glucose_low", thresholds.glucoseLow)
            putInt("threshold_glucose_high", thresholds.glucoseHigh)
            putInt("threshold_battery_percent", thresholds.batteryPercent)
            putFloat("threshold_iob_units", thresholds.iobUnits)
            putInt("threshold_sensor_days", thresholds.sensorDays)
            apply()
        }
    }

    /** Null until the phone's first battery report arrives (see FaceMessageListenerService). */
    fun getPhoneBatteryPercent(): Int? = prefs.getInt("phone_battery_percent", -1).takeIf { it >= 0 }

    fun setPhoneBatteryPercent(percent: Int) {
        prefs.edit().putInt("phone_battery_percent", percent).apply()
    }

    /** Null until the first successful fetch (see WeatherFetcher). Int.MIN_VALUE, not -1, is the
     *  "absent" sentinel here -- unlike the percentages above, a negative temperature is a real
     *  reading, not a missing one. */
    fun getWeatherTempCelsius(): Int? =
        prefs.getInt("weather_temp_celsius", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

    fun setWeatherTempCelsius(celsius: Int) {
        prefs.edit().putInt("weather_temp_celsius", celsius).apply()
    }
}
