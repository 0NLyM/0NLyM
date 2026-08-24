package it.mattia.glucoseglyph.model

import android.content.Context
import android.content.SharedPreferences

/** Thin wrapper around SharedPreferences holding connection + display settings and the last reading. */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("glucose_glyph_prefs", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString(KEY_HOST, "127.0.0.1") ?: "127.0.0.1"
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, 18282)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "admin") ?: "admin"
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var pollIntervalSeconds: Int
        get() = prefs.getInt(KEY_POLL_INTERVAL, 60)
        set(value) = prefs.edit().putInt(KEY_POLL_INTERVAL, value).apply()

    var useMmol: Boolean
        get() = prefs.getBoolean(KEY_USE_MMOL, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_MMOL, value).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    // --- Latest reading cache, so the Glyph Toy can draw instantly on bind ---

    var lastMgdl: Int
        get() = prefs.getInt(KEY_LAST_MGDL, -1)
        set(value) = prefs.edit().putInt(KEY_LAST_MGDL, value).apply()

    var lastTrendRate: Int
        get() = prefs.getInt(KEY_LAST_TREND, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_TREND, value).apply()

    var lastReadingEpochMillis: Long
        get() = prefs.getLong(KEY_LAST_READING_MILLIS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_READING_MILLIS, value).apply()

    var lastFetchOk: Boolean
        get() = prefs.getBoolean(KEY_LAST_FETCH_OK, false)
        set(value) = prefs.edit().putBoolean(KEY_LAST_FETCH_OK, value).apply()

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    fun registerOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_POLL_INTERVAL = "poll_interval_seconds"
        private const val KEY_USE_MMOL = "use_mmol"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_LAST_MGDL = "last_mgdl"
        private const val KEY_LAST_TREND = "last_trend_rate"
        private const val KEY_LAST_READING_MILLIS = "last_reading_millis"
        private const val KEY_LAST_FETCH_OK = "last_fetch_ok"
        private const val KEY_LAST_ERROR = "last_error"
    }
}
