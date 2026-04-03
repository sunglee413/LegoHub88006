package com.sung.mylegohub88006

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import java.util.UUID

@SuppressLint("MissingPermission")
class LegoHubManager(private val context: Context) {

    companion object {
        // ── LEGO Wireless Protocol BLE UUIDs ──
        val SERVICE_UUID: UUID = UUID.fromString("00001623-1212-efde-1623-785feabcd123")
        val CHAR_UUID:    UUID = UUID.fromString("00001624-1212-efde-1623-785feabcd123")
        val CCCD_UUID:    UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // ── Hub ports ──
        const val PORT_MOTOR_A: Byte = 0x00   // built-in motor A
        const val PORT_MOTOR_B: Byte = 0x01   // built-in motor B
        const val PORT_C:       Byte = 0x02   // external port C → Color & Distance Sensor 88007
        const val PORT_D:       Byte = 0x03   // external port D → Medium Linear Motor 88008
        const val PORT_LED:     Byte = 0x32.toByte()  // virtual: built-in RGB LED
        const val PORT_TILT:    Byte = 0x3A.toByte()  // virtual: built-in tilt sensor

        // ── Known Powered Up device type IDs ──
        const val DEV_COLOR_DISTANCE   = 0x0025  // Color & Distance Sensor 88007
        const val DEV_MEDIUM_LINEAR    = 0x0026  // Medium Linear Motor 88008
        const val DEV_BOOST_INTERACTIVE= 0x0027  // Boost Interactive Motor (built-in)

        // ── Color & Distance Sensor modes ──
        const val COLOR_DIST_MODE_COLOR    = 0  // 1 byte  – color index
        const val COLOR_DIST_MODE_PROX     = 1  // 1 byte  – proximity 0-10
        const val COLOR_DIST_MODE_COUNT    = 2  // 4 bytes – count
        const val COLOR_DIST_MODE_REFLECT  = 3  // 1 byte  – reflected light intensity
        const val COLOR_DIST_MODE_AMBIENT  = 4  // 1 byte  – ambient light intensity
        const val COLOR_DIST_MODE_COMBINED = 5  // 4 bytes – color + distance combined
        const val COLOR_DIST_MODE_RGB      = 8  // 6 bytes – raw RGB (3 × Int16)

        // ── Medium Linear Motor modes ──
        const val MOTOR_MODE_POWER  = 0  // 1 byte  – current power/speed
        const val MOTOR_MODE_SPEED  = 1  // 1 byte  – current speed
        const val MOTOR_MODE_POS    = 2  // 4 bytes – relative position (encoder)
        const val MOTOR_MODE_APOS   = 3  // 2 bytes – absolute position 0-359°

        // LED color indices
        val LED_COLORS = mapOf(
            "off" to 0, "pink" to 1, "purple" to 2, "blue" to 3,
            "lightblue" to 4, "cyan" to 5, "green" to 6,
            "yellow" to 7, "orange" to 8, "red" to 9, "white" to 10
        )
    }

    // ── Public callbacks ──────────────────────────────────────────────────────
    var onConnected:    (() -> Unit)?                              = null
    var onDisconnected: (() -> Unit)?                              = null
    var onDeviceFound:  ((BluetoothDevice) -> Unit)?               = null
    var onAttachedIO:   ((port: Int, deviceType: Int) -> Unit)?    = null
    /** Fires for every sensor notification: port, mode, raw payload bytes */
    var onSensorData:   ((port: Int, mode: Int, raw: ByteArray) -> Unit)? = null

    // ── Internal state ────────────────────────────────────────────────────────
    private var gatt:        BluetoothGatt?              = null
    private var hubChar:     BluetoothGattCharacteristic? = null
    private val writeQueue = ArrayDeque<ByteArray>()
    private var isWriting  = false
    private val mainHandler = Handler(Looper.getMainLooper())
    /** port → last subscribed mode (needed to parse incoming notifications) */
    private val portModes = mutableMapOf<Int, Int>()

    private val btAdapter: BluetoothAdapter
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // ── Scanning ──────────────────────────────────────────────────────────────
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        val filter   = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        btAdapter.bluetoothLeScanner.startScan(listOf(filter), settings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() { btAdapter.bluetoothLeScanner.stopScan(scanCallback) }

    // ── Connection ────────────────────────────────────────────────────────────
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        gatt?.disconnect(); gatt?.close()
        gatt = null; hubChar = null
        writeQueue.clear(); isWriting = false
    }

    // ── Built-in motor control (ports A & B) ──────────────────────────────────
    /** speed: -100..100, 127 = brake, 0 = float */
    fun setMotorSpeed(port: Byte, speed: Int) {
        val s = speed.coerceIn(-100, 100).toByte()
        write(byteArrayOf(0x08, 0x00, 0x81.toByte(), port, 0x11, 0x51, 0x00, s))
    }
    fun brakeMotor(port: Byte) =
        write(byteArrayOf(0x08, 0x00, 0x81.toByte(), port, 0x11, 0x51, 0x00, 0x7F))
    fun floatMotor(port: Byte) =
        write(byteArrayOf(0x08, 0x00, 0x81.toByte(), port, 0x11, 0x51, 0x00, 0x00))

    // ── Medium Linear Motor 88008 (port D) ────────────────────────────────────
    /** Run at a given speed (-100..100) until stopped */
    fun setMediumMotorSpeed(speed: Int) = setMotorSpeed(PORT_D, speed)

    /** Run for a fixed number of degrees, then auto-stop
     *  speed: 1..100, degrees: any Int */
    fun runMediumMotorDegrees(degrees: Int, speed: Int = 50) {
        val sp  = speed.coerceIn(1, 100)
        val deg = degrees
        // Command 0x0B – GOTO_ABS_POS (relative degrees)
        write(byteArrayOf(
            0x0C, 0x00, 0x81.toByte(), PORT_D, 0x11, 0x0B,
            (deg and 0xFF).toByte(), ((deg shr 8) and 0xFF).toByte(),
            ((deg shr 16) and 0xFF).toByte(), ((deg shr 24) and 0xFF).toByte(),
            sp.toByte(), 0x64, 0x7E, 0x03
        ))
    }

    /** Rotate to an absolute position 0-359°, speed 1..100 */
    fun gotoMediumMotorAbsPos(position: Int, speed: Int = 50) {
        val sp  = speed.coerceIn(1, 100)
        val pos = position.coerceIn(0, 359)
        write(byteArrayOf(
            0x0E, 0x00, 0x81.toByte(), PORT_D, 0x11, 0x0D,
            (pos and 0xFF).toByte(), ((pos shr 8) and 0xFF).toByte(),
            sp.toByte(), 0x64, 0x7E, 0x00, 0x03, 0x00
        ))
    }

    fun brakeMediumMotor() = brakeMotor(PORT_D)
    fun floatMediumMotor() = floatMotor(PORT_D)

    /** Reset encoder position to zero */
    fun resetMediumMotorEncoder() {
        write(byteArrayOf(0x0B, 0x00, 0x81.toByte(), PORT_D, 0x11, 0x51, 0x02,
                          0x00, 0x00, 0x00, 0x00))
    }

    // ── LED ───────────────────────────────────────────────────────────────────
    fun setLedColor(colorIndex: Int) {
        write(byteArrayOf(0x08, 0x00, 0x81.toByte(), PORT_LED, 0x11, 0x51, 0x00, colorIndex.toByte()))
    }

    // ── Sensor subscription ───────────────────────────────────────────────────
    /**
     * Enable continuous notifications from a port.
     * deltaInterval: minimum change before a new notification fires (1 = every change)
     */
    fun subscribePort(port: Byte, mode: Int, deltaInterval: Int = 1) {
        portModes[port.toInt() and 0xFF] = mode
        val di = deltaInterval
        write(byteArrayOf(
            0x0A, 0x00, 0x41, port, mode.toByte(),
            (di and 0xFF).toByte(), ((di shr 8) and 0xFF).toByte(),
            ((di shr 16) and 0xFF).toByte(), ((di shr 24) and 0xFF).toByte(),
            0x01
        ))
    }

    fun unsubscribePort(port: Byte) {
        val mode = portModes[port.toInt() and 0xFF] ?: 0
        write(byteArrayOf(
            0x0A, 0x00, 0x41, port, mode.toByte(),
            0x01, 0x00, 0x00, 0x00, 0x00
        ))
    }

    // ── Write queue (BLE is strictly sequential) ──────────────────────────────
    private fun write(data: ByteArray) {
        writeQueue.addLast(data)
        if (!isWriting) drainQueue()
    }

    private fun drainQueue() {
        val data = writeQueue.removeFirstOrNull() ?: run { isWriting = false; return }
        isWriting = true
        val char = hubChar ?: run { isWriting = false; return }
        char.value = data
        gatt?.writeCharacteristic(char)
    }

    // ── BLE callbacks ─────────────────────────────────────────────────────────
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            mainHandler.post { onDeviceFound?.invoke(result.device) }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED    -> gatt.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> mainHandler.post { onDisconnected?.invoke() }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val char = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID) ?: return
            hubChar = char
            gatt.setCharacteristicNotification(char, true)
            val cccd = char.getDescriptor(CCCD_UUID) ?: return
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) { mainHandler.post { onConnected?.invoke() } }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) { mainHandler.post { drainQueue() } }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            if (data.size < 3) return
            when (data[2].toInt() and 0xFF) {
                0x04 -> parseAttachedIO(data)
                0x45 -> parsePortValue(data)
            }
        }
    }

    // ── Protocol parsing ──────────────────────────────────────────────────────
    private fun parseAttachedIO(data: ByteArray) {
        if (data.size < 5) return
        val port  = data[3].toInt() and 0xFF
        val event = data[4].toInt() and 0xFF
        val deviceType = if (event != 0 && data.size >= 7)
            (data[5].toInt() and 0xFF) or ((data[6].toInt() and 0xFF) shl 8)
        else 0
        mainHandler.post { onAttachedIO?.invoke(port, deviceType) }
    }

    private fun parsePortValue(data: ByteArray) {
        if (data.size < 4) return
        val port    = data[3].toInt() and 0xFF
        val mode    = portModes[port] ?: 0
        val payload = data.copyOfRange(4, data.size)
        mainHandler.post { onSensorData?.invoke(port, mode, payload) }
    }

}
