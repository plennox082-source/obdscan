package com.example.obdscan

/** Standard OBD-II (SAE J1979) decoding helpers. */
object Obd {

    private val hexByte = Regex("^[0-9A-Fa-f]{2}$")

    /** Flatten a raw ELM response into a list of byte values, dropping multi-frame headers. */
    private fun bytesOf(resp: String): List<Int> {
        val out = mutableListOf<Int>()
        for (rawLine in resp.lines()) {
            var line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.matches(Regex("^[0-9A-Fa-f]{3}$"))) continue // multi-frame length header
            line = line.replace(Regex("^[0-9A-Fa-f]:\\s*"), "")   // "0: 43 02 ..." prefix
            for (tok in line.split(Regex("\\s+"))) {
                if (hexByte.matches(tok)) out.add(tok.toInt(16))
            }
        }
        return out
    }

    /** Value bytes following a "41 <pid>" reply, or null if not found. */
    fun pidBytes(resp: String, pid: Int): List<Int>? {
        val b = bytesOf(resp)
        for (i in 0 until b.size - 1) {
            if (b[i] == 0x41 && b[i + 1] == pid) return b.drop(i + 2)
        }
        return null
    }

    fun rpm(resp: String): Int? = pidBytes(resp, 0x0C)?.takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 4 }
    fun speedKmh(resp: String): Int? = pidBytes(resp, 0x0D)?.firstOrNull()
    fun coolantC(resp: String): Int? = pidBytes(resp, 0x05)?.firstOrNull()?.let { it - 40 }
    fun throttlePct(resp: String): Int? = pidBytes(resp, 0x11)?.firstOrNull()?.let { it * 100 / 255 }

    /** Decode mode 03 / 07 / 0A responses into DTC strings. */
    fun parseDtcs(resp: String, responseMode: Int, isCan: Boolean): List<String> {
        val result = mutableListOf<String>()
        // Each ECU answers on its own line (or its own multi-frame group); parse line by line.
        val groups = mutableListOf<MutableList<Int>>()
        for (rawLine in resp.lines()) {
            var line = rawLine.trim()
            if (line.isEmpty() || line.matches(Regex("^[0-9A-Fa-f]{3}$"))) continue
            val continuation = line.matches(Regex("^[0-9A-Fa-f]:.*"))
            line = line.replace(Regex("^[0-9A-Fa-f]:\\s*"), "")
            val toks = line.split(Regex("\\s+")).filter { hexByte.matches(it) }.map { it.toInt(16) }
            if (toks.isEmpty()) continue
            if (continuation && groups.isNotEmpty()) groups.last().addAll(toks) else groups.add(toks.toMutableList())
        }
        for (g in groups) {
            val start = g.indexOf(responseMode)
            if (start < 0) continue
            var data = g.drop(start + 1)
            if (isCan && data.isNotEmpty()) data = data.drop(1) // CAN: count byte follows the mode
            var i = 0
            while (i + 1 < data.size) {
                val a = data[i]; val b = data[i + 1]
                i += 2
                if (a == 0 && b == 0) continue
                val letter = "PCBU"[(a shr 6) and 3]
                val d2 = (a shr 4) and 3
                val code = "%c%d%X%02X".format(letter, d2, a and 0xF, b)
                if (code !in result) result.add(code)
            }
        }
        return result
    }
}
