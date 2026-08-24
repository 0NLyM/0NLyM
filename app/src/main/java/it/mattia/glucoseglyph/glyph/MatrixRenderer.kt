package it.mattia.glucoseglyph.glyph

import it.mattia.glucoseglyph.model.GlucoseReading
import it.mattia.glucoseglyph.model.Trend
import kotlin.math.roundToInt

/**
 * Renders the current glucose reading as a raw 25x25 brightness grid (row-major, values 0-255)
 * matching the format expected by GlyphMatrixManager.setMatrixFrame(IntArray).
 *
 * Style notes (deliberately minimal, "Nothing"-esque): the matrix is monochrome hardware, so the
 * only expressive dimension left is brightness. A fresh, valid reading is drawn at full
 * brightness; a stale one (no update in a while) dims automatically instead of adding an icon;
 * no reading at all shows a dim dashed placeholder.
 */
object MatrixRenderer {
    const val SIZE = 25

    private const val FULL_BRIGHTNESS = 255
    private const val DIM_BRIGHTNESS = 70
    private const val STALE_AFTER_MS = 15 * 60 * 1000L

    fun render(reading: GlucoseReading?, useMmol: Boolean, nowMillis: Long = System.currentTimeMillis()): IntArray {
        val grid = IntArray(SIZE * SIZE)

        if (reading == null) {
            drawPlaceholder(grid)
            return grid
        }

        val stale = (nowMillis - reading.receivedEpochMillis) > STALE_AFTER_MS
        val brightness = if (!reading.valid) DIM_BRIGHTNESS else if (stale) DIM_BRIGHTNESS else FULL_BRIGHTNESS

        if (!reading.valid) {
            drawPlaceholder(grid, brightness)
            return grid
        }

        if (useMmol) {
            drawMmol(grid, reading.mmol(), reading.trend, brightness)
        } else {
            drawMgdl(grid, reading.mgdl, reading.trend, brightness)
        }
        return grid
    }

    private fun drawPlaceholder(grid: IntArray, brightness: Int = DIM_BRIGHTNESS) {
        val chars = "---".toList()
        val totalWidth = chars.size * PixelFont.DIGIT_WIDTH + (chars.size - 1)
        var x = centeredStart(totalWidth, 17)
        val y = (SIZE - PixelFont.GLYPH_HEIGHT) / 2
        for (c in chars) {
            drawGlyph(grid, PixelFont.digits.getValue(c), x, y, brightness)
            x += PixelFont.DIGIT_WIDTH + 1
        }
    }

    private fun drawMgdl(grid: IntArray, mgdl: Int, trend: Trend, brightness: Int) {
        val digitsStr = mgdl.coerceIn(0, 999).toString()
        val totalWidth = digitsStr.length * PixelFont.DIGIT_WIDTH + (digitsStr.length - 1)
        var x = centeredStart(totalWidth, 17)
        val y = (SIZE - PixelFont.GLYPH_HEIGHT) / 2
        for (c in digitsStr) {
            drawGlyph(grid, PixelFont.digits.getValue(c), x, y, brightness)
            x += PixelFont.DIGIT_WIDTH + 1
        }

        val arrowX = 19
        drawGlyph(grid, PixelFont.arrows.getValue(trend), arrowX, y, arrowBrightness(brightness))
    }

    private fun drawMmol(grid: IntArray, mmol: Double, trend: Trend, brightness: Int) {
        val rounded = (mmol * 10).roundToInt() / 10.0
        val text = String.format("%.1f", rounded) // e.g. "6.7" or "12.3"

        var totalWidth = 0
        for (c in text) totalWidth += if (c == '.') PixelFont.DOT_WIDTH else PixelFont.DIGIT_WIDTH
        totalWidth += text.length - 1 // 1px gap between glyphs

        var x = centeredStart(totalWidth, SIZE)
        val y = 5
        for (c in text) {
            if (c == '.') {
                drawGlyph(grid, PixelFont.dot, x, y, brightness)
                x += PixelFont.DOT_WIDTH + 1
            } else {
                drawGlyph(grid, PixelFont.digits.getValue(c), x, y, brightness)
                x += PixelFont.DIGIT_WIDTH + 1
            }
        }

        val arrowX = centeredStart(PixelFont.ARROW_WIDTH, SIZE)
        drawGlyph(grid, PixelFont.arrows.getValue(trend), arrowX, 15, arrowBrightness(brightness))
    }

    private fun arrowBrightness(base: Int) = (base * 0.75).roundToInt().coerceIn(0, 255)

    private fun centeredStart(contentWidth: Int, availableWidth: Int): Int =
        ((availableWidth - contentWidth) / 2).coerceAtLeast(0)

    private fun drawGlyph(grid: IntArray, pattern: List<String>, x0: Int, y0: Int, brightness: Int) {
        for (row in pattern.indices) {
            val y = y0 + row
            if (y < 0 || y >= SIZE) continue
            val line = pattern[row]
            for (col in line.indices) {
                if (line[col] != '1') continue
                val x = x0 + col
                if (x < 0 || x >= SIZE) continue
                grid[y * SIZE + x] = brightness
            }
        }
    }
}
