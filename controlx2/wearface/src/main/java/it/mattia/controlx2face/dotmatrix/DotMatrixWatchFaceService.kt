package it.mattia.controlx2face.dotmatrix

import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository

/**
 * "Dot Matrix" -- the watch-side counterpart to the phone's Glyph Matrix toy, drawing the time,
 * date and glucose reading from the very same [PixelFont][it.mattia.pixelfont.PixelFont] glyph
 * tables, so the two surfaces read as one product rather than two lookalikes.
 *
 * The value readout is tappable: it cycles glucose -> pump battery -> IOB -> sensor days
 * remaining (see [DotMatrixRenderer.handleValueTap]), always reset back to glucose on ambient
 * entry.
 */
class DotMatrixWatchFaceService : WatchFaceService() {

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): WatchFace {
        val renderer = DotMatrixRenderer(
            surfaceHolder = surfaceHolder,
            currentUserStyleRepository = currentUserStyleRepository,
            watchState = watchState,
            canvasType = CanvasType.HARDWARE,
            context = this,
        )
        return WatchFace(WatchFaceType.DIGITAL, renderer)
            .setTapListener(object : WatchFace.TapListener {
                override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
                    if (tapType != TapType.UP) return
                    if (renderer.handleValueTap(tapEvent.xPos, tapEvent.yPos)) {
                        renderer.invalidate()
                    }
                }
            })
    }
}
