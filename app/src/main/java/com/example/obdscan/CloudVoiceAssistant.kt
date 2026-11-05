package com.example.obdscan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class CrewLine(val character: String, val text: String)

/** Voice-only UI: speech goes to the online Worker; the reply is spoken, never rendered as chat text. */
@Composable
fun CloudVoiceAssistant() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("voice_assistant", android.content.Context.MODE_PRIVATE) }
    var open by remember { mutableStateOf(false) }
    var workerUrl by remember { mutableStateOf(preferences.getString("worker_url", "") ?: "") }
    var status by remember { mutableStateOf("Ready") }
    var busy by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }

    val tts = remember {
        TextToSpeech(context) { result -> ttsReady = result == TextToSpeech.SUCCESS }
    }
    DisposableEffect(tts) {
        onDispose { tts.stop(); tts.shutdown() }
    }

    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spokenPrompt = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spokenPrompt.isNullOrBlank()) {
            val endpoint = workerUrl.trim()
            val parsed = runCatching { URL(endpoint) }.getOrNull()
            if (parsed == null || parsed.protocol != "https" || parsed.host.isBlank()) {
                status = "Set the Worker URL in setup first"
            } else {
                busy = true
                status = "Pat and the crew are thinking…"
                scope.launch {
                    try {
                        val lines = withContext(Dispatchers.IO) { requestCrew(endpoint, spokenPrompt.trim()) }
                        if (ttsReady) {
                            status = "The crew is speaking"
                            speakCrew(tts, lines)
                        } else {
                            status = "Speech output is unavailable on this device"
                        }
                    } catch (e: Exception) {
                        status = e.message ?: "Could not reach the online assistant"
                    } finally {
                        busy = false
                    }
                }
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            status = "Ready"
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { if (!busy) { tts.stop(); open = false } },
            title = { Text("Mechanic crew — voice only") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Speak to the online AI crew. Replies are spoken aloud and not displayed as text.")
                    OutlinedTextField(
                        value = workerUrl,
                        onValueChange = {
                            workerUrl = it.trim()
                            preferences.edit().putString("worker_url", workerUrl).apply()
                        },
                        label = { Text("Cloudflare Worker HTTPS URL") },
                        placeholder = { Text("https://your-worker.example.workers.dev") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Status: $status", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                tts.stop()
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask the mechanic crew")
                                }
                                status = "Listening…"
                                runCatching { voiceInput.launch(intent) }
                                    .onFailure { status = "Voice input unavailable; check your Android speech service" }
                            }
                        ) { Text("🎙 Talk") }
                        OutlinedButton(onClick = {
                            tts.stop()
                            status = "Speech stopped"
                        }) { Text("Stop") }
                    }
                    Text("Pat leads; Adam, Steve, and Jesse each add their own take.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { if (!busy) { tts.stop(); open = false } }) { Text("Close") } }
        )
    } else {
        FloatingActionButton(
            onClick = { open = true },
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 216.dp),
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary
        ) { Text("Crew") }
    }
}

private fun speakCrew(tts: TextToSpeech, lines: List<CrewLine>) {
    lines.forEachIndexed { index, line ->
        // TextToSpeech uses the documented string keys for pitch and speaking rate.
        val settings = Bundle().apply {
            putFloat("pitch", line.character.pitch)
            putFloat("rate", line.character.rate)
        }
        tts.speak(
            "${line.character}: ${line.text}",
            if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            settings,
            "crew_${index}_${line.character}"
        )
    }
}

private val String.pitch: Float
    get() = when (this) {
        "Steve" -> 0.88f
        "Adam" -> 0.94f
        "Jesse" -> 1.18f
        else -> 0.82f
    }

private val String.rate: Float
    get() = when (this) {
        "Steve" -> 0.92f
        "Adam" -> 0.88f
        "Jesse" -> 1.12f
        else -> 0.96f
    }

private suspend fun requestCrew(endpoint: String, prompt: String): List<CrewLine> {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 45_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
    }
    try {
        val body = JSONObject().put("prompt", prompt).toString()
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException("Online AI returned HTTP $code. Check Worker setup and free-plan availability.")
        }
        val json = JSONObject(responseBody)
        val result = json.optJSONObject("data")?.optString("response").orEmpty()
        if (!json.optBoolean("success") || result.isBlank()) {
            throw IllegalStateException("The Worker returned an empty or unexpected response")
        }
        return parseCrewLines(result)
    } finally {
        connection.disconnect()
    }
}

private fun parseCrewLines(raw: String): List<CrewLine> {
    val names = listOf("Pat", "Adam", "Steve", "Jesse")
    val matcher = Regex("(?im)^\\s*(Pat|Adam|Steve|Jesse)\\s*:\\s*(.+?)\\s*$")
    val parsed = matcher.findAll(raw)
        .map { CrewLine(it.groupValues[1].replaceFirstChar { c -> c.uppercase() }, it.groupValues[2].trim()) }
        .distinctBy { it.character }
        .associateBy { it.character }
    if (parsed.isEmpty()) return listOf(CrewLine("Pat", raw.trim()))
    return names.mapNotNull(parsed::get)
}
