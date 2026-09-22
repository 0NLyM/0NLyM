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
import it.mattia.controlx2face.data.WeatherRefreshLoop

/**
 * "Dot Matrix" -- the watch-side counterpart to the phone's Glyph Matrix toy, drawing the time,
 * date and glucose reading from the very same [PixelFont][it.mattia.pixelfont.PixelFont] glyph
 * tables, so the two surfaces read as one product rather than two lookalikes.
 *
 * The value readout is tappable: it cycles glucose -> pump battery -> IOB -> sensor days
 * remaining (see [DotMatrixRenderer.handleValueTap]), always reset back to glucose on ambient
 * entry.
 *
 * No complication slots: the weather widget is a plain drawn icon rather than a real
 * complication, since the androidx.wear.watchface complications API has no system weather data
 * source to bind it to (that only exists as a [WEATHER.*] expression in the declarative Watch
 * Face Format, which this programmatic face doesn't use) and there's no configuration editor for
 * the wearer to pick a third-party provider either -- its temperature instead comes from
 * [WeatherRefreshLoop]'s own best-effort fetch, started below.
 */
class DotMatrixWatchFaceService : WatchFaceService() {

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): WatchFace {
        WeatherRefreshLoop.start(this)
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
