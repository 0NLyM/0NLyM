package it.mattia.controlx2face.diagnostic

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.time.ZonedDateTime

/**
 * Diagnostic probe: the absolute minimum a WatchFaceService can be -- stock Canvas/Paint only, no
 * PixelFont, no FaceStateHolder, no complications, no data layer. Exists solely to answer one
 * question the two real faces can't: does *anything* this app declares reach the picker, or does
 * the picker discard the app entirely regardless of what a face's renderer does. Remove once that
 * question is answered.
 */
class DiagnosticWatchFaceService : WatchFaceService() {

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): WatchFace {
        val renderer = DiagnosticRenderer(surfaceHolder, currentUserStyleRepository, watchState)
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}

private class DiagnosticRenderer(
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    watchState: WatchState,
) : Renderer.CanvasRenderer2<DiagnosticRenderer.Assets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    CanvasType.HARDWARE,
    16L,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false,
) {
    class Assets : SharedAssets {
        override fun onDestroy() = Unit
    }

    override suspend fun createSharedAssets() = Assets()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        canvas.drawColor(Color.BLACK)
        paint.textSize = bounds.height() * 0.2f
        canvas.drawText(
            "%02d:%02d".format(zonedDateTime.hour, zonedDateTime.minute),
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            paint,
        )
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        canvas.drawColor(Color.TRANSPARENT)
    }
}
