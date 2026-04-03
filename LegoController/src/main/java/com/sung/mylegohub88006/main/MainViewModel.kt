package com.sung.mylegohub88006.main

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import com.sung.mylegohub88006.LegoHubManager

// ── Sealed state classes ──────────────────────────────────────────────────────
sealed class ConnState {
    object Disconnected : ConnState();
    object Scanning : ConnState()
    data class Connecting(val name: String) : ConnState();
    object Connected : ConnState()
}

data class ColorSensorState(
    val detected:    Boolean = false,
    val subscribed:  Boolean = false,
    val mode:        Int     = LegoHubManager.COLOR_DIST_MODE_COMBINED,
    val colorIndex:  Int     = -1,
    val colorName:   String  = "—",
    val colorHex:    Color   = Color(0xFFBDBDBD),
    val distance:    Int     = -1,
    val rgbR:        Int     = 0,
    val rgbG:        Int     = 0,
    val rgbB:        Int     = 0,
    val rawHex:      String  = "—"
)

data class MotorDState(
    val detected:    Boolean = false,
    val subscribed:  Boolean = false,
    val mode:        Int     = LegoHubManager.MOTOR_MODE_POS,
    val position:    Int     = 0,
    val rawHex:      String  = "—"
)

@SuppressLint("MissingPermission")
class MainViewModel(app: Application) : AndroidViewModel(app) {

    val hub = LegoHubManager(app)

    var connState   by mutableStateOf<ConnState>(ConnState.Disconnected)
    var motorASpeed by mutableStateOf(0)
    var motorBSpeed by mutableStateOf(0)
    var colorSensor by mutableStateOf(ColorSensorState())
    var motorD      by mutableStateOf(MotorDState())

    // Color index → (name, Compose Color)
    private val colorMap = mapOf(
        0  to ("Black"  to Color(0xFF1E1E1E)),
        3  to ("Blue"   to Color(0xFF3498DB)),
        5  to ("Green"  to Color(0xFF2ECC71)),
        7  to ("Yellow" to Color(0xFFF1C40F)),
        9  to ("Red"    to Color(0xFFE74C3C)),
        10 to ("White"  to Color(0xFFECF0F1))
    )

    init {
        hub.onDeviceFound = { device ->
            connState = ConnState.Connecting(device.name ?: device.address)
            hub.connect(device)
        }
        hub.onConnected    = { connState = ConnState.Connected }
        hub.onDisconnected = { connState = ConnState.Disconnected; resetState() }

        hub.onAttachedIO = { port, type ->
            when (port) {
                LegoHubManager.PORT_C.toInt() and 0xFF ->
                    colorSensor = colorSensor.copy(detected = type == LegoHubManager.DEV_COLOR_DISTANCE)
                LegoHubManager.PORT_D.toInt() and 0xFF ->
                    motorD = motorD.copy(detected = type == LegoHubManager.DEV_MEDIUM_LINEAR)
            }
        }

        hub.onSensorData = { port, mode, raw ->
            val hex = raw.joinToString(" ") { it.toInt().and(0xFF).toString(16).uppercase().padStart(2,'0') }
            when (port) {
                LegoHubManager.PORT_C.toInt() and 0xFF -> parseColorSensor(mode, raw, hex)
                LegoHubManager.PORT_D.toInt() and 0xFF -> parseMotorD(mode, raw, hex)
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    fun startScan()  { connState = ConnState.Scanning; hub.startScan() }
    fun disconnect() { hub.disconnect() }

    fun setMotorA(speed: Int) { motorASpeed = speed; hub.setMotorSpeed(LegoHubManager.PORT_MOTOR_A, speed) }
    fun setMotorB(speed: Int) { motorBSpeed = speed; hub.setMotorSpeed(LegoHubManager.PORT_MOTOR_B, speed) }
    fun brakeBuiltIn() {
        hub.brakeMotor(LegoHubManager.PORT_MOTOR_A)
        hub.brakeMotor(LegoHubManager.PORT_MOTOR_B)
        motorASpeed = 0; motorBSpeed = 0
    }

    fun toggleColorSubscribe() {
        if (colorSensor.subscribed) {
            hub.unsubscribePort(LegoHubManager.PORT_C)
            colorSensor = colorSensor.copy(subscribed = false)
        } else {
            hub.subscribePort(LegoHubManager.PORT_C, colorSensor.mode)
            colorSensor = colorSensor.copy(subscribed = true)
        }
    }
    fun setColorMode(mode: Int) {
        colorSensor = colorSensor.copy(mode = mode)
        if (colorSensor.subscribed) hub.subscribePort(LegoHubManager.PORT_C, mode)
    }

    fun toggleMotorDSubscribe() {
        if (motorD.subscribed) {
            hub.unsubscribePort(LegoHubManager.PORT_D)
            motorD = motorD.copy(subscribed = false)
        } else {
            hub.subscribePort(LegoHubManager.PORT_D, motorD.mode)
            motorD = motorD.copy(subscribed = true)
        }
    }
    fun setMotorDMode(mode: Int) {
        motorD = motorD.copy(mode = mode)
        if (motorD.subscribed) hub.subscribePort(LegoHubManager.PORT_D, mode)
    }

    // ── Sensor parsing ────────────────────────────────────────────────────────
    private fun parseColorSensor(mode: Int, raw: ByteArray, hex: String) {
        if (raw.isEmpty()) return
        colorSensor = when (mode) {
            LegoHubManager.COLOR_DIST_MODE_COLOR -> {
                val idx = raw[0].toInt() and 0xFF
                val (name, color) = colorMap[idx] ?: ("Unknown" to Color.Gray)
                colorSensor.copy(colorIndex = idx, colorName = name, colorHex = color,
                                 distance = -1, rawHex = hex)
            }
            LegoHubManager.COLOR_DIST_MODE_PROX ->
                colorSensor.copy(distance = raw[0].toInt() and 0xFF,
                                 colorName = "—", colorIndex = -1, rawHex = hex)
            LegoHubManager.COLOR_DIST_MODE_COMBINED -> if (raw.size >= 4) {
                val idx  = raw[0].toInt() and 0xFF
                val dist = raw[2].toInt() and 0xFF
                val (name, color) = colorMap[idx] ?: ("Unknown" to Color.Gray)
                colorSensor.copy(colorIndex = idx, colorName = name, colorHex = color,
                                 distance = dist, rawHex = hex)
            } else colorSensor.copy(rawHex = hex)
            LegoHubManager.COLOR_DIST_MODE_RGB -> if (raw.size >= 6) {
                val r = ((raw[0].toInt() and 0xFF) or ((raw[1].toInt() and 0xFF) shl 8)).coerceAtMost(255)
                val g = ((raw[2].toInt() and 0xFF) or ((raw[3].toInt() and 0xFF) shl 8)).coerceAtMost(255)
                val b = ((raw[4].toInt() and 0xFF) or ((raw[5].toInt() and 0xFF) shl 8)).coerceAtMost(255)
                colorSensor.copy(rgbR = r, rgbG = g, rgbB = b,
                                 colorHex = Color(r, g, b), colorName = "RGB",
                                 colorIndex = -1, rawHex = hex)
            } else colorSensor.copy(rawHex = hex)
            else -> colorSensor.copy(rawHex = hex)
        }
    }

    private fun parseMotorD(mode: Int, raw: ByteArray, hex: String) {
        if (raw.isEmpty()) return
        val pos = when (mode) {
            LegoHubManager.MOTOR_MODE_POS -> if (raw.size >= 4)
                (raw[0].toInt() and 0xFF) or ((raw[1].toInt() and 0xFF) shl 8) or
                        ((raw[2].toInt() and 0xFF) shl 16) or ((raw[3].toInt() and 0xFF) shl 24)
            else motorD.position
            LegoHubManager.MOTOR_MODE_APOS -> if (raw.size >= 2)
                (raw[0].toInt() and 0xFF) or ((raw[1].toInt() and 0xFF) shl 8)
            else motorD.position
            LegoHubManager.MOTOR_MODE_POWER -> raw[0].toInt()
            else -> motorD.position
        }
        motorD = motorD.copy(position = pos, rawHex = hex)
    }

    private fun resetState() {
        colorSensor = ColorSensorState()
        motorD      = MotorDState()
        motorASpeed = 0; motorBSpeed = 0
    }

    override fun onCleared() { super.onCleared(); hub.disconnect() }

}
