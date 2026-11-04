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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
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
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(t) })
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp).fillMaxSize()) {
            Text(status, style = MaterialTheme.typography.labelLarge)
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

    Text("Pair the adapter first in Android Bluetooth settings (PIN usually 1234 or 0000), then pick it below. Ignition on, engine off or running.")
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
    SelectionContainer { Text(out, fontFamily = FontFamily.Monospace) }
    Spacer(Modifier.height(8.dp))
    Text("Code descriptions are not bundled yet. Search the code (e.g. P0301) for your make.", style = MaterialTheme.typography.bodySmall)

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
    Text("RPM: ${f(rpm, "rpm")}", style = MaterialTheme.typography.headlineSmall)
    Text("Speed: ${f(speed, "km/h")}", style = MaterialTheme.typography.headlineSmall)
    Text("Coolant: ${f(coolant, "°C")}", style = MaterialTheme.typography.headlineSmall)
    Text("Throttle: ${f(throttle, "%")}", style = MaterialTheme.typography.headlineSmall)
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
        Text("No profiles found.")
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
        if (prof.notes.isNotBlank()) Text(prof.notes, style = MaterialTheme.typography.bodySmall)
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

    Text("Log", style = MaterialTheme.typography.labelLarge)
    Box(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
        SelectionContainer { Text(log, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
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
        SelectionContainer { Text(log, fontFamily = FontFamily.Monospace) }
    }
}
