package com.example.obdscan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Bluetooth Classic (SPP/RFCOMM) transport + ELM327 command handling. */
class Elm327 {
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val lock = Mutex()

    var protocol: String = ""
        private set
    val isCan: Boolean
        get() = protocol.trim().lastOrNull() in '6'..'9'
    val connected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        close()
        val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val s = device.createRfcommSocketToServiceRecord(spp)
        s.connect()
        socket = s
        input = s.inputStream
        output = s.outputStream
        send("ATZ", 4000)
        send("ATE0")
        send("ATL0")
        send("ATS0")
        send("ATH0")
        send("ATSP0")
        // Force protocol detection so ATDPN reports the real protocol.
        send("0100", 8000)
        protocol = send("ATDPN").replace("A", "").trim()
    }

    /** Sends one command and returns the cleaned response text (up to the '>' prompt). */
    suspend fun send(cmd: String, timeoutMs: Long = 2500): String = lock.withLock {
        withContext(Dispatchers.IO) {
            val out = output ?: error("Not connected")
            val inp = input ?: error("Not connected")
            while (inp.available() > 0) inp.read()
            out.write((cmd + "\r").toByteArray())
            out.flush()
            val sb = StringBuilder()
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end) {
                if (inp.available() > 0) {
                    val c = inp.read().toChar()
                    if (c == '>') break
                    sb.append(c)
                } else {
                    Thread.sleep(10)
                }
            }
            sb.toString()
                .replace("\r", "\n")
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(cmd, ignoreCase = true) }
                .joinToString("\n")
        }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null; input = null; output = null
    }
}
