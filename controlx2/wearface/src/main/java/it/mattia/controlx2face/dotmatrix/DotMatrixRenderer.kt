package it.mattia.controlx2face.dotmatrix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.SurfaceHolder
import androidx.core.content.res.ResourcesCompat
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import it.mattia.controlx2face.R
import it.mattia.controlx2face.data.DisplayMetric
import it.mattia.controlx2face.data.FacePrefs
import it.mattia.controlx2face.data.FaceSnapshot
import it.mattia.controlx2face.data.FaceStateHolder
import it.mattia.controlx2face.data.FaceThresholds
import it.mattia.controlx2face.data.GlucoseRange
import it.mattia.controlx2face.data.Staleness
import it.mattia.controlx2face.render.DotGrid
import it.mattia.controlx2face.render.FacePalette
import it.mattia.pixelfont.PixelFont
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Nothing there is sub-minute on this face, so it only needs redrawing once a minute; live data
 * changes and value taps drive extra redraws through [Renderer.invalidate] rather than polling.
 */
private const val FRAME_PERIOD_MS = 60_000L

/** Date line: small tracked-out caps, top edge as a fraction of display height. */
private const val DATE_TOP_RATIO = 0.19f
private const val DATE_TEXT_SIZE_RATIO = 0.034f
private const val DATE_LETTER_SPACING_EM = 0.22f

/** Cell pitch for the clock (Stile 5, 5x7), as a fraction of the display's shorter side. */
private const val CLOCK_PITCH_RATIO = 0.032f

/** Clock top edge, as a fraction of display height. */
private const val CLOCK_TOP_RATIO = 0.25f

/** Cell pitch for the tappable value (Stile 3/ORIGINAL, 5x7) -- smaller than the clock so the
 *  two read as headline/detail rather than as two clocks. */
private const val VALUE_PITCH_RATIO = 0.017f

/** Top edge of the value glyph block, as a fraction of display height. */
private const val VALUE_TOP_RATIO = 0.51f

/** Trend arrow pitch, relative to the value's, so the arrow reads as the smaller mark. */
private const val TREND_PITCH_RATIO = 0.8f

/** Gap between the value and its trend arrow, in value cell pitches. Wider than one cell gap --
 *  at this font's smaller size a single-cell gap read as the arrow overlapping the last digit. */
private const val TREND_GAP_RATIO = 2.2f

/** Extra tap-target padding above/below the value block, in value cell pitches -- the glyph box
 *  itself is a precise but uncomfortably small target to hit on a wrist. */
private const val VALUE_TAP_PADDING_RATIO = 0.8f

class DotMatrixRenderer(
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    private val watchState: WatchState,
    canvasType: Int,
    private val context: Context,
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
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FacePalette.SECONDARY
        typeface = ResourcesCompat.getFont(context, R.font.geist_mono_regular)
        letterSpacing = DATE_LETTER_SPACING_EM
        textAlign = Paint.Align.CENTER
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    /** Which value the tappable readout currently shows; always resets to glucose on ambient
     *  entry (see [render]) -- the watch-face equivalent of the screen turning off. */
    private var displayMetric = DisplayMetric.GLUCOSE

    /** Hit area for [handleValueTap], recomputed every interactive frame since the value's width
     *  (and so its centred position) depends on the text currently shown. Null in ambient, where
     *  taps are not delivered anyway. */
    private var valueTapRect: RectF? = null

    /** Cycles the tappable value to the next metric if ([xPos], [yPos]) falls inside its current
     *  hit area. Returns whether it actually changed, so the caller only invalidates when it did. */
    fun handleValueTap(xPos: Int, yPos: Int): Boolean {
        val rect = valueTapRect ?: return false
        if (!rect.contains(xPos.toFloat(), yPos.toFloat())) return false
        displayMetric = displayMetric.next()
        return true
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        val ambient = renderParameters.drawMode == DrawMode.AMBIENT
        canvas.drawColor(FacePalette.BACKGROUND)

        val lowBit = ambient && watchState.hasLowBitAmbient
        litPaint.isAntiAlias = !lowBit

        if (ambient) {
            // The face's equivalent of "screen off": whatever the wearer tapped to while
            // interactive is gone the moment the display goes dark, so it always comes back on
            // glucose rather than resuming wherever it was left.
            displayMetric = DisplayMetric.GLUCOSE
            valueTapRect = null
        } else {
            drawDate(canvas, bounds, zonedDateTime)
        }

        drawClock(canvas, bounds, zonedDateTime, ambient)

        if (!ambient) {
            val snapshot = FaceStateHolder.getInstance(context).getSnapshot()
            val thresholds = FacePrefs(context).getThresholds()
            drawValue(canvas, bounds, snapshot, thresholds)
        }
    }

    private fun drawDate(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        datePaint.textSize = bounds.height() * DATE_TEXT_SIZE_RATIO
        val text = zonedDateTime.format(dateFormatter).uppercase(Locale.getDefault())
        canvas.drawText(text, bounds.exactCenterX(), bounds.top + bounds.height() * DATE_TOP_RATIO, datePaint)
    }

    private fun drawClock(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, ambient: Boolean) {
        val glyphs = PixelFont.clockDigitSets.getValue(PixelFont.DigitStyle.STYLE5)
        val pitch = minOf(bounds.width(), bounds.height()) * CLOCK_PITCH_RATIO
        val clockText = "%02d:%02d".format(zonedDateTime.hour, zonedDateTime.minute)
        val width = DotGrid.measureText(clockText, glyphs, pitch)
        val y = bounds.top + bounds.height() * CLOCK_TOP_RATIO
        val unlit = if (ambient) null else unlitPaint
        // Ambient stays monochrome (see FacePalette: colour is a signal, not decoration, and the
        // separator's red is purely a style choice, not one) -- only interactive gets the accent.
        val separatorPaint = if (ambient) litPaint else accentPaint

        var cursor = bounds.exactCenterX() - width / 2f
        for (c in clockText) {
            if (c == ':') {
                DotGrid.drawGlyph(canvas, PixelFont.clockSeparator7Row, cursor, y, pitch, separatorPaint, unlit)
                cursor += (PixelFont.STATUS_COLON_WIDTH + DotGrid.GLYPH_GAP_CELLS) * pitch
            } else {
                val pattern = glyphs.glyphs[c]
                if (pattern != null) {
                    DotGrid.drawGlyph(canvas, pattern, cursor, y, pitch, litPaint, unlit)
                }
                cursor += (glyphs.width + DotGrid.GLYPH_GAP_CELLS) * pitch
            }
        }
    }

    private fun drawValue(canvas: Canvas, bounds: Rect, snapshot: FaceSnapshot, thresholds: FaceThresholds) {
        val glyphs = PixelFont.valueDigitSets.getValue(PixelFont.DigitStyle.ORIGINAL)
        val pitch = minOf(bounds.width(), bounds.height()) * VALUE_PITCH_RATIO
        val y = bounds.top + bounds.height() * VALUE_TOP_RATIO

        val (text, belowThreshold) = valueTextAndAlert(snapshot, thresholds)
        val valuePaint = if (belowThreshold) accentPaint else litPaint

        // The trend arrow is glucose-specific -- battery/IOB/sensor-days have no direction to show.
        val trendGlyph = if (displayMetric == DisplayMetric.GLUCOSE) {
            snapshot.trend
                ?.takeIf { snapshot.staleness != Staleness.DEAD }
                ?.let { PixelFont.arrowSets.getValue(PixelFont.ArrowStyle.CURRENT).getValue(it) }
        } else {
            null
        }
        val trendPitch = pitch * TREND_PITCH_RATIO
        val trendGap = pitch * TREND_GAP_RATIO

        // Value and arrow are centred as one block: centring the value alone would put the arrow
        // on top of the digits, since it hangs off the value's right edge.
        val valueWidth = DotGrid.measureText(text, glyphs, pitch)
        val trendWidth = if (trendGlyph == null) 0f else trendGap + PixelFont.ARROW_WIDTH * trendPitch
        val x = bounds.exactCenterX() - (valueWidth + trendWidth) / 2f

        DotGrid.drawText(
            canvas = canvas,
            text = text,
            glyphs = glyphs,
            x = x,
            y = y,
            pitch = pitch,
            litPaint = valuePaint,
            unlitPaint = unlitPaint,
        )

        val valueHeight = DotGrid.measureHeight(glyphs, pitch)
        if (trendGlyph != null) {
            DotGrid.drawGlyph(
                canvas = canvas,
                pattern = trendGlyph,
                x = x + valueWidth + trendGap,
                y = y + (valueHeight - trendGlyph.size * trendPitch) / 2f,
                pitch = trendPitch,
                litPaint = valuePaint,
                unlitPaint = unlitPaint,
            )
        }

        val padding = pitch * VALUE_TAP_PADDING_RATIO
        valueTapRect = RectF(
            bounds.left.toFloat(),
            y - padding,
            bounds.right.toFloat(),
            y + valueHeight + padding,
        )
    }

    /** The text to draw for [displayMetric], and whether it's below/out of its configured
     *  threshold and so should draw in the accent colour -- the same mechanism as glucose's
     *  existing out-of-range red, generalised to every cycled value (see [FaceThresholds]). */
    private fun valueTextAndAlert(snapshot: FaceSnapshot, thresholds: FaceThresholds): Pair<String, Boolean> {
        return when (displayMetric) {
            DisplayMetric.GLUCOSE -> {
                val text = when {
                    snapshot.glucoseMgdl == null -> "-"
                    snapshot.staleness == Staleness.DEAD -> "--"
                    else -> snapshot.glucoseMgdl.toString()
                }
                val range = snapshot.range(thresholds)
                text to (range == GlucoseRange.LOW || range == GlucoseRange.HIGH)
            }
            DisplayMetric.PUMP_BATTERY -> {
                val percent = snapshot.batteryPercent
                (percent?.toString() ?: "-") to (percent != null && percent < thresholds.batteryPercent)
            }
            DisplayMetric.IOB -> {
                val units = snapshot.iobUnits
                (units?.let(::formatIobUnits) ?: "-") to (units != null && units < thresholds.iobUnits)
            }
            DisplayMetric.SENSOR_DAYS -> {
                val days = snapshot.sensorDaysRemaining
                (days?.toString() ?: "-") to (days != null && days < thresholds.sensorDays)
            }
        }
    }

    private fun formatIobUnits(units: Float): String {
        val tenths = Math.round(units * 10)
        return "${tenths / 10}.${tenths % 10}"
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        // No complication slots on this face yet, so there is nothing to highlight in the editor.
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
    }
}
