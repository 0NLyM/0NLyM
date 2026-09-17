package it.mattia.pixelfont

/**
 * Pixel fonts, each offered in a few selectable [DigitStyle]/[ArrowStyle] variants (see
 * [AppSettings][it.mattia.glucoseglyph.model.AppSettings] for the phone's stored choice):
 * a glucose-value digit font, a clock digit font, and a set of 5x5 trend-arrow glyphs (plus a
 * fixed "X" for an expired sensor, which isn't part of the style choice). '1' = lit pixel.
 *
 * Lives in its own dependency-free module so the phone's Glyph Matrix toy (:glyph) and the Wear
 * OS watch faces (:wearface) render from one shared set of glyph tables -- retouching a digit
 * here updates both surfaces at once, and neither drags the other's platform SDK along.
 */
object PixelFont {

    /** CURRENT is always the designer's latest-confirmed shapes and the default selection.
     * LEGACY/ORIGINAL are kept around purely as alternate looks the designer can switch back to.
     * Labels are deliberately neutral, numbered in picker order ("Stile 1" = the default).
     * STYLE4 (glucose-value digits only, extracted from the designer's own pixel-editor sheet) has
     * no clock counterpart -- both digit pickers filter to `it in <their own map>`, so it simply
     * never appears in the clock picker. */
    enum class DigitStyle(val label: String) {
        CURRENT("Stile 1"),
        LEGACY("Stile 2"),
        ORIGINAL("Stile 3"),
        STYLE4("Stile 4"),
        STYLE5("Stile 5")
    }

    enum class ArrowStyle(val label: String) {
        CURRENT("Stile 1"),
        ALTERNATE("Stile 2"),
        OUTLINE("Stile 3")
    }

    /** A digit font plus the column width its glyphs are drawn at (styles vary in both shape and
     * width, e.g. the original glucose-value font is 5 wide, today's is 4). */
    class GlyphSet(val glyphs: Map<Char, List<String>>, val width: Int)

    // --- Glucose-value digit font: CURRENT is 4x7 (widened from the designer's first 3x7 pass to
    // read more clearly); LEGACY is that first 3x7 designer pass; ORIGINAL is this app's very
    // first (pre-designer) 5x7 font. Each includes '-' and the lowercase "n/a" letters used when
    // ControlX2 reports no CGM connected to the pump. ---
    val valueDigitSets: Map<DigitStyle, GlyphSet> = mapOf(
        DigitStyle.CURRENT to GlyphSet(
            mapOf(
                '0' to listOf("0110", "1001", "1001", "1001", "1001", "1001", "0110"),
                '1' to listOf("0010", "0110", "0010", "0010", "0010", "0010", "0111"),
                '2' to listOf("1110", "0001", "0001", "0110", "1000", "1000", "1111"),
                '3' to listOf("1110", "0001", "0001", "0110", "0001", "0001", "1110"),
                '4' to listOf("0010", "0110", "0110", "1010", "1111", "0010", "0010"),
                '5' to listOf("1111", "1000", "1000", "1110", "0001", "0001", "1110"),
                '6' to listOf("0110", "1000", "1000", "1110", "1001", "1001", "0110"),
                '7' to listOf("1111", "0001", "0010", "0010", "0100", "0100", "0100"),
                '8' to listOf("0110", "1001", "1001", "0110", "1001", "1001", "0110"),
                '9' to listOf("0110", "1001", "1001", "0111", "0001", "0001", "0110"),
                '-' to listOf("0000", "0000", "0000", "1111", "0000", "0000", "0000"),
                'n' to listOf("0000", "0000", "1100", "1010", "1010", "1010", "1010"),
                // 'a' redrawn by the designer in their pixel editor (screenshot-extracted, a 3x5
                // shape right-padded into this style's 4-wide box like 'n' already is), then
                // trimmed of its rightmost column and nudged one pixel right per their follow-up.
                'a' to listOf("0000", "0000", "0110", "0001", "0011", "0101", "0111"),
                '/' to listOf("0001", "0001", "0010", "0010", "0100", "0100", "1000")
            ),
            width = 4
        ),
        DigitStyle.LEGACY to GlyphSet(
            mapOf(
                // '0' and '1' redrawn by the designer in their pixel editor (screenshot-extracted).
                '0' to listOf("110", "101", "101", "101", "101", "101", "011"),
                '1' to listOf("110", "010", "010", "010", "010", "010", "111"),
                '2' to listOf("110", "001", "001", "010", "100", "100", "111"),
                '3' to listOf("110", "001", "001", "110", "001", "001", "111"),
                '4' to listOf("100", "101", "101", "101", "110", "001", "001"),
                '5' to listOf("111", "100", "100", "010", "001", "001", "110"),
                '6' to listOf("011", "100", "100", "110", "101", "101", "011"),
                '7' to listOf("110", "001", "001", "010", "001", "001", "001"),
                '8' to listOf("110", "101", "101", "111", "101", "101", "011"),
                '9' to listOf("110", "101", "101", "011", "001", "001", "110"),
                '-' to listOf("000", "000", "000", "111", "000", "000", "000"),
                'n' to listOf("000", "000", "110", "101", "101", "101", "101"),
                'a' to listOf("000", "000", "011", "101", "011", "101", "011"),
                '/' to listOf("001", "001", "010", "010", "100", "100", "100")
            ),
            width = 3
        ),
        DigitStyle.ORIGINAL to GlyphSet(
            mapOf(
                '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
                '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
                '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
                '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
                '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
                '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
                '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
                '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
                '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
                '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
                '-' to listOf("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
                'n' to listOf("00000", "00000", "10110", "11001", "10001", "10001", "10001"),
                'a' to listOf("00000", "00000", "01110", "00001", "01111", "10001", "01111"),
                '/' to listOf("00001", "00001", "00010", "00100", "01000", "10000", "10000")
            ),
            width = 5
        ),
        // Stile 4: uniform 4x7 blocky/squared family, in the same spirit as the clock's Stile 2 --
        // started from the designer's own font sheet (drawn by hand in their pixel editor,
        // pauwma.github.io/GlyphMatrixEditor, all ten digits on one 25x25 canvas) but normalized to
        // one shared 4x7 box instead of each digit's own natural (and here, quite irregular) size,
        // improvising the shape wherever a digit didn't already fit that box cleanly.
        // '-'/'n'/'a'/'/' aren't part of the designer's sheet, so they're carried over from Stile 1
        // just to keep "---"/"n/a" renderable.
        DigitStyle.STYLE4 to GlyphSet(
            mapOf(
                '0' to listOf("1111", "1001", "1001", "1001", "1001", "1001", "1111"),
                '1' to listOf("0010", "0110", "0010", "0010", "0010", "0010", "0111"),
                '2' to listOf("1111", "0001", "0001", "1111", "1000", "1000", "1111"),
                '3' to listOf("1111", "0001", "0001", "1111", "0001", "0001", "1111"),
                '4' to listOf("1001", "1001", "1001", "1111", "0001", "0001", "0001"),
                '5' to listOf("1111", "1000", "1000", "1111", "0001", "0001", "1111"),
                '6' to listOf("1111", "1000", "1000", "1111", "1001", "1001", "1111"),
                '7' to listOf("1111", "0001", "0001", "0001", "0001", "0001", "0001"),
                '8' to listOf("1111", "1001", "1001", "1111", "1001", "1001", "1111"),
                '9' to listOf("1111", "1001", "1001", "1111", "0001", "0001", "1111"),
                '-' to listOf("0000", "0000", "0000", "1111", "0000", "0000", "0000"),
                'n' to listOf("0000", "0000", "1100", "1010", "1010", "1010", "1010"),
                'a' to listOf("0000", "0000", "0110", "1010", "0110", "1010", "0110"),
                '/' to listOf("0001", "0001", "0010", "0010", "0100", "0100", "1000")
            ),
            width = 4
        )
    )
    const val DOT_WIDTH = 1

    /** A single lit pixel on the baseline, used as a decimal point (same for every style). */
    val dot: List<String> = listOf("0", "0", "0", "0", "0", "0", "1")

    // --- Trend-arrow glyphs, 5x5 for every style. CURRENT is the designer-confirmed shape set
    // (labeled A-E and confirmed against a render after an earlier guess turned out wrong on real
    // hardware). ALTERNATE is a second hand-drawn style the designer provided afterward. ---
    private val currentFlat = listOf(
        "00100", "10110", "01111", "10110", "00100"
    )
    private val currentUp = listOf(
        "00100", "01110", "11111", "00100", "01010"
    )
    private val currentDown = listOf(
        "01010", "00100", "11111", "01110", "00100"
    )
    private val currentFortyFiveUp = listOf(
        "11111", "01111", "00111", "11011", "01001"
    )
    private val currentFortyFiveDown = listOf(
        "01001", "11011", "00111", "01111", "11111"
    )

    // ALTERNATE: solid-block arrows, extracted pixel-for-pixel from the designer's second
    // reference photo and confirmed shape-by-shape: drawn as stabile/su/giu across the top row
    // and diagonale su/diagonale giu across the bottom row of that sheet.
    private val altFlat = listOf(
        "11000", "11110", "11111", "11110", "11000"
    )
    private val altUp = listOf(
        "00100", "01110", "01110", "11111", "11111"
    )
    private val altDown = listOf(
        "11111", "11111", "01110", "01110", "00100"
    )
    private val altFortyFiveUp = listOf(
        "11111", "01111", "00111", "00011", "00001"
    )
    private val altFortyFiveDown = listOf(
        "00001", "00011", "00111", "01111", "11111"
    )

    // OUTLINE (Stile 3): Claude-designed classic outline arrows -- a shaft with a winged head,
    // Unicode-arrow style -- approved by the designer from a render.
    private val outlineFlat = listOf(
        "00100", "00010", "11111", "00010", "00100"
    )
    private val outlineUp = listOf(
        "00100", "01110", "10101", "00100", "00100"
    )
    private val outlineDown = listOf(
        "00100", "00100", "10101", "01110", "00100"
    )
    private val outlineFortyFiveUp = listOf(
        "00111", "00011", "00101", "01000", "10000"
    )
    private val outlineFortyFiveDown = listOf(
        "10000", "01000", "00101", "00011", "00111"
    )

    val arrowSets: Map<ArrowStyle, Map<Trend, List<String>>> = mapOf(
        ArrowStyle.CURRENT to mapOf(
            Trend.DOUBLE_UP to currentUp,
            Trend.SINGLE_UP to currentUp,
            Trend.FORTY_FIVE_UP to currentFortyFiveUp,
            Trend.FLAT to currentFlat,
            Trend.FORTY_FIVE_DOWN to currentFortyFiveDown,
            Trend.SINGLE_DOWN to currentDown,
            Trend.DOUBLE_DOWN to currentDown,
            Trend.UNKNOWN to currentFlat
        ),
        ArrowStyle.ALTERNATE to mapOf(
            Trend.DOUBLE_UP to altUp,
            Trend.SINGLE_UP to altUp,
            Trend.FORTY_FIVE_UP to altFortyFiveUp,
            Trend.FLAT to altFlat,
            Trend.FORTY_FIVE_DOWN to altFortyFiveDown,
            Trend.SINGLE_DOWN to altDown,
            Trend.DOUBLE_DOWN to altDown,
            Trend.UNKNOWN to altFlat
        ),
        ArrowStyle.OUTLINE to mapOf(
            Trend.DOUBLE_UP to outlineUp,
            Trend.SINGLE_UP to outlineUp,
            Trend.FORTY_FIVE_UP to outlineFortyFiveUp,
            Trend.FLAT to outlineFlat,
            Trend.FORTY_FIVE_DOWN to outlineFortyFiveDown,
            Trend.SINGLE_DOWN to outlineDown,
            Trend.DOUBLE_DOWN to outlineDown,
            Trend.UNKNOWN to outlineFlat
        )
    )
    const val ARROW_WIDTH = 5

    // Not part of the style choice -- kept as-is regardless of arrow style.
    val expiredSensor = listOf(
        "10011", "01010", "00100", "01010", "11001"
    )

    // Fixed 5x5 icons shown in the arrow's slot when the Glyph Toy is cycled to a non-glucose
    // display mode (see ToyDisplayMode) -- not part of the arrow style choice either.
    val batteryIcon = listOf(
        "01110", "11111", "10101", "10101", "11111"
    )
    val reservoirIcon = listOf(
        "00100", "01110", "11111", "11111", "01110"
    )
    val sensorDaysIcon = listOf(
        "11111", "01010", "00100", "01010", "11111"
    )

    // --- Clock digit font: CURRENT is today's pixel-perfect re-transcription (photo-verified,
    // 5-9-0 corrected via automated per-cell analysis); LEGACY is the transcription attempt right
    // before that fix; ORIGINAL is this app's very first (pre-designer) clock font. A dedicated
    // 1-wide colon glyph (same for every style) sits between HH and MM. ---
    val clockDigitSets: Map<DigitStyle, GlyphSet> = mapOf(
        // '1' and '2' updated from a later reference photo of the designer's pixel editor
        // (brightness-probed per cell): '1' moved its flag to the top row, '2' centered its
        // middle pixel.
        DigitStyle.CURRENT to GlyphSet(
            mapOf(
                '0' to listOf("111", "101", "101", "111"),
                '1' to listOf("110", "010", "010", "111"),
                '2' to listOf("111", "001", "010", "111"),
                '3' to listOf("111", "011", "001", "111"),
                '4' to listOf("101", "101", "111", "001"),
                '5' to listOf("111", "110", "001", "111"),
                '6' to listOf("111", "100", "111", "111"),
                '7' to listOf("111", "001", "001", "001"),
                '8' to listOf("111", "101", "111", "111"),
                '9' to listOf("111", "111", "001", "111")
            ),
            width = 3
        ),
        // Stile 2: a Claude-designed 3x5 "digital/LED" family, replacing the earlier 3x4 rounded
        // set. The designer approved this set from a render.
        DigitStyle.LEGACY to GlyphSet(
            mapOf(
                '0' to listOf("111", "101", "101", "101", "111"),
                '1' to listOf("010", "110", "010", "010", "111"),
                '2' to listOf("111", "001", "111", "100", "111"),
                '3' to listOf("111", "001", "111", "001", "111"),
                '4' to listOf("101", "101", "111", "001", "001"),
                '5' to listOf("111", "100", "111", "001", "111"),
                '6' to listOf("111", "100", "111", "101", "111"),
                '7' to listOf("111", "001", "010", "010", "010"),
                '8' to listOf("111", "101", "111", "101", "111"),
                '9' to listOf("111", "101", "111", "001", "111")
            ),
            width = 3
        ),
        DigitStyle.ORIGINAL to GlyphSet(
            mapOf(
                '0' to listOf("0110", "1001", "1001", "1001", "0110"),
                '1' to listOf("0010", "0110", "0010", "0010", "0111"),
                '2' to listOf("1110", "0001", "0110", "1000", "1111"),
                '3' to listOf("1110", "0001", "0110", "0001", "1110"),
                '4' to listOf("1001", "1001", "1111", "0001", "0001"),
                '5' to listOf("1111", "1000", "1110", "0001", "1110"),
                '6' to listOf("0110", "1000", "1110", "1001", "0110"),
                '7' to listOf("1111", "0001", "0010", "0100", "0100"),
                '8' to listOf("0110", "1001", "0110", "1001", "0110"),
                '9' to listOf("0110", "1001", "0111", "0001", "0110")
            ),
            width = 4
        ),
        // Stile 5: the blocky 5x7 "digital dot matrix" family transcribed from the designer's
        // reference photos of a big LED-style clock -- same shapes as valueDigitSets.ORIGINAL,
        // added here as its clock counterpart (clock-only, same reason STYLE4 is value-only: the
        // picker filters to `it in <its own map>`) so the Dot Matrix watch face's clock can use
        // it large while the glucose value below it uses the existing ORIGINAL entry, smaller.
        DigitStyle.STYLE5 to GlyphSet(
            mapOf(
                '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
                '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
                '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
                '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
                '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
                '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
                '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
                '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
                '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
                '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100")
            ),
            width = 5
        )
    )

    val statusColon: List<String> = listOf("0", "1", "0", "1")
    const val STATUS_COLON_WIDTH = 1

    /** A 7-row colon matching STYLE5's height, drawn separately from [statusColon] (4 rows) so it
     *  lines up with a taller digit; kept here rather than folded into [statusColon] since every
     *  other style is 4-5 rows tall and would mis-space against a 7-row separator. Two adjacent
     *  pairs (bottom pair anchored to the last two rows), split by a single unlit row in the
     *  middle -- the pairs stay fused, only the middle gap separates them. */
    val clockSeparator7Row: List<String> = listOf("0", "0", "1", "1", "0", "1", "1")
}
