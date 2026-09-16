package it.mattia.glucoseglyph.model

import android.content.Context
import android.content.SharedPreferences
import it.mattia.pixelfont.PixelFont

/** Thin wrapper around SharedPreferences holding the Glyph Toy's display personalization. */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("glucose_glyph_prefs", Context.MODE_PRIVATE)

    var useMmol: Boolean
        get() = prefs.getBoolean(KEY_USE_MMOL, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_MMOL, value).apply()

    // --- Personalizzazione: which glyph style to draw with, per element ---

    var arrowStyle: PixelFont.ArrowStyle
        get() = readEnum(KEY_ARROW_STYLE, PixelFont.ArrowStyle.entries, PixelFont.ArrowStyle.CURRENT)
            // A persisted style whose glyph set isn't (or is no longer) drawn in reads back as
            // CURRENT, so the app never resurrects a selection it can't render.
            .takeIf { it in PixelFont.arrowSets } ?: PixelFont.ArrowStyle.CURRENT
        set(value) = prefs.edit().putString(KEY_ARROW_STYLE, value.name).apply()

    var clockDigitStyle: PixelFont.DigitStyle
        get() = readEnum(KEY_CLOCK_DIGIT_STYLE, PixelFont.DigitStyle.entries, PixelFont.DigitStyle.CURRENT)
        set(value) = prefs.edit().putString(KEY_CLOCK_DIGIT_STYLE, value.name).apply()

    var valueDigitStyle: PixelFont.DigitStyle
        get() = readEnum(KEY_VALUE_DIGIT_STYLE, PixelFont.DigitStyle.entries, PixelFont.DigitStyle.CURRENT)
        set(value) = prefs.edit().putString(KEY_VALUE_DIGIT_STYLE, value.name).apply()

    // The pump doesn't report a CGM sensor's total lifespan, only when its current session
    // started -- so "days remaining" (one of the Glyph Toy's cycled display modes) needs this
    // to be told to it explicitly, picked from the Personalizzazione sheet.
    var sensorDurationDays: Int
        get() = prefs.getInt(KEY_SENSOR_DURATION_DAYS, 10)
        set(value) = prefs.edit().putInt(KEY_SENSOR_DURATION_DAYS, value).apply()

    // --- Dot Matrix watch face red-accent thresholds. The watch face itself has no settings UI
    // (its wearableConfigurationAction was removed -- see wearface's AndroidManifest.xml), so
    // these live here, next to the rest of the app's personalization, and are pushed to the
    // watch via the Data Layer whenever they change (see FaceThresholdsSync in :mobile). Glucose
    // keeps its existing two-sided range; the other three are single-sided ("red below X"). ---

    var glucoseLowThreshold: Int
        get() = prefs.getInt(KEY_GLUCOSE_LOW_THRESHOLD, 70)
        set(value) = prefs.edit().putInt(KEY_GLUCOSE_LOW_THRESHOLD, value).apply()

    var glucoseHighThreshold: Int
        get() = prefs.getInt(KEY_GLUCOSE_HIGH_THRESHOLD, 180)
        set(value) = prefs.edit().putInt(KEY_GLUCOSE_HIGH_THRESHOLD, value).apply()

    var batteryThreshold: Int
        get() = prefs.getInt(KEY_BATTERY_THRESHOLD, 20)
        set(value) = prefs.edit().putInt(KEY_BATTERY_THRESHOLD, value).apply()

    var iobThreshold: Float
        get() = prefs.getFloat(KEY_IOB_THRESHOLD, 1f)
        set(value) = prefs.edit().putFloat(KEY_IOB_THRESHOLD, value).apply()

    var sensorDaysThreshold: Int
        get() = prefs.getInt(KEY_SENSOR_DAYS_THRESHOLD, 2)
        set(value) = prefs.edit().putInt(KEY_SENSOR_DAYS_THRESHOLD, value).apply()

    private fun <T : Enum<T>> readEnum(key: String, values: List<T>, default: T): T =
        prefs.getString(key, null)?.let { saved -> values.find { it.name == saved } } ?: default

    fun registerOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val KEY_USE_MMOL = "use_mmol"
        const val KEY_ARROW_STYLE = "arrow_style"
        const val KEY_CLOCK_DIGIT_STYLE = "clock_digit_style"
        const val KEY_VALUE_DIGIT_STYLE = "value_digit_style"
        const val KEY_SENSOR_DURATION_DAYS = "sensor_duration_days"
        const val KEY_GLUCOSE_LOW_THRESHOLD = "face_glucose_low_threshold"
        const val KEY_GLUCOSE_HIGH_THRESHOLD = "face_glucose_high_threshold"
        const val KEY_BATTERY_THRESHOLD = "face_battery_threshold"
        const val KEY_IOB_THRESHOLD = "face_iob_threshold"
        const val KEY_SENSOR_DAYS_THRESHOLD = "face_sensor_days_threshold"
    }
}
