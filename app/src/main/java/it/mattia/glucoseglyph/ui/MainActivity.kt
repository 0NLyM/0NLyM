package it.mattia.glucoseglyph.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import it.mattia.glucoseglyph.model.AppSettings
import it.mattia.glucoseglyph.model.GlucoseState
import it.mattia.glucoseglyph.glyph.MatrixRenderer
import it.mattia.glucoseglyph.net.ControlX2Client
import it.mattia.glucoseglyph.service.GlucosePollingService
import it.mattia.glucoseglyph.ui.theme.GlucoseGlyphTheme
import it.mattia.glucoseglyph.ui.theme.NothingGrey
import it.mattia.glucoseglyph.ui.theme.NothingRed
import it.mattia.glucoseglyph.ui.theme.NothingWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var settings: AppSettings

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* if denied, the foreground service still runs, just silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        setContent {
            GlucoseGlyphTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        settings = settings,
                        onStartService = ::startPollingService,
                        onStopService = ::stopPollingService
                    )
                }
            }
        }
    }

    private fun startPollingService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        startForegroundService(Intent(this, GlucosePollingService::class.java))
    }

    private fun stopPollingService() {
        startService(Intent(this, GlucosePollingService::class.java).setAction(GlucosePollingService.ACTION_STOP))
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    var host by remember { mutableStateOf(settings.host) }
    var port by remember { mutableStateOf(settings.port.toString()) }
    var username by remember { mutableStateOf(settings.username) }
    var password by remember { mutableStateOf(settings.password) }
    var pollInterval by remember { mutableStateOf(settings.pollIntervalSeconds.toString()) }
    var useMmol by remember { mutableStateOf(settings.useMmol) }
    var serviceOn by remember { mutableStateOf(settings.serviceEnabled) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Live-refresh the matrix preview and status text as new readings arrive.
    var tick by remember { mutableStateOf(0L) }
    DisposableEffect(Unit) {
        val listener = { tick = System.currentTimeMillis() }
        GlucoseState.addListener(listener)
        onDispose { GlucoseState.removeListener(listener) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Text(
            text = "GLUCOSE",
            style = MaterialTheme.typography.headlineMedium,
            color = NothingWhite
        )
        Row {
            Text(
                text = "GLYPH",
                style = MaterialTheme.typography.headlineMedium,
                color = NothingRed
            )
        }

        Spacer(Modifier.height(24.dp))

        MatrixPreview(reading = GlucoseState.current, useMmol = useMmol, tick = tick)

        Spacer(Modifier.height(8.dp))
        StatusLine(tick = tick)

        Spacer(Modifier.height(28.dp))
        SectionLabel("CONTROLX2")
        SettingsCard {
            LabeledField("Host", host) { host = it; settings.host = it }
            LabeledField("Porta", port, KeyboardType.Number) {
                port = it
                it.toIntOrNull()?.let { p -> settings.port = p }
            }
            LabeledField("Utente", username) { username = it; settings.username = it }
            LabeledField("Password", password, isPassword = true) {
                password = it; settings.password = it
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ControlX2Client(host, port.toIntOrNull() ?: 18282, username, password)
                                    .testConnection()
                            }
                            testing = false
                            testResult = when (result) {
                                is ControlX2Client.FetchResult.Success -> "OK — connesso"
                                is ControlX2Client.FetchResult.Failure -> result.message
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
                    enabled = !testing
                ) {
                    Text(if (testing) "..." else "Testa connessione")
                }
                testResult?.let {
                    Spacer(Modifier.width(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = NothingGrey)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("VISUALIZZAZIONE")
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unità", style = MaterialTheme.typography.bodyLarge, color = NothingWhite)
                Row {
                    UnitChip("mg/dL", selected = !useMmol) {
                        useMmol = false; settings.useMmol = false
                    }
                    Spacer(Modifier.width(8.dp))
                    UnitChip("mmol/L", selected = useMmol) {
                        useMmol = true; settings.useMmol = true
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LabeledField("Intervallo di aggiornamento (secondi)", pollInterval, KeyboardType.Number) {
                pollInterval = it
                it.toIntOrNull()?.let { s -> settings.pollIntervalSeconds = s.coerceIn(15, 900) }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("SERVIZIO")
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Lettura in background", style = MaterialTheme.typography.bodyLarge, color = NothingWhite)
                    Text(
                        "Interroga ControlX2 e aggiorna il Glyph Matrix",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingGrey
                    )
                }
                Switch(
                    checked = serviceOn,
                    onCheckedChange = {
                        serviceOn = it
                        if (it) onStartService() else onStopService()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = NothingRed)
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Apri ControlX2 → Impostazioni → Debug → HTTP API per abilitare l'API " +
                "locale (stesse credenziali qui sopra). Poi apri il pannello Glyph Toys " +
                "sul retro del telefono e scegli \"Glucose\".",
            style = MaterialTheme.typography.labelSmall,
            color = NothingGrey
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = NothingGrey,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = NothingGrey) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = NothingWhite,
            unfocusedTextColor = NothingWhite,
            focusedBorderColor = NothingRed,
            unfocusedBorderColor = NothingGrey,
            cursorColor = NothingRed
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) NothingRed else Color.Transparent,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) NothingWhite else NothingGrey,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun StatusLine(tick: Long) {
    val reading = GlucoseState.current
    val error = GlucoseState.lastError
    val text = when {
        error != null -> "Errore: $error"
        reading == null -> "Nessuna lettura ancora"
        else -> {
            val ageSec = ((System.currentTimeMillis() - reading.receivedEpochMillis) / 1000).coerceAtLeast(0)
            "${reading.mgdl} mg/dL · aggiornato ${formatAge(ageSec)} fa"
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = NothingGrey)
}

private fun formatAge(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m"
    else -> "${seconds / 3600}h"
}

@Composable
private fun MatrixPreview(
    reading: it.mattia.glucoseglyph.model.GlucoseReading?,
    useMmol: Boolean,
    tick: Long
) {
    val grid = remember(reading, useMmol, tick) {
        MatrixRenderer.render(reading, useMmol)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.Black, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSize = MatrixRenderer.SIZE
            val cell = size.minDimension / gridSize
            val gap = cell * 0.18f
            val dot = cell - gap
            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    val brightness = grid[row * gridSize + col]
                    if (brightness <= 0) continue
                    val alpha = (brightness / 255f).coerceIn(0.12f, 1f)
                    drawCircle(
                        color = Color(1f, 1f, 1f, alpha),
                        radius = dot / 2f,
                        center = Offset(
                            x = col * cell + cell / 2f,
                            y = row * cell + cell / 2f
                        )
                    )
                }
            }
        }
    }
}
