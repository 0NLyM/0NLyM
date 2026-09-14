package it.mattia.controlx2face.dotmatrix

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.SurfaceHolder
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import it.mattia.controlx2face.data.FaceStateHolder
import it.mattia.controlx2face.data.GlucoseRange
import it.mattia.controlx2face.data.Staleness
import it.mattia.controlx2face.render.DotGrid
import it.mattia.controlx2face.render.FacePalette
import it.mattia.pixelfont.PixelFont
import java.time.ZonedDateTime

/**
 * Nothing there is sub-minute on this face, so it only needs redrawing once a minute; live data
 * changes drive extra redraws through [Renderer.invalidate] rather than through polling.
 */
private const val FRAME_PERIOD_MS = 60_000L

/** Cell pitch for the clock, as a fraction of the display's shorter side. */
private const val CLOCK_PITCH_RATIO = 0.0356f

/** Clock baseline, as a fraction of display height (top edge of the glyph block). */
private const val CLOCK_TOP_RATIO = 0.213f

/** Cell pitch for the glucose value, as a fraction of the display's shorter side. */
private const val GLUCOSE_PITCH_RATIO = 0.0267f

/** Top edge of the glucose glyph block, as a fraction of display height. */
private const val GLUCOSE_TOP_RATIO = 0.45f

/** Trend arrow pitch, relative to the glucose value's, so the arrow reads as the smaller mark. */
private const val TREND_PITCH_RATIO = 0.8f

class DotMatrixRenderer(
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    private val watchState: WatchState,
    canvasType: Int,
    private val context: android.content.Context,
) : Renderer.CanvasRenderer2<DotMatrixRenderer.Assets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    FRAME_PERIOD_MS,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false,
) {

    class Assets : SharedAssets {
        override fun onDestroy() = Unit
    }

    override suspend fun createSharedAssets() = Assets()

    private val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FacePalette.LIT }
    private val unlitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FacePalette.UNLIT }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FacePalette.ACCENT }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        val ambient = renderParameters.drawMode == DrawMode.AMBIENT
        canvas.drawColor(FacePalette.BACKGROUND)

        val lowBit = ambient && watchState.hasLowBitAmbient
        litPaint.isAntiAlias = !lowBit

        val glyphs = PixelFont.clockDigitSets.getValue(PixelFont.DigitStyle.CURRENT)
        val pitch = minOf(bounds.width(), bounds.height()) * CLOCK_PITCH_RATIO

        val clockText = "%02d:%02d".format(zonedDateTime.hour, zonedDateTime.minute)
        val clockX = bounds.exactCenterX() - DotGrid.measureText(clockText, glyphs, pitch) / 2f
        val clockY = bounds.top + bounds.height() * CLOCK_TOP_RATIO

        DotGrid.drawText(
            canvas = canvas,
            text = clockText,
            glyphs = glyphs,
            x = clockX,
            y = clockY,
            pitch = pitch,
            litPaint = litPaint,
            unlitPaint = if (ambient) null else unlitPaint,
        )

        if (!ambient) {
            val snapshot = FaceStateHolder.getInstance(context).getSnapshot()
            val glucosePaint = if (snapshot.range == GlucoseRange.HIGH || snapshot.range == GlucoseRange.LOW) {
                accentPaint
            } else {
                litPaint
            }
            renderGlucoseAndTrend(canvas, bounds, pitch, glucosePaint, unlitPaint, snapshot)
        }
    }

    private fun renderGlucoseAndTrend(
        canvas: Canvas,
        bounds: Rect,
        pitch: Float,
        glucosePaint: Paint,
        unlitPaint: Paint,
        snapshot: it.mattia.controlx2face.data.FaceSnapshot,
    ) {
        val glucoseText = when {
            snapshot.glucoseMgdl == null -> "n/a"
            snapshot.staleness == Staleness.DEAD -> "---"
            else -> snapshot.glucoseMgdl.toString()
        }

        val glyphs = PixelFont.valueDigitSets.getValue(PixelFont.DigitStyle.CURRENT)
        val glucosePitch = minOf(bounds.width(), bounds.height()) * GLUCOSE_PITCH_RATIO
        val glucoseY = bounds.top + bounds.height() * GLUCOSE_TOP_RATIO

        val trendGlyph = snapshot.trend
            ?.takeIf { snapshot.staleness != Staleness.DEAD }
            ?.let { PixelFont.arrowSets.getValue(PixelFont.ArrowStyle.CURRENT).getValue(it) }
        val trendPitch = glucosePitch * TREND_PITCH_RATIO

        // Value and arrow are centred as one block: centring the value alone would put the arrow
        // on top of the digits, since it hangs off the value's right edge.
        val valueWidth = DotGrid.measureText(glucoseText, glyphs, glucosePitch)
        val trendWidth = if (trendGlyph == null) 0f else glucosePitch + PixelFont.ARROW_WIDTH * trendPitch
        val glucoseX = bounds.exactCenterX() - (valueWidth + trendWidth) / 2f

        DotGrid.drawText(
            canvas = canvas,
            text = glucoseText,
            glyphs = glyphs,
            x = glucoseX,
            y = glucoseY,
            pitch = glucosePitch,
            litPaint = glucosePaint,
            unlitPaint = unlitPaint,
        )

        if (trendGlyph != null) {
            val valueHeight = DotGrid.measureHeight(glyphs, glucosePitch)
            DotGrid.drawGlyph(
                canvas = canvas,
                pattern = trendGlyph,
                x = glucoseX + valueWidth + glucosePitch,
                y = glucoseY + (valueHeight - trendGlyph.size * trendPitch) / 2f,
                pitch = trendPitch,
                litPaint = glucosePaint,
                unlitPaint = unlitPaint,
            )
        }
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        // No complication slots on this face yet, so there is nothing to highlight in the editor.
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
    }
}
