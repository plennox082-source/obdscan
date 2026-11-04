package com.example.obdscan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
        setContent { MaterialTheme { BackgroundSwitcher { App() } } }
    }
}

@Composable
fun CyberBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF080103))
        val grid = Color(0xFF2B080F)
        for (x in 0..((w / 50f).toInt() + 2)) drawLine(grid, Offset(x * 50f, 0f), Offset(x * 50f, h), 1f)
        for (y in 0..((h / 50f).toInt() + 2)) drawLine(grid, Offset(0f, y * 50f), Offset(w, y * 50f), 1f)
        val red = Color(0xFFFF003C)
        val lime = Color(0xFF39FF14)
        val left = Offset(160f, 110f)
        drawCircle(Color(0xFF1C0208), 55f, left, style = Stroke(1.5f))
        drawCircle(red, 48f, left, style = Stroke(3f))
        drawArc(red, 190f, 120f, false, Offset(left.x - 48f, left.y - 48f), Size(96f, 96f), style = Stroke(4f))
        drawLine(red, left, Offset(left.x + 32f, left.y - 22f), 2f)
        drawCircle(Color.White, 4f, left)
        val right = Offset(1040f, 110f)
        drawCircle(Color(0xFF041202), 55f, right, style = Stroke(1.5f))
        drawCircle(lime, 48f, right, style = Stroke(2f))
        drawLine(lime, right, Offset(right.x + 10f, right.y - 38f), 2f)
        drawCircle(Color.White, 4f, right)
        drawLine(red, Offset(230f, 110f), Offset(970f, 110f), 3f)
        drawLine(lime, Offset(970f, 130f), Offset(230f, 130f), 3f)
        val core = Offset(600f, 160f)
        drawCircle(Color(0xFF2B080F), 45f, core, style = Stroke(2f))
        drawCircle(lime, 35f, core, style = Stroke(1f))
        drawLine(red, Offset(core.x - 15f, core.y), Offset(core.x + 15f, core.y), 2f)
        drawLine(lime, Offset(core.x, core.y - 15f), Offset(core.x, core.y + 15f), 2f)
        drawRoundRect(Color(0xFF040001), Offset(80f, 240f), Size(1040f, 120f), androidx.compose.ui.geometry.CornerRadius(4f, 4f), style = Stroke(1f))
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
            NavigationBar(containerColor = Color(0x33000000), contentColor = Color.White) {
                tabs.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(12.dp).fillMaxSize()) {
            Text(status, style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> ConnectTab(elm) { text, isConnected -> status = text; connected = isConnected }
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    fun load() { devices = context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.toList() ?: emptyList() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) load() else onStatus("Bluetooth permission denied", false)
    }
    LaunchedEffect(Unit) {
        val required = Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (required) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) else load()
    }
    Text("Pair the adapter first in Android Bluetooth settings (PIN usually 1234 or 0000), then pick it below. Ignition on, engine off or running.", color = Color.White)
    Spacer(Modifier.height(8.dp))
    Button(onClick = { load() }) { Text("Refresh paired devices") }
    Spacer(Modifier.height(8.dp))
    LazyColumn {
        items(devices) { device ->
            OutlinedButton(
                onClick = {
                    scope.launch {
                        onStatus("Connecting…", false)
                        try { elm.connect(device); onStatus("Connected: ${device.name} · protocol ${elm.protocol}", true) }
                        catch (e: Exception) { onStatus("Failed: ${e.message}", false) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("${device.name ?: "Unknown"}  ${device.address}") }
        }
    }
}

@Composable
fun DtcTab(elm: Elm327, connected: Boolean) {
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    suspend fun read(label: String, command: String, mode: Int): String {
        val raw = elm.send(command, 5000)
        if (raw.contains("NO DATA") || raw.isBlank()) return "$label: none\n"
        val codes = Obd.parseDtcs(raw, mode, elm.isCan)
        return if (codes.isEmpty()) "$label: none\n" else "$label: ${codes.joinToString(", ")}\n"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = connected, onClick = { scope.launch {
            output = "Reading…"
            try { output = read("Stored", "03", 0x43) + read("Pending", "07", 0x47) + read("Permanent", "0A", 0x4A) }
            catch (e: Exception) { output = "Error: ${e.message}" }
        } }) { Text("Read codes") }
        OutlinedButton(enabled = connected, onClick = { confirmClear = true }) { Text("Clear codes") }
    }
    Spacer(Modifier.height(12.dp))
    SelectionContainer { Text(output, fontFamily = FontFamily.Monospace, color = Color.White) }
    Spacer(Modifier.height(8.dp))
    Text("Code descriptions are available from the Mechanic helper. Search the code (e.g. P0301) for your make.", style = MaterialTheme.typography.bodySmall, color = Color.White)
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear codes?") },
        text = { Text("This erases stored codes and freeze frame data and resets readiness monitors. The car will not pass an emissions test until monitors complete again.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; scope.launch { output = try { "Clear response: " + elm.send("04", 5000) } catch (e: Exception) { "Error: ${e.message}" } } }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
    )
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
                rpm = Obd.rpm(elm.send("010C")); speed = Obd.speedKmh(elm.send("010D"))
                coolant = Obd.coolantC(elm.send("0105")); throttle = Obd.throttlePct(elm.send("0111"))
            } catch (_: Exception) { running = false }
            delay(300)
        }
    }
    Button(enabled = connected, onClick = { running = !running }) { Text(if (running) "Stop" else "Start") }
    Spacer(Modifier.height(12.dp))
    fun format(value: Int?, unit: String) = if (value == null) "—" else "$value $unit"
    Text("RPM: ${format(rpm, "rpm")}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text("Speed: ${format(speed, "km/h")}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text("Coolant: ${format(coolant, "°C")}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text("Throttle: ${format(throttle, "%")}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
}

@Composable
fun ColumnScope.TestsTab(elm: Elm327, connected: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(Profiles.loadAll(context)) }
    var selected by remember { mutableIntStateOf(0) }
    var log by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ObdTest?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) try {
            val json = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val profile = Profiles.import(context, json)
            profiles = Profiles.loadAll(context)
            selected = profiles.indexOfFirst { it.name == profile.name }.coerceAtLeast(0)
            log = "Imported profile: ${profile.name}"
        } catch (e: Exception) { log = "Import failed: ${e.message}" }
    }
    suspend fun resetBus() { elm.send("ATCRA"); elm.send("ATSH7DF"); elm.send("ATAR") }
    suspend fun runSteps(test: ObdTest, steps: List<String>) {
        if (test.header.isNotEmpty()) elm.send("ATSH${test.header}")
        if (test.rx.isNotEmpty()) elm.send("ATCRA${test.rx}")
        for (step in steps) log += "> $step\n${elm.send(step, 4000)}\n"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import profile") }
        OutlinedButton(onClick = { profiles = Profiles.loadAll(context) }) { Text("Reload") }
    }
    Spacer(Modifier.height(8.dp))
    if (profiles.isEmpty()) Text("No profiles found.", color = Color.White) else {
        val profile = profiles[selected.coerceIn(0, profiles.lastIndex)]
        var menu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { menu = true }) { Text("Profile: ${profile.name}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                profiles.forEachIndexed { index, item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { selected = index; menu = false }) }
            }
        }
        if (profile.notes.isNotBlank()) Text(profile.notes, style = MaterialTheme.typography.bodySmall, color = Color.White)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(profile.tests) { test ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(test.title, style = MaterialTheme.typography.titleMedium)
                        if (test.warning.isNotBlank()) Text(test.warning, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = connected, onClick = { pending = test }) { Text("Run") }
                            OutlinedButton(enabled = connected, onClick = { scope.launch {
                                try { runSteps(test, test.stop); resetBus(); log += "Stopped / released.\n" }
                                catch (e: Exception) { log += "Error: ${e.message}\n" }
                            } }) { Text("Stop") }
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
    pending?.let { test -> AlertDialog(
        onDismissRequest = { pending = null },
        title = { Text("Run: ${test.title}") },
        text = { Text((test.warning.ifBlank { "No warning provided." }) + "\n\nOnly continue if the car is parked safely and this command is verified for your vehicle.") },
        confirmButton = { TextButton(onClick = { pending = null; scope.launch { try { log += "== ${test.title} ==\n"; runSteps(test, test.steps) } catch (e: Exception) { log += "Error: ${e.message}\n" } } }) { Text("Run") } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
    ) }
}

@Composable
fun ColumnScope.ConsoleTab(elm: Elm327, connected: Boolean) {
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }
    OutlinedTextField(value = command, onValueChange = { command = it.uppercase() }, label = { Text("AT or hex command") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(8.dp))
    Button(enabled = connected && command.isNotBlank(), onClick = {
        val sent = command.trim()
        scope.launch { log += "> $sent\n"; log += try { elm.send(sent, 4000) + "\n" } catch (e: Exception) { "Error: ${e.message}\n" } }
    }) { Text("Send") }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        SelectionContainer { Text(log, fontFamily = FontFamily.Monospace, color = Color.White) }
    }
}
