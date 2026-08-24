package it.mattia.glucoseglyph.glyph

import it.mattia.glucoseglyph.model.Trend

/**
 * A tiny 5x7 dot-matrix font (digits + dash + decimal point) and a handful of 6x7 trend-arrow
 * glyphs, hand-drawn to read clearly on the Phone (3) Glyph Matrix's 25x25 LEDs.
 */
internal object PixelFont {

    // Each glyph is 7 rows of a bit-string; '1' = lit pixel.
    val digits: Map<Char, List<String>> = mapOf(
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
        '-' to listOf("00000", "00000", "00000", "11111", "00000", "00000", "00000")
    )
    const val DIGIT_WIDTH = 5
    const val DOT_WIDTH = 1
    const val GLYPH_HEIGHT = 7

    /** A single lit pixel on the baseline, used as a decimal point. */
    val dot: List<String> = listOf("0", "0", "0", "0", "0", "0", "1")

    val arrows: Map<Trend, List<String>> = mapOf(
        Trend.DOUBLE_UP to listOf(
            "001100", "011110", "110011", "001100", "011110", "110011", "000000"
        ),
        Trend.SINGLE_UP to listOf(
            "001100", "011110", "110011", "000110", "000110", "000110", "000110"
        ),
        Trend.FORTY_FIVE_UP to listOf(
            "000111", "000011", "000101", "001000", "010000", "100000", "000000"
        ),
        Trend.FLAT to listOf(
            "000000", "000010", "000011", "111111", "000011", "000010", "000000"
        ),
        Trend.FORTY_FIVE_DOWN to listOf(
            "000000", "100000", "010000", "001000", "000101", "000011", "000111"
        ),
        Trend.SINGLE_DOWN to listOf(
            "000110", "000110", "000110", "000110", "110011", "011110", "001100"
        ),
        Trend.DOUBLE_DOWN to listOf(
            "000000", "110011", "011110", "001100", "110011", "011110", "001100"
        ),
        Trend.UNKNOWN to listOf(
            "000000", "000000", "000000", "111111", "000000", "000000", "000000"
        )
    )
    const val ARROW_WIDTH = 6
}
