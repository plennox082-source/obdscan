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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

/** A free, on-device mechanic helper with optional Android speech recognition and speech output. */
@Composable
fun MechanicAssistant() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Enter a trouble code (for example P0301) or describe the symptoms. Add the vehicle details for context.") }
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    DisposableEffect(tts) {
        onDispose { tts.stop(); tts.shutdown() }
    }

    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) question = spoken
    }

    Box(Modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = { open = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 152.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) { Text("Ask") }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Mechanic helper") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Free, on-device code and symptom guide. It does not send vehicle data to a service and is not a substitute for a scan or professional diagnosis.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = year, onValueChange = { year = it.take(4) }, label = { Text("Year") }, modifier = Modifier.weight(0.7f), singleLine = true)
                        OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make") }, modifier = Modifier.weight(1.2f), singleLine = true)
                    }
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Trouble code or symptoms") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the vehicle problem or say a trouble code")
                            }
                            runCatching { voiceInput.launch(intent) }
                                .onFailure { answer = "Voice input is unavailable on this device. You can type the code or symptoms instead." }
                        }) { Text("Speak") }
                        Button(onClick = {
                            answer = diagnose(question, year, make, model)
                        }) { Text("Diagnose") }
                    }
                    Text(answer, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = {
                            if (ttsReady) tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "mechanic_answer")
                        },
                        enabled = ttsReady
                    ) { Text("Read answer aloud") }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
        )
    }
}

private data class CodeGuide(val summary: String, val commonCauses: String, val checks: String)

private val commonCodeGuides = mapOf(
    "P0300" to CodeGuide("Random/multiple-cylinder misfire detected.", "Common causes include worn spark plugs, a failing ignition coil, a vacuum leak, or a fuel-delivery issue.", "Check for other codes and inspect ignition and intake components. A flashing check-engine light can indicate a catalyst-damaging misfire; avoid driving and get it checked promptly."),
    "P0301" to CodeGuide("Cylinder 1 misfire detected.", "Often caused by a worn spark plug, ignition coil, injector, or a compression problem on cylinder 1.", "Compare cylinder-specific symptoms and inspect the plug/coil before replacing parts. The cylinder location depends on engine layout."),
    "P0302" to CodeGuide("Cylinder 2 misfire detected.", "Often caused by a worn spark plug, ignition coil, injector, or a compression problem on cylinder 2.", "Compare cylinder-specific symptoms and inspect the plug/coil before replacing parts. The cylinder location depends on engine layout."),
    "P0303" to CodeGuide("Cylinder 3 misfire detected.", "Often caused by a worn spark plug, ignition coil, injector, or a compression problem on cylinder 3.", "Compare cylinder-specific symptoms and inspect the plug/coil before replacing parts. The cylinder location depends on engine layout."),
    "P0304" to CodeGuide("Cylinder 4 misfire detected.", "Often caused by a worn spark plug, ignition coil, injector, or a compression problem on cylinder 4.", "Compare cylinder-specific symptoms and inspect the plug/coil before replacing parts. The cylinder location depends on engine layout."),
    "P0171" to CodeGuide("System too lean, bank 1.", "Common causes include an intake/vacuum leak, dirty or faulty mass-airflow sensor, or low fuel delivery.", "Look for intake leaks and related fuel-trim/MAF codes. Avoid replacing a sensor without confirming the readings."),
    "P0174" to CodeGuide("System too lean, bank 2.", "Common causes include an intake/vacuum leak, dirty or faulty mass-airflow sensor, or low fuel delivery.", "Look for intake leaks and related fuel-trim/MAF codes. Avoid replacing a sensor without confirming the readings."),
    "P0420" to CodeGuide("Catalyst system efficiency below threshold, bank 1.", "Possible causes include an exhaust leak, oxygen-sensor issue, engine misfire, or a worn catalytic converter.", "Check for exhaust leaks and other engine codes first. Do not assume the catalytic converter is bad from this code alone."),
    "P0430" to CodeGuide("Catalyst system efficiency below threshold, bank 2.", "Possible causes include an exhaust leak, oxygen-sensor issue, engine misfire, or a worn catalytic converter.", "Check for exhaust leaks and other engine codes first. Do not assume the catalytic converter is bad from this code alone."),
    "P0455" to CodeGuide("EVAP system large leak detected.", "A loose/damaged fuel cap is a common simple cause; hoses, purge/vent valves, or the tank system can also leak.", "Tighten and inspect the fuel cap, then allow time for the vehicle's monitor to rerun. A smoke test may be needed for other leaks."),
    "P0442" to CodeGuide("EVAP system small leak detected.", "Common causes include a fuel-cap seal, small cracked hose, or a leaking purge/vent component.", "Inspect the cap and visible EVAP lines. Small leaks often require a smoke test to locate."),
    "P0128" to CodeGuide("Coolant temperature is below the expected regulating temperature.", "A thermostat stuck open is common; low coolant or a coolant-temperature sensor issue may also contribute.", "When the engine is fully cold, check coolant level safely and look for leaks. Never open a hot cooling system."),
    "P0101" to CodeGuide("Mass-airflow sensor range/performance problem.", "Possible causes include an intake leak, dirty MAF sensor, restricted air filter, wiring issue, or a failing sensor.", "Inspect the air duct and filter and check for unmetered-air leaks before replacing the MAF."),
    "P0700" to CodeGuide("The transmission control system requested the check-engine light.", "P0700 is a general flag; the actual fault is usually stored in the transmission module.", "Use a scanner that can read transmission-specific codes. Avoid guessing or continuing to drive if shifting is unsafe.")
)

private fun diagnose(question: String, year: String, make: String, model: String): String {
    val vehicle = listOf(year, make, model).filter { it.isNotBlank() }.joinToString(" ")
        .ifBlank { "vehicle details not provided" }
    val code = Regex("\\bP[0-9A-F]{4}\\b", RegexOption.IGNORE_CASE)
        .find(question.uppercase())?.value

    if (code != null) {
        val guide = commonCodeGuides[code]
        return if (guide != null) {
            "$vehicle — $code\n${guide.summary}\n\nCommon possibilities: ${guide.commonCauses}\n\nNext checks: ${guide.checks}\n\nThis is general OBD-II guidance, not a confirmed vehicle-specific failure. Exact common faults vary by engine, model year, and manufacturer."
        } else {
            "$vehicle — $code\nI don't have a reliable guide for this code in the included offline reference. Read the code description and any related codes with a capable scanner, then check service information for your exact year, make, model, and engine. Don't replace parts based on the code alone."
        }
    }

    val symptomAdvice = when {
        question.contains("overheat", ignoreCase = true) || question.contains("hot", ignoreCase = true) -> "If the temperature is in the red, steam is present, or coolant is leaking, stop safely and switch the engine off. Never remove a hot radiator cap. Once fully cool, check coolant level and arrange diagnosis."
        question.contains("brake", ignoreCase = true) -> "If braking is weak, the pedal is soft, a warning light is on, or you hear grinding, stop driving and have the brakes inspected. Brake faults are safety-critical."
        question.contains("battery", ignoreCase = true) || question.contains("start", ignoreCase = true) -> "For a no-start, note whether the engine cranks. Check battery terminals for looseness/corrosion and have the battery and charging system tested. Avoid repeated cranking if cables heat up or there is a burning smell."
        question.isBlank() -> "Enter a code such as P0301 or describe what the vehicle is doing. Include the exact year, make, model, engine, and any warning lights for more useful general guidance."
        else -> "I can offer general troubleshooting, but the offline guide needs a trouble code or clearer symptoms. Tell me when it happens, warning lights, sounds/smells, and any scan codes. Vehicle-specific common issues require exact year, make, model, and engine and should be checked against reliable service information."
    }
    return "$vehicle\n$symptomAdvice"
}
