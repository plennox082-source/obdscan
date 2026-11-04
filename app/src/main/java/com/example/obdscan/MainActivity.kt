package com.example.obdscan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    CyberBackground()
                    App()
                }
            }
        }
    }
}

@Composable
fun CyberBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = Color(0xFF080103))

        val gridColor = Color(0xFF2B080F)
        for (x in 0..((w / 50f).toInt() + 2)) {
            val px = x * 50f
            drawLine(gridColor, Offset(px, 0f), Offset(px, h), 1f)
        }
        for (y in 0..((h / 50f).toInt() + 2)) {
            val py = y * 50f
            drawLine(gridColor, Offset(0f, py), Offset(w, py), 1f)
        }

        val red = Color(0xFFFF003C)
        val lime = Color(0xFF39FF14)

        val leftCenter = Offset(160f, 110f)
        drawCircle(Color(0xFF1C0208), radius = 55f, center = leftCenter, style = Stroke(width = 1.5f))
        drawCircle(red, radius = 48f, center = leftCenter, style = Stroke(width = 3f))
        drawArc(
            red,
            startAngle = 190f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(leftCenter.x - 48f, leftCenter.y - 48f),
            size = Size(96f, 96f),
            style = Stroke(width = 4f)
        )
        drawLine(red, leftCenter, Offset(leftCenter.x + 32f, leftCenter.y - 22f), 2f)
        drawCircle(Color.White, radius = 4f, center = leftCenter)

        val rightCenter = Offset(1040f, 110f)
        drawCircle(Color(0xFF041202), radius = 55f, center = rightCenter, style = Stroke(width = 1.5f))
        drawCircle(lime, radius = 48f, center = rightCenter, style = Stroke(width = 2f))
        drawLine(lime, rightCenter, Offset(rightCenter.x + 10f, rightCenter.y - 38f), 2f)
        drawCircle(Color.White, radius = 4f, center = rightCenter)

        drawLine(Color(0xFFFF003C), Offset(230f, 110f), Offset(970f, 110f), 3f)
        drawLine(Color(0xFF39FF14), Offset(970f, 130f), Offset(230f, 130f), 3f)

        drawLine(Color(0xFFFF003C), Offset(230f, 110f), Offset(500f, 110f), 1.5f)
        drawLine(Color(0xFF39FF14), Offset(970f, 130f), Offset(700f, 130f), 1.5f)

        val core = Offset(600f, 160f)
        drawCircle(Color(0xFF2B080F), radius = 45f, center = core, style = Stroke(width = 2f))
        drawCircle(lime, radius = 35f, center = core, style = Stroke(width = 1f))
        drawLine(Color(0xFFFF003C), Offset(core.x - 15f, core.y), Offset(core.x + 15f, core.y), 2f)
        drawLine(Color(0xFF39FF14), Offset(core.x, core.y - 15f), Offset(core.x, core.y + 15f), 2f)

        val terminalTop = Offset(80f, 240f)
        drawRoundRect(
            color = Color(0xFF040001),
            topLeft = terminalTop,
            size = Size(1040f, 120f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
            style = Stroke(width = 1f)
        )

        drawLine(Color(0xFFFF003C), Offset(80f, 240f), Offset(1120f, 240f), 1f)
        drawLine(Color(0xFF39FF14), Offset(80f, 240f), Offset(1120f, 240f), 1f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val elm = remember { Elm327() }
    var tab by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Not connected") }
    var connected by remember { mutableStateOf(false) }
    val tabs = listOf("Connect", "DTCs", "Live", "Tests", "Console")

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0x33000000),
                contentColor = Color.White
            ) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(t) })
                }
            }
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(12.dp)
        ) {
            Text(status, style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> ConnectTab(elm) { s, c -> status = s; connected = c }
                1 -> DtcTab(elm, connected)
                2 -> LiveTab(elm, connected)
                3 -> TestsTab(elm, connected)
                4 -> ConsoleTab(elm, connected)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectTab(elm: Elm327, onStatus: (String, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf(listOf<BluetoothDevice>()) }

    fun load() {
        val bm = ctx.getSystemService(BluetoothManager::class.java)
        devices = bm?.adapter?.bondedDevices?.toList() ?: emptyList()
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) load() else onStatus("Bluetooth permission denied", false)
    }

    LaunchedEffect(Unit) {
        val needs = Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needs) perm.launch(Manifest.permission.BLUETOOTH_CONNECT) else load()
    }

    Text("Pair the adapter first in Android Bluetooth settings (PIN usually 1234 or 0000), then pick it below. Ignition on, engine off or running.", color = Color.White)
    Spacer(Modifier.height(8.dp))
    Button(onClick = { load() }) { Text("Refresh paired devices") }
    Spacer(Modifier.height(8.dp))
    LazyColumn {
        items(devices) { d ->
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        onStatus("Connecting…", false)
                        try {
                            elm.connect(d)
                            onStatus("Connected: ${d.name} · protocol ${elm.protocol}", true)
                        } catch (e: Exception) {
                            onStatus("Failed: ${e.message}", false)
                        }
                    }
                }
            ) { Text("${d.name ?: "Unknown"}  ${d.address}") }
        }
    }
}

@Composable
fun DtcTab(elm: Elm327, connected: Boolean) {
    val scope = rememberCoroutineScope()
    var out by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    suspend fun read(label: String, cmd: String, mode: Int): String {
        val raw = elm.send(cmd, 5000)
        if (raw.contains("NO DATA") || raw.isBlank()) return "$label: none\n"
        val codes = Obd.parseDtcs(raw, mode, elm.isCan)
        return if (codes.isEmpty()) "$label: none\n" else "$label: ${codes.joinToString(", ")}\n"
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = connected, onClick = {
            scope.launch {
                out = "Reading…"
                try {
                    out = read("Stored", "03", 0x43) + read("Pending", "07", 0x47) + read("Permanent", "0A", 0x4A)
                } catch (e: Exception) { out = "Error: ${e.message}" }
            }
        }) { Text("Read codes") }
        OutlinedButton(enabled = connected, onClick = { confirmClear = true }) { Text("Clear codes") }
    }
    Spacer(Modifier.height(12.dp))
    SelectionContainer { Text(out, fontFamily = FontFamily.Monospace, color = Color.White) }
    Spacer(Modifier.height(8.dp))
    Text("Code descriptions are not bundled yet. Search the code (e.g. P0301) for your make.", style = MaterialTheme.typography.bodySmall, color = Color.White)

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear codes?") },
            text = { Text("This erases stored codes and freeze frame data and resets readiness monitors. The car will not pass an emissions test until monitors complete again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        out = try { "Clear response: " + elm.send("04", 5000) } catch (e: Exception) { "Error: ${e.message}" }
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun LiveTab(elm: Elm327, connected: Boolean) {
    var running by remember { mutableStateOf(false) }
    var rpm by remember { mutableStateOf<Int?>(null) }
    var speed by remember { mutableStateOf<Int?>(null) }
    var coolant by remember { mutableStateOf<Int?>(null) }
    var throttle by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(running, connected) {
        while (running && connected) {
            try {
                rpm = Obd.rpm(elm.send("010C"))
                speed = Obd.speedKmh(elm.send("010D"))
                coolant = Obd.coolantC(elm.send("0105"))
                throttle = Obd.throttlePct(elm.send("0111"))
            } catch (_: Exception) { running = false }
            delay(300)
        }
    }

    Button(enabled = connected, onClick = { running = !running }) { Text(if (running) "Stop" else "Start") }
    Spacer(Modifier.height(12.dp))
    fun f(v: Int?, unit: String) = if (v == null) "—" else "$v $unit"
    Text("RPM: ${f(rpm, "rpm")}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text("Speed: ${f(speed, "km/h")}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text("Coolant: ${f(coolant, "°C")}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text("Throttle: ${f(throttle, "%")}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
}

@Composable
fun ColumnScope.TestsTab(elm: Elm327, connected: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(Profiles.loadAll(ctx)) }
    var selected by remember { mutableIntStateOf(0) }
    var log by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ObdTest?>(null) }
    var lastRun by remember { mutableStateOf<ObdTest?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val p = Profiles.import(ctx, json)
                profiles = Profiles.loadAll(ctx)
                selected = profiles.indexOfFirst { it.name == p.name }.coerceAtLeast(0)
                log = "Imported profile: ${p.name}"
            } catch (e: Exception) { log = "Import failed: ${e.message}" }
        }
    }

    suspend fun resetBus() {
        elm.send("ATCRA"); elm.send("ATSH7DF"); elm.send("ATAR")
    }

    suspend fun runSteps(test: ObdTest, steps: List<String>) {
        if (test.header.isNotEmpty()) elm.send("ATSH${test.header}")
        if (test.rx.isNotEmpty()) elm.send("ATCRA${test.rx}")
        for (s in steps) {
            log += "> $s\n${elm.send(s, 4000)}\n"
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import profile") }
        OutlinedButton(onClick = { profiles = Profiles.loadAll(ctx) }) { Text("Reload") }
    }
    Spacer(Modifier.height(8.dp))

    if (profiles.isEmpty()) {
        Text("No profiles found.", color = Color.White)
    } else {
        val prof = profiles[selected.coerceIn(0, profiles.lastIndex)]
        var menu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { menu = true }) { Text("Profile: ${prof.name}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                profiles.forEachIndexed { i, p ->
                    DropdownMenuItem(text = { Text(p.name) }, onClick = { selected = i; menu = false })
                }
            }
        }
        if (prof.notes.isNotBlank()) Text(prof.notes, style = MaterialTheme.typography.bodySmall, color = Color.White)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(prof.tests) { t ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        if (t.warning.isNotBlank()) Text(t.warning, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = connected, onClick = { pending = t }) { Text("Run") }
                            OutlinedButton(enabled = connected, onClick = {
                                scope.launch {
                                    try { runSteps(t, t.stop); resetBus(); log += "Stopped / released.\n" }
                                    catch (e: Exception) { log += "Error: ${e.message}\n" }
                                }
                            }) { Text("Stop") }
                        }
                    }
                }
            }
        }
    }

    Text("Log", style = MaterialTheme.typography.labelLarge, color = Color.White)
    Box(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
        SelectionContainer { Text(log, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Color.White) }
    }

    pending?.let { t ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Run: ${t.title}") },
            text = { Text((t.warning.ifBlank { "No warning provided." }) + "\n\nOnly continue if the car is parked safely and this command is verified for your vehicle.") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    lastRun = t
                    scope.launch {
                        try { log += "== ${t.title} ==\n"; runSteps(t, t.steps) }
                        catch (e: Exception) { log += "Error: ${e.message}\n" }
                    }
                }) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ColumnScope.ConsoleTab(elm: Elm327, connected: Boolean) {
    val scope = rememberCoroutineScope()
    var cmd by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }

    OutlinedTextField(value = cmd, onValueChange = { cmd = it.uppercase() }, label = { Text("AT or hex command") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(8.dp))
    Button(enabled = connected && cmd.isNotBlank(), onClick = {
        val c = cmd.trim()
        scope.launch {
            log += "> $c\n"
            log += try { elm.send(c, 4000) + "\n" } catch (e: Exception) { "Error: ${e.message}\n" }
        }
    }) { Text("Send") }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        SelectionContainer { Text(log, fontFamily = FontFamily.Monospace, color = Color.White) }
    }
}
