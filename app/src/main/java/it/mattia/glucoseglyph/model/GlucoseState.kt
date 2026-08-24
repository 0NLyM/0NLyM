package it.mattia.glucoseglyph.model

import android.os.Handler
import android.os.Looper

/**
 * In-process latest-reading holder shared between [it.mattia.glucoseglyph.service.GlucosePollingService]
 * (the producer) and [it.mattia.glucoseglyph.glyph.GlucoseToyService] / the UI (the consumers).
 * All components run in the app's single default process, so a plain singleton is enough here.
 */
object GlucoseState {

    @Volatile
    var current: GlucoseReading? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun update(reading: GlucoseReading) {
        current = reading
        lastError = null
        notifyListeners()
    }

    fun updateError(message: String) {
        lastError = message
        notifyListeners()
    }

    private fun notifyListeners() {
        val snapshot = synchronized(listeners) { listeners.toList() }
        mainHandler.post { snapshot.forEach { it.invoke() } }
    }
}
