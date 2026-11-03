package com.example.obdscan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Bidirectional tests are data, not code. A profile is a JSON file describing
 * one car or brand. Each test is a list of raw commands (AT commands and hex
 * requests) sent in order, plus "stop" commands that release control.
 */
data class ObdTest(
    val title: String,
    val warning: String,
    val header: String,
    val rx: String,
    val steps: List<String>,
    val stop: List<String>,
)

data class Profile(val name: String, val notes: String, val tests: List<ObdTest>)

object Profiles {

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    fun parse(json: String): Profile {
        val o = JSONObject(json)
        val tests = o.optJSONArray("tests")?.let { arr ->
            (0 until arr.length()).map { idx ->
                val t = arr.getJSONObject(idx)
                ObdTest(
                    title = t.getString("title"),
                    warning = t.optString("warning", ""),
                    header = t.optString("header", ""),
                    rx = t.optString("rx", ""),
                    steps = t.optJSONArray("steps").strings(),
                    stop = t.optJSONArray("stop").strings(),
                )
            }
        } ?: emptyList()
        return Profile(o.getString("name"), o.optString("notes", ""), tests)
    }

    fun userDir(ctx: Context): File = File(ctx.filesDir, "profiles").apply { mkdirs() }

    fun loadAll(ctx: Context): List<Profile> {
        val list = mutableListOf<Profile>()
        ctx.assets.list("profiles")?.forEach { name ->
            runCatching {
                list.add(parse(ctx.assets.open("profiles/$name").bufferedReader().use { it.readText() }))
            }
        }
        userDir(ctx).listFiles()?.forEach { f ->
            runCatching { list.add(parse(f.readText())) }
        }
        return list
    }

    /** Validates and stores an imported profile. Throws if the JSON is invalid. */
    fun import(ctx: Context, json: String): Profile {
        val p = parse(json)
        val safe = p.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        File(userDir(ctx), "$safe.json").writeText(json)
        return p
    }
}
