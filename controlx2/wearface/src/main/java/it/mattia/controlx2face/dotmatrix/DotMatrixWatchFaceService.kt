package it.mattia.controlx2face.dotmatrix

import android.graphics.Color
import android.graphics.RectF
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotBounds
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import it.mattia.controlx2face.render.FacePalette

/** The only complication slot on this face: weather, bound permanently to the system's own
 *  weather provider (see [DotMatrixWatchFaceService.createComplicationSlotsManager] for why it
 *  can't be reassigned) and read by [DotMatrixRenderer] to draw it in the status row's third cell. */
const val WEATHER_COMPLICATION_SLOT_ID = 100

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

    // Weather is a plain complication slot, unrelated to the wearableConfigurationAction removed
    // from the manifest (that only ever gated the system style/complication *editor* UI). Bound
    // permanently to SystemDataSources.DATA_SOURCE_WEATHER since there's no editor to let the
    // wearer reassign it -- same tradeoff already accepted for Minimal Grid's fixed data cells
    // before that face was removed.
    override fun createComplicationSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): ComplicationSlotsManager {
        val canvasComplicationFactory = CanvasComplicationFactory { watchState, listener ->
            CanvasComplicationDrawable(
                ComplicationDrawable(this).apply {
                    activeStyle.textColor = FacePalette.SECONDARY
                    activeStyle.iconColor = FacePalette.SECONDARY
                    activeStyle.backgroundColor = Color.TRANSPARENT
                    activeStyle.borderStyle = ComplicationDrawable.BORDER_STYLE_NONE
                    ambientStyle.textColor = FacePalette.LIT_DIM
                    ambientStyle.iconColor = FacePalette.LIT_DIM
                    ambientStyle.backgroundColor = Color.TRANSPARENT
                    ambientStyle.borderStyle = ComplicationDrawable.BORDER_STYLE_NONE
                },
                watchState,
                listener,
            )
        }

        val weatherSlot = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = WEATHER_COMPLICATION_SLOT_ID,
            canvasComplicationFactory = canvasComplicationFactory,
            supportedTypes = listOf(ComplicationType.SHORT_TEXT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
                SystemDataSources.DATA_SOURCE_WEATHER,
                ComplicationType.SHORT_TEXT,
            ),
            // Status row's third cell (see DotMatrixRenderer's STATUS_* ratios): generous bounds,
            // since the system drawable centres its own icon+text within whatever box it's given.
            bounds = ComplicationSlotBounds(RectF(0.62f, 0.71f, 0.94f, 0.84f)),
        ).build()

        return ComplicationSlotsManager(listOf(weatherSlot), currentUserStyleRepository)
    }

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
            complicationSlotsManager = complicationSlotsManager,
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
