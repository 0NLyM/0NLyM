@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.presentation.components.HeaderLine
import it.mattia.controlx2face.FaceThresholdsSync
import it.mattia.pixelfont.PixelFont
import it.mattia.glucoseglyph.model.AppSettings
import it.mattia.glucoseglyph.model.Trend

private val SENSOR_DURATION_OPTIONS = listOf(7, 10, 14, 15, 21)

/** The 5 visually distinct arrow shapes a style set defines, in reading order (up to down) --
 *  DOUBLE_UP/SINGLE_UP share one shape, as do SINGLE_DOWN/DOUBLE_DOWN, so this list already
 *  covers every shape the toy can actually draw without repeats. */
private val ARROW_PREVIEW_ORDER = listOf(
    Trend.SINGLE_UP, Trend.FORTY_FIVE_UP, Trend.FLAT, Trend.FORTY_FIVE_DOWN, Trend.SINGLE_DOWN
)

private val DIGIT_PREVIEW_ORDER = "0123456789".toList()

/**
 * Personalization for the Glyph Toy: which pixel-font style each element draws with, the CGM
 * sensor's configured lifespan (the pump doesn't report this itself, only when the current
 * session started -- see AppSettings.sensorDurationDays), and mg/dL vs mmol/L for the toy's
 * display. Deliberately its own preference, independent of ControlX2's own glucose unit setting
 * -- the Glyph Toy lives in a separate Gradle module with no dependency on :mobile's Prefs.
 */
@Composable
fun GlyphSettings(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }

    var arrowStyle by remember { mutableStateOf(settings.arrowStyle) }
    var clockDigitStyle by remember { mutableStateOf(settings.clockDigitStyle) }
    var valueDigitStyle by remember { mutableStateOf(settings.valueDigitStyle) }
    var sensorDurationDays by remember { mutableStateOf(settings.sensorDurationDays) }
    var useMmol by remember { mutableStateOf(settings.useMmol) }

    var glucoseLowThreshold by remember { mutableStateOf(settings.glucoseLowThreshold) }
    var glucoseHighThreshold by remember { mutableStateOf(settings.glucoseHighThreshold) }
    var batteryThreshold by remember { mutableStateOf(settings.batteryThreshold) }
    var iobThreshold by remember { mutableStateOf(settings.iobThreshold) }
    var sensorDaysThreshold by remember { mutableStateOf(settings.sensorDaysThreshold) }

    var showArrowDialog by remember { mutableStateOf(false) }
    var showClockDialog by remember { mutableStateOf(false) }
    var showValueDialog by remember { mutableStateOf(false) }
    var showSensorDialog by remember { mutableStateOf(false) }
    var showGlucoseLowDialog by remember { mutableStateOf(false) }
    var showGlucoseHighDialog by remember { mutableStateOf(false) }
    var showBatteryThresholdDialog by remember { mutableStateOf(false) }
    var showIobThresholdDialog by remember { mutableStateOf(false) }
    var showSensorDaysThresholdDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                HeaderLine("Glyph Toy")
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Unità di misura") },
                    supportingContent = { Text(if (useMmol) "mmol/L" else "mg/dL") },
                    leadingContent = {
                        Icon(Icons.Filled.WaterDrop, contentDescription = "Unità icon")
                    },
                    trailingContent = {
                        Switch(
                            checked = useMmol,
                            onCheckedChange = {
                                useMmol = it
                                settings.useMmol = it
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        useMmol = !useMmol
                        settings.useMmol = useMmol
                    }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Stile freccia di tendenza") },
                    supportingContent = { Text(arrowStyle.label) },
                    leadingContent = {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Freccia icon")
                    },
                    modifier = Modifier.clickable { showArrowDialog = true }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Stile cifre orologio") },
                    supportingContent = { Text(clockDigitStyle.label) },
                    leadingContent = {
                        Icon(Icons.Filled.Timer, contentDescription = "Orologio icon")
                    },
                    modifier = Modifier.clickable { showClockDialog = true }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Stile cifre glucosio") },
                    supportingContent = { Text(valueDigitStyle.label) },
                    leadingContent = {
                        Icon(Icons.Filled.Numbers, contentDescription = "Cifre icon")
                    },
                    modifier = Modifier.clickable { showValueDialog = true }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Durata sensore CGM") },
                    supportingContent = { Text("$sensorDurationDays giorni") },
                    leadingContent = {
                        Icon(Icons.Filled.Timer, contentDescription = "Durata icon")
                    },
                    modifier = Modifier.clickable { showSensorDialog = true }
                )
                Divider()
            }

            item {
                HeaderLine("Quadrante Dot Matrix (orologio)")
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Soglia glicemia bassa") },
                    supportingContent = { Text("Sotto $glucoseLowThreshold mg/dL, valore in rosso") },
                    leadingContent = {
                        Icon(Icons.Filled.WaterDrop, contentDescription = "Soglia glicemia bassa icon")
                    },
                    modifier = Modifier.clickable { showGlucoseLowDialog = true }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Soglia glicemia alta") },
                    supportingContent = { Text("Sopra $glucoseHighThreshold mg/dL, valore in rosso") },
                    leadingContent = {
                        Icon(Icons.Filled.WaterDrop, contentDescription = "Soglia glicemia alta icon")
                    },
                    modifier = Modifier.clickable { showGlucoseHighDialog = true }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Soglia batteria microinfusore") },
                    supportingContent = { Text("Sotto $batteryThreshold%, valore in rosso") },
                    leadingContent = {
                        Icon(Icons.Filled.BatteryAlert, contentDescription = "Soglia batteria icon")
                    },
                    modifier = Modifier.clickable { showBatteryThresholdDialog = true }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Soglia unità insulina attiva") },
                    supportingContent = { Text("Sotto ${"%.1f".format(iobThreshold)}U, valore in rosso") },
                    leadingContent = {
                        Icon(Icons.Filled.Opacity, contentDescription = "Soglia insulina icon")
                    },
                    modifier = Modifier.clickable { showIobThresholdDialog = true }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Soglia giorni scadenza sensore") },
                    supportingContent = { Text("Sotto $sensorDaysThreshold giorni, valore in rosso") },
                    leadingContent = {
                        Icon(Icons.Filled.Timer, contentDescription = "Soglia scadenza icon")
                    },
                    modifier = Modifier.clickable { showSensorDaysThresholdDialog = true }
                )
                Divider()
            }
        }
    )

    if (showArrowDialog) {
        EnumPickerDialog(
            title = "Stile freccia di tendenza",
            options = PixelFont.ArrowStyle.entries.filter { it in PixelFont.arrowSets },
            selected = arrowStyle,
            label = { it.label },
            preview = { style ->
                val arrows = PixelFont.arrowSets[style] ?: emptyMap()
                ARROW_PREVIEW_ORDER.mapNotNull { arrows[it] }
            },
            onSelect = {
                arrowStyle = it
                settings.arrowStyle = it
            },
            onDismiss = { showArrowDialog = false }
        )
    }
    if (showClockDialog) {
        EnumPickerDialog(
            title = "Stile cifre orologio",
            options = PixelFont.DigitStyle.entries.filter { it in PixelFont.clockDigitSets },
            selected = clockDigitStyle,
            label = { it.label },
            preview = { style ->
                val glyphs = PixelFont.clockDigitSets[style]?.glyphs ?: emptyMap()
                DIGIT_PREVIEW_ORDER.mapNotNull { glyphs[it] }
            },
            onSelect = {
                clockDigitStyle = it
                settings.clockDigitStyle = it
            },
            onDismiss = { showClockDialog = false }
        )
    }
    if (showValueDialog) {
        EnumPickerDialog(
            title = "Stile cifre glucosio",
            options = PixelFont.DigitStyle.entries.filter { it in PixelFont.valueDigitSets },
            selected = valueDigitStyle,
            label = { it.label },
            preview = { style ->
                val glyphs = PixelFont.valueDigitSets[style]?.glyphs ?: emptyMap()
                DIGIT_PREVIEW_ORDER.mapNotNull { glyphs[it] }
            },
            onSelect = {
                valueDigitStyle = it
                settings.valueDigitStyle = it
            },
            onDismiss = { showValueDialog = false }
        )
    }
    if (showSensorDialog) {
        EnumPickerDialog(
            title = "Durata sensore CGM",
            options = SENSOR_DURATION_OPTIONS,
            selected = sensorDurationDays,
            label = { "$it giorni" },
            onSelect = {
                sensorDurationDays = it
                settings.sensorDurationDays = it
            },
            onDismiss = { showSensorDialog = false }
        )
    }

    // Every threshold edit also pushes the full set to the watch: cheap (one small DataItem) and
    // avoids ever sending it out of sync with what the phone just saved.
    if (showGlucoseLowDialog) {
        NumberEditDialog(
            title = "Soglia glicemia bassa (mg/dL)",
            initialValue = glucoseLowThreshold.toString(),
            onConfirm = { text ->
                text.toIntOrNull()?.let {
                    glucoseLowThreshold = it
                    settings.glucoseLowThreshold = it
                    FaceThresholdsSync.push(context, settings)
                }
            },
            onDismiss = { showGlucoseLowDialog = false }
        )
    }
    if (showGlucoseHighDialog) {
        NumberEditDialog(
            title = "Soglia glicemia alta (mg/dL)",
            initialValue = glucoseHighThreshold.toString(),
            onConfirm = { text ->
                text.toIntOrNull()?.let {
                    glucoseHighThreshold = it
                    settings.glucoseHighThreshold = it
                    FaceThresholdsSync.push(context, settings)
                }
            },
            onDismiss = { showGlucoseHighDialog = false }
        )
    }
    if (showBatteryThresholdDialog) {
        NumberEditDialog(
            title = "Soglia batteria microinfusore (%)",
            initialValue = batteryThreshold.toString(),
            onConfirm = { text ->
                text.toIntOrNull()?.let {
                    batteryThreshold = it
                    settings.batteryThreshold = it
                    FaceThresholdsSync.push(context, settings)
                }
            },
            onDismiss = { showBatteryThresholdDialog = false }
        )
    }
    if (showIobThresholdDialog) {
        NumberEditDialog(
            title = "Soglia unità insulina attiva (U)",
            initialValue = iobThreshold.toString(),
            keyboardType = KeyboardType.Decimal,
            onConfirm = { text ->
                text.toFloatOrNull()?.let {
                    iobThreshold = it
                    settings.iobThreshold = it
                    FaceThresholdsSync.push(context, settings)
                }
            },
            onDismiss = { showIobThresholdDialog = false }
        )
    }
    if (showSensorDaysThresholdDialog) {
        NumberEditDialog(
            title = "Soglia giorni scadenza sensore",
            initialValue = sensorDaysThreshold.toString(),
            onConfirm = { text ->
                text.toIntOrNull()?.let {
                    sensorDaysThreshold = it
                    settings.sensorDaysThreshold = it
                    FaceThresholdsSync.push(context, settings)
                }
            },
            onDismiss = { showSensorDaysThresholdDialog = false }
        )
    }
}

/** A single numeric field in a dialog, used for the Dot Matrix face's red-accent thresholds --
 *  arbitrary integers/decimals across too wide a range to offer as a picker list. */
@Composable
private fun NumberEditDialog(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType = KeyboardType.Number,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(text)
                onDismiss()
            }) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

/** Renders a PixelFont pattern (list of "0110…" row strings) as a small lit-pixel grid,
 *  scaled so each lit cell is 4dp wide with 1dp gaps between cells. */
@Composable
private fun PixelGridPreview(pattern: List<String>, modifier: Modifier = Modifier) {
    val cellDp = 4.dp
    val gapDp = 1.dp
    val rows = pattern.size
    val cols = pattern.maxOfOrNull { it.length } ?: 0
    val width = cellDp * cols + gapDp * maxOf(0, cols - 1)
    val height = cellDp * rows + gapDp * maxOf(0, rows - 1)
    val litColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(width, height)) {
        val cellPx = cellDp.toPx()
        val gapPx = gapDp.toPx()
        pattern.forEachIndexed { row, line ->
            line.forEachIndexed { col, c ->
                if (c == '1') {
                    drawRect(
                        color = litColor,
                        topLeft = Offset(col * (cellPx + gapPx), row * (cellPx + gapPx)),
                        size = Size(cellPx, cellPx)
                    )
                }
            }
        }
    }
}

/** Lays out every glyph a style defines in rows of up to [perRow] side by side, wrapping to a
 *  new row rather than scrolling -- 10 digits in one row ran wider than the dialog and got
 *  clipped by its edge, so digit styles wrap to two rows of 5; arrow styles (5 glyphs) still
 *  fit on a single row. */
@Composable
private fun MultiGlyphPreview(patterns: List<List<String>>, modifier: Modifier = Modifier, perRow: Int = 5) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        patterns.chunked(perRow).forEach { rowPatterns ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowPatterns.forEach { pattern -> PixelGridPreview(pattern) }
            }
        }
    }
}

@Composable
private fun <T> EnumPickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    preview: ((T) -> List<List<String>>)? = null,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { option ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = {
                                    onSelect(option)
                                    onDismiss()
                                }
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = option == selected, onClick = null)
                            // With a full glyph-set preview available, "Stile N" adds nothing the
                            // preview doesn't already show more usefully -- so it's only shown for
                            // preview-less pickers (e.g. the sensor-duration dialog).
                            if (preview == null) {
                                Text(label(option), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        if (preview != null) {
                            MultiGlyphPreview(
                                patterns = preview(option),
                                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        }
    )
}
