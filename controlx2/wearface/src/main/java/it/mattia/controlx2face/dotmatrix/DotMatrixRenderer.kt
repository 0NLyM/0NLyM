package it.mattia.controlx2face.dotmatrix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.BatteryManager
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
import kotlin.math.roundToInt

/**
 * Nothing on this face is sub-minute any more (the clock separator's blink was dropped along
 * with the separator itself), so it only needs redrawing once a minute; live data changes and
 * value taps drive extra redraws through [Renderer.invalidate] on top of this.
 */
private const val FRAME_PERIOD_MS = 60_000L

/** Weather widget: the face's topmost element, centred -- everything else below is shifted down
 *  to make room for it (see DATE_TOP_RATIO onward). */
private const val WEATHER_TOP_RATIO = 0.07f

/** Date line: small tracked-out caps, top edge as a fraction of display height. */
private const val DATE_TOP_RATIO = 0.235f
private const val DATE_TEXT_SIZE_RATIO = 0.05f
private const val DATE_LETTER_SPACING_EM = 0.18f

/** Cell pitch for the clock (Stile 5, 5x7), as a fraction of the display's shorter side. */
private const val CLOCK_PITCH_RATIO = 0.032f

/** Clock top edge, as a fraction of display height. */
private const val CLOCK_TOP_RATIO = 0.30f

/** Cell pitch for the tappable value (Stile 3/ORIGINAL, 5x7) -- smaller than the clock so the
 *  two read as headline/detail rather than as two clocks. */
private const val VALUE_PITCH_RATIO = 0.017f

/** Top edge of the value glyph block, as a fraction of display height -- kept a clear gap below
 *  the clock rather than the two reading as one crowded block. */
private const val VALUE_TOP_RATIO = 0.585f

/** Trend/icon glyph pitch, relative to the value's, so the mark reads as the smaller one. */
private const val TREND_PITCH_RATIO = 0.8f

/** Gap between the value and its trend arrow/icon, in value cell pitches. Wider than one cell gap
 *  -- at this font's smaller size a single-cell gap read as the mark overlapping the last digit. */
private const val TREND_GAP_RATIO = 2.2f

/** Extra tap-target padding above/below the value block, in value cell pitches -- the glyph box
 *  itself is a precise but uncomfortably small target to hit on a wrist. */
private const val VALUE_TAP_PADDING_RATIO = 0.8f

/** Hairline dashes flanking the value block on both sides, vertically centred on it (aligned with
 *  the glucose widget's own Y axis, not hung below the whole row). */
private const val VALUE_RULE_LEFT_RATIO = 0.16f
private const val VALUE_RULE_RIGHT_RATIO = 0.84f
private const val VALUE_RULE_GAP_PITCHES = 2f
private const val RULE_THICKNESS_RATIO = 0.0022f

/** Status row: watch battery | phone battery, each an icon+label pair centred within its half of
 *  the row -- weather moved out to its own widget at the top (see WEATHER_TOP_RATIO), leaving
 *  two columns instead of three. */
private const val STATUS_ROW_TOP_RATIO = 0.775f
private const val STATUS_ICON_HEIGHT_RATIO = 0.046f
private const val STATUS_LABEL_SIZE_RATIO = 0.040f
private const val STATUS_ICON_LABEL_GAP_RATIO = 0.018f
private const val STATUS_BATTERY_ICON_WIDTH_RATIO = 0.078f
private const val STATUS_OUTLINE_WIDTH_RATIO = 0.0044f
private val STATUS_COLUMN_CENTRE_RATIOS = floatArrayOf(0.35f, 0.65f)
private val STATUS_DIVIDER_X_RATIOS = floatArrayOf(0.5f)

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
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FacePalette.RULE }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FacePalette.SECONDARY
        typeface = ResourcesCompat.getFont(context, R.font.geist_mono_regular)
        letterSpacing = DATE_LETTER_SPACING_EM
        textAlign = Paint.Align.CENTER
    }
    private val statusLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FacePalette.SECONDARY
        typeface = ResourcesCompat.getFont(context, R.font.geist_mono_regular)
    }
    private val statusStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FacePalette.SECONDARY
        style = Paint.Style.STROKE
    }
    private val statusFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FacePalette.SECONDARY
        style = Paint.Style.FILL
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
            drawWeatherWidget(canvas, bounds)
            drawDate(canvas, bounds, zonedDateTime)
        }

        drawClock(canvas, bounds, zonedDateTime, ambient)

        if (!ambient) {
            val snapshot = FaceStateHolder.getInstance(context).getSnapshot()
            val thresholds = FacePrefs(context).getThresholds()
            drawValue(canvas, bounds, snapshot, thresholds)
            // Kept out of ambient with everything else in the status row: an always-on region
            // this size is both a burn-in pattern and wasted power on the low-refresh AOD path.
            drawStatusRow(canvas, bounds)
        }
    }

    /** The face's topmost element: a small sun icon + temperature, centred. See [WeatherFetcher]
     *  for where the temperature actually comes from -- there's no system data source a
     *  programmatic watch face can read, so this is a direct best-effort fetch instead. */
    private fun drawWeatherWidget(canvas: Canvas, bounds: Rect) {
        val shortSide = minOf(bounds.width(), bounds.height()).toFloat()
        val iconHeight = shortSide * STATUS_ICON_HEIGHT_RATIO
        val gap = shortSide * STATUS_ICON_LABEL_GAP_RATIO
        statusLabelPaint.textSize = shortSide * STATUS_LABEL_SIZE_RATIO
        statusStrokePaint.strokeWidth = shortSide * STATUS_OUTLINE_WIDTH_RATIO

        val rowTop = bounds.top + bounds.height() * WEATHER_TOP_RATIO
        val celsius = FacePrefs(context).getWeatherTempCelsius()
        drawStatusCell(
            canvas = canvas,
            columnCentre = bounds.exactCenterX(),
            rowTop = rowTop,
            iconHeight = iconHeight,
            gap = gap,
            iconWidth = iconHeight,
            label = celsius?.let { "$it°" } ?: "-°",
        ) { iconX, iconWidth ->
            drawWeatherIcon(canvas, iconX + iconWidth / 2f, rowTop + iconHeight / 2f, iconWidth * 0.55f)
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
        // clock's red is purely a style choice, not one) -- only interactive gets the accent.
        val digitPaint = if (ambient) litPaint else accentPaint

        var cursor = bounds.exactCenterX() - width / 2f
        for (c in clockText) {
            if (c == ':') {
                // No separator dots drawn -- just the blank gap they used to occupy, so HH and MM
                // still read as two groups without the dots themselves.
                cursor += (PixelFont.STATUS_COLON_WIDTH + DotGrid.GLYPH_GAP_CELLS) * pitch
            } else {
                val pattern = glyphs.glyphs[c]
                if (pattern != null) {
                    DotGrid.drawGlyph(canvas, pattern, cursor, y, pitch, digitPaint, unlit)
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
        val shortSide = minOf(bounds.width(), bounds.height()).toFloat()

        // Glucose gets its trend arrow; the other three get a small fixed icon in the same slot
        // (the same battery/reservoir/sensor-days marks the phone's Glyph Toy already uses for
        // its own cycled display), so every non-glucose reading is still legible at a glance.
        val markGlyph = when (displayMetric) {
            DisplayMetric.GLUCOSE -> snapshot.trend
                ?.takeIf { snapshot.staleness != Staleness.DEAD }
                ?.let { PixelFont.arrowSets.getValue(PixelFont.ArrowStyle.CURRENT).getValue(it) }
            DisplayMetric.PUMP_BATTERY -> PixelFont.batteryIcon
            DisplayMetric.IOB -> PixelFont.reservoirIcon
            DisplayMetric.SENSOR_DAYS -> PixelFont.sensorDaysIcon
        }
        val trendPitch = pitch * TREND_PITCH_RATIO
        val trendGap = pitch * TREND_GAP_RATIO

        // Value and mark are centred as one block: centring the value alone would put the mark on
        // top of the digits, since it hangs off the value's right edge.
        val valueWidth = DotGrid.measureText(text, glyphs, pitch)
        val trendWidth = if (markGlyph == null) 0f else trendGap + PixelFont.ARROW_WIDTH * trendPitch
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
        if (markGlyph != null) {
            DotGrid.drawGlyph(
                canvas = canvas,
                pattern = markGlyph,
                x = x + valueWidth + trendGap,
                y = y + (valueHeight - markGlyph.size * trendPitch) / 2f,
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

        // Hairline dashes flanking the value on both sides, vertically centred on it -- aligned
        // with the widget's own Y axis rather than hung below the whole row.
        val dashY = y + valueHeight / 2f
        val hairline = maxOf(1f, shortSide * RULE_THICKNESS_RATIO)
        val blockRight = x + valueWidth + trendWidth
        val dashGap = pitch * VALUE_RULE_GAP_PITCHES
        val dashLeftEnd = x - dashGap
        val dashLeftStart = bounds.left + bounds.width() * VALUE_RULE_LEFT_RATIO
        if (dashLeftEnd > dashLeftStart) {
            canvas.drawRect(dashLeftStart, dashY - hairline / 2f, dashLeftEnd, dashY + hairline / 2f, rulePaint)
        }
        val dashRightStart = blockRight + dashGap
        val dashRightEnd = bounds.left + bounds.width() * VALUE_RULE_RIGHT_RATIO
        if (dashRightEnd > dashRightStart) {
            canvas.drawRect(dashRightStart, dashY - hairline / 2f, dashRightEnd, dashY + hairline / 2f, rulePaint)
        }
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
                // Whole units only -- a decimal reads as false precision at this size.
                (units?.let { it.roundToInt().toString() } ?: "-") to
                    (units != null && units < thresholds.iobUnits)
            }
            DisplayMetric.SENSOR_DAYS -> {
                val days = snapshot.sensorDaysRemaining
                (days?.toString() ?: "-") to (days != null && days < thresholds.sensorDays)
            }
        }
    }

    private fun drawStatusRow(canvas: Canvas, bounds: Rect) {
        val shortSide = minOf(bounds.width(), bounds.height()).toFloat()
        val hairline = maxOf(1f, shortSide * RULE_THICKNESS_RATIO)

        val rowTop = bounds.top + bounds.height() * STATUS_ROW_TOP_RATIO
        val iconHeight = shortSide * STATUS_ICON_HEIGHT_RATIO
        val gap = shortSide * STATUS_ICON_LABEL_GAP_RATIO
        statusLabelPaint.textSize = shortSide * STATUS_LABEL_SIZE_RATIO
        statusStrokePaint.strokeWidth = shortSide * STATUS_OUTLINE_WIDTH_RATIO

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val watchBattery = batteryManager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
        val phoneBattery = FacePrefs(context).getPhoneBatteryPercent()

        val batteryIconWidth = shortSide * STATUS_BATTERY_ICON_WIDTH_RATIO
        drawStatusCell(
            canvas = canvas,
            columnCentre = bounds.left + bounds.width() * STATUS_COLUMN_CENTRE_RATIOS[0],
            rowTop = rowTop,
            iconHeight = iconHeight,
            gap = gap,
            iconWidth = batteryIconWidth,
            label = watchBattery?.let { "$it%" } ?: "-",
        ) { iconX, iconWidth -> drawBatteryIcon(canvas, iconX, rowTop, iconWidth, iconHeight, watchBattery ?: 0) }

        val phoneIconWidth = iconHeight * 0.6f
        drawStatusCell(
            canvas = canvas,
            columnCentre = bounds.left + bounds.width() * STATUS_COLUMN_CENTRE_RATIOS[1],
            rowTop = rowTop,
            iconHeight = iconHeight,
            gap = gap,
            iconWidth = phoneIconWidth,
            label = phoneBattery?.let { "$it%" } ?: "-",
        ) { iconX, iconWidth ->
            drawPhoneIcon(canvas, iconX, rowTop - iconHeight * 0.05f, iconWidth, iconHeight * 1.1f)
        }

        for (xRatio in STATUS_DIVIDER_X_RATIOS) {
            val x = bounds.left + bounds.width() * xRatio
            canvas.drawRect(
                x,
                rowTop - iconHeight * 0.12f,
                x + hairline,
                rowTop + iconHeight * 1.12f,
                rulePaint,
            )
        }
    }

    /** Draws an icon + label pair centred as one block within [columnCentre]'s third of the row --
     *  mirrors the now-removed Minimal Grid face's cell-centring approach. */
    private fun drawStatusCell(
        canvas: Canvas,
        columnCentre: Float,
        rowTop: Float,
        iconHeight: Float,
        gap: Float,
        iconWidth: Float,
        label: String,
        drawIcon: (iconX: Float, iconWidth: Float) -> Unit,
    ) {
        val labelWidth = statusLabelPaint.measureText(label)
        val blockWidth = iconWidth + gap + labelWidth
        val iconX = columnCentre - blockWidth / 2f
        drawIcon(iconX, iconWidth)
        val labelBaselineY = rowTop + iconHeight / 2f - (statusLabelPaint.ascent() + statusLabelPaint.descent()) / 2f
        canvas.drawText(label, iconX + iconWidth + gap, labelBaselineY, statusLabelPaint)
    }

    /** Minimal line battery glyph: outline + a small nub, fill proportional to [level]. */
    private fun drawBatteryIcon(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, level: Int) {
        val nubWidth = maxOf(2f, w * 0.12f)
        val cornerRadius = h * 0.22f
        canvas.drawRoundRect(RectF(x, y, x + w - nubWidth, y + h), cornerRadius, cornerRadius, statusStrokePaint)
        val nubHeight = h * 0.4f
        canvas.drawRoundRect(
            RectF(x + w - nubWidth, y + (h - nubHeight) / 2f, x + w, y + (h + nubHeight) / 2f),
            2f, 2f, statusFillPaint,
        )
        val pad = 3f
        val fillWidth = (w - nubWidth - pad * 2f) * (level.coerceIn(0, 100) / 100f)
        if (fillWidth > 0f) {
            canvas.drawRect(x + pad, y + pad, x + pad + fillWidth, y + h - pad, statusFillPaint)
        }
    }

    /** Minimal line phone silhouette: rounded rect + a home-indicator line. */
    private fun drawPhoneIcon(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val cornerRadius = w * 0.22f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), cornerRadius, cornerRadius, statusStrokePaint)
        val lineY = y + h - h * 0.12f
        canvas.drawLine(x + w * 0.32f, lineY, x + w * 0.68f, lineY, statusStrokePaint)
    }

    /** Minimal line sun glyph: a circle plus eight short rays -- decorative only (see the call
     *  site for why there's no live temperature reading behind it yet). */
    private fun drawWeatherIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r * 0.55f, statusStrokePaint)
        for (i in 0 until 8) {
            val angle = i * (Math.PI / 4.0)
            val cos = Math.cos(angle).toFloat()
            val sin = Math.sin(angle).toFloat()
            canvas.drawLine(
                cx + cos * r * 0.72f, cy + sin * r * 0.72f,
                cx + cos * r, cy + sin * r,
                statusStrokePaint,
            )
        }
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        // No complication slots on this face -- see drawWeatherIcon's call site for why weather is
        // a plain drawn icon instead of one.
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
    }
}
