package it.mattia.controlx2face.diagnostic

import android.content.Context
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
import it.mattia.controlx2face.data.FaceStateHolder
import java.time.ZonedDateTime

/**
 * Diagnostic probe, round 2: same stock Canvas/Paint face as before (that build showed up fine),
 * now with exactly one addition -- the FaceStateHolder.getSnapshot() read that both real faces
 * make in render() and Diagnostic didn't. If this build vanishes from the picker the same way Dot
 * Matrix and Minimal Grid did, that read (SharedPreferences access on the render thread) is the
 * crash. If it still shows up, the bug is elsewhere in the real renderers. Remove this service,
 * its class and its preview once the question is answered.
 */
class DiagnosticWatchFaceService : WatchFaceService() {

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): WatchFace {
        val renderer = DiagnosticRenderer(surfaceHolder, currentUserStyleRepository, watchState, this)
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}

private class DiagnosticRenderer(
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    watchState: WatchState,
    private val context: Context,
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

        // The one addition over the previous diagnostic build: the exact read both real faces
        // make and this one didn't.
        val snapshot = FaceStateHolder.getInstance(context).getSnapshot()
        paint.textSize = bounds.height() * 0.06f
        canvas.drawText(
            "mgdl=${snapshot.glucoseMgdl}",
            bounds.exactCenterX(),
            bounds.exactCenterY() + bounds.height() * 0.15f,
            paint,
        )
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        canvas.drawColor(Color.TRANSPARENT)
    }
}
