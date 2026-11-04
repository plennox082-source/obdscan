package com.example.obdscan

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Tap-to-talk Cloudflare voice assistant. The user supplies their deployed Worker URL. */
@Composable
fun CloudVoiceAssistant() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var workerUrl by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Tap the microphone or type a question for Pat.") }
    var crewComments by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
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
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            prompt = spoken
            status = "Voice input received"
        }
    }

    Box(Modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = { open = true },
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 16.dp, bottom = 216.dp),
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary
        ) { Text("Pat") }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { if (!busy) open = false },
            title = { Text("Pat — Cloud voice assistant") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tap-to-talk sends your recognized text to your Cloudflare Worker. Do not enter secrets or sensitive personal information.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = workerUrl,
                        onValueChange = { workerUrl = it.trim() },
                        label = { Text("Cloudflare Worker URL") },
                        placeholder = { Text("https://your-worker.your-subdomain.workers.dev") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Your question") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Pat a question")
                                }
                                runCatching { voiceInput.launch(intent) }
                                    .onFailure { status = "Voice input is unavailable; type your question instead." }
                            }
                        ) { Text("🎙 Speak") }
                        Button(
                            enabled = !busy && prompt.isNotBlank(),
                            onClick = {
                                val endpoint = workerUrl.trim()
                                val question = prompt.trim()
                                val parsedUrl = runCatching { URL(endpoint) }.getOrNull()
                                if (parsedUrl == null || parsedUrl.protocol != "https" || parsedUrl.host.isBlank()) {
                                    status = "Enter a valid HTTPS Worker URL."
                                } else {
                                    busy = true
                                    status = "Pat is thinking…"
                                    answer = "Connecting to Cloudflare…"
                                    crewComments = emptyList()
                                    scope.launch {
                                        try {
                                            val reply = withContext(Dispatchers.IO) { requestWorker(endpoint, question) }
                                            val banter = makeCrewBanter()
                                            answer = reply
                                            crewComments = banter
                                            status = "Pat and the crew are done"
                                            if (ttsReady) tts.speak(buildSpokenResponse(reply, banter), TextToSpeech.QUEUE_FLUSH, null, "pat_and_crew_reply")
                                        } catch (e: Exception) {
                                            answer = e.message ?: "Could not reach the Worker. Check the URL and connection."
                                            crewComments = emptyList()
                                            status = "Request failed"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            }
                        ) { Text(if (busy) "Working…" else "Ask Pat") }
                    }
                    Text("Status: $status", style = MaterialTheme.typography.labelMedium)
                    Text("Pat's response", style = MaterialTheme.typography.labelSmall)
                    Text(answer, style = MaterialTheme.typography.bodyMedium)
                    if (crewComments.isNotEmpty()) {
                        Text("Adam, Steve, and Jesse", style = MaterialTheme.typography.labelMedium)
                        crewComments.forEach { (name, comment) ->
                            Text("$name: $comment", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (ttsReady) {
                        OutlinedButton(onClick = {
                            if (answer.isNotBlank()) tts.speak(buildSpokenResponse(answer, crewComments), TextToSpeech.QUEUE_FLUSH, null, "pat_and_crew_reply")
                        }) { Text("Read response and crew comments aloud") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (!busy) open = false }) { Text("Close") } }
        )
    }
}

private fun buildSpokenResponse(reply: String, comments: List<Pair<String, String>>): String = buildString {
    append("Pat says: ").append(reply)
    comments.forEach { (name, comment) -> append(". ").append(name).append(" says: ").append(comment) }
}

/** Each crew member gets a fresh, randomly selected supportive line after Pat answers. */
private fun makeCrewBanter(): List<Pair<String, String>> = listOf(
    "Adam" to listOf(
        "Nice one, Pat. Clear and to the point.",
        "Good work, Pat. That explanation was easy to follow.",
        "Solid answer, Pat. The crew is on the same page."
    ).random(),
    "Steve" to listOf(
        "Well I'll be, Pat got it sorted. Good job.",
        "That's a useful answer, Pat. You earned the coffee break.",
        "Good call, Pat. Let's get the right tools and check it out."
    ).random(),
    "Jesse" to listOf(
        "Nice, Pat! That makes sense now.",
        "Pat came through with the answer. Teamwork for the win!",
        "Great explanation, Pat. I actually remembered the whole thing."
    ).random()
)

private fun requestWorker(endpoint: String, prompt: String): String {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 30_000
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
        if (code !in 200..299) throw IllegalStateException("Worker returned HTTP $code${responseBody.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")
        val json = JSONObject(responseBody)
        val reply = json.optJSONObject("data")?.optString("response").orEmpty()
        if (!json.optBoolean("success") || reply.isBlank()) {
            throw IllegalStateException("Worker response did not match the expected { success, data: { response } } format.")
        }
        return reply
    } finally {
        connection.disconnect()
    }
}
