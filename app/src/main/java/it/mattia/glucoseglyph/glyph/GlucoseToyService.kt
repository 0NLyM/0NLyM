package it.mattia.glucoseglyph.glyph

import android.content.Context
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager
import it.mattia.glucoseglyph.model.AppSettings
import it.mattia.glucoseglyph.model.GlucoseState

/**
 * The Glyph Toy shown in the Phone (3)'s Glyph Toys carousel / AOD. Draws whatever
 * [GlucoseState] currently holds and redraws immediately whenever [GlucosePollingService]
 * publishes a new reading, plus once a minute while on the always-on display (EVENT_AOD).
 *
 * Long-pressing the toy toggles between mg/dL and mmol/L, matching the long-press "cycle"
 * convention used by other Glyph Toys.
 */
class GlucoseToyService : GlyphMatrixServiceBase("Glucose-Toy") {

    private val settings by lazy { AppSettings(this) }
    private val onReadingChanged: () -> Unit = { redraw() }

    override fun performOnServiceConnected(context: Context, glyphMatrixManager: GlyphMatrixManager) {
        redraw()
        GlucoseState.addListener(onReadingChanged)
    }

    override fun performOnServiceDisconnected(context: Context) {
        GlucoseState.removeListener(onReadingChanged)
    }

    override fun onAodTick() {
        redraw()
    }

    override fun onTouchPointLongPress() {
        settings.useMmol = !settings.useMmol
        redraw()
    }

    private fun redraw() {
        val manager = glyphMatrixManager ?: return
        val frame = MatrixRenderer.render(GlucoseState.current, settings.useMmol)
        try {
            manager.setMatrixFrame(frame)
        } catch (e: Exception) {
            Log.w(TAG, "setMatrixFrame failed", e)
        }
    }

    private companion object {
        const val TAG = "GlucoseToyService"
    }
}
