package com.sung.mylegohub88006.main

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.sung.mylegohub88006.LegoHubManager

import com.sung.mylegohub88006.ui.theme.MyLegoHub88006Theme

// ── Color constants ───────────────────────────────────────────────────────────
 val BgColor    = Color(0xFFECEFF1)
private val CardColor  = Color.White
private val Green      = Color(0xFF27AE60)
private val Orange     = Color(0xFFE67E22)
private val TextGray   = Color(0xFF9E9E9E)

// ── LED palette ───────────────────────────────────────────────────────────────
private data class LedEntry(val label: String, val index: Int, val color: Color,
                            val textColor: Color = Color.White)
private val LED_PALETTE = listOf(
    LedEntry("Off",    0,  Color(0xFF888888)),
    LedEntry("Pink",   1,  Color(0xFFFF69B4)),
    LedEntry("Purple", 2,  Color(0xFF9B59B6)),
    LedEntry("Blue",   3,  Color(0xFF3498DB)),
    LedEntry("L.Blue", 4,  Color(0xFF5DADE2)),
    LedEntry("Cyan",   5,  Color(0xFF1ABC9C)),
    LedEntry("Green",  6,  Color(0xFF2ECC71)),
    LedEntry("Yellow", 7,  Color(0xFFF1C40F), Color.Black),
    LedEntry("Orange", 8,  Color(0xFFE67E22), Color.Black),
    LedEntry("Red",    9,  Color(0xFFE74C3C)),
    LedEntry("White",  10, Color(0xFFECF0F1), Color.Black)
)


// ─────────────────────────────────────────────────────────────────────────────
//  Root screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HubScreen(
    vm: MainViewModel, onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ConnectionCard(state = vm.connState, onScan = onScan, onDisconnect = vm::disconnect)
        BuiltInMotorsCard(
            speedA = vm.motorASpeed, speedB = vm.motorBSpeed,
            onSpeedA = vm::setMotorA, onSpeedB = vm::setMotorB,
            onBrake  = vm::brakeBuiltIn
        )
        LedCard(onColor = { vm.hub.setLedColor(it) })
        ColorSensorCard(
            state       = vm.colorSensor,
            onSubscribe = vm::toggleColorSubscribe,
            onMode      = vm::setColorMode
        )
        MediumMotorCard(
            state       = vm.motorD,
            onSubscribe = vm::toggleMotorDSubscribe,
            onMode      = vm::setMotorDMode,
            onSpeed     = { vm.hub.setMediumMotorSpeed(it) },
            onBrake     = { vm.hub.brakeMediumMotor() },
            onFloat     = { vm.hub.floatMediumMotor() },
            onReset     = { vm.hub.resetMediumMotorEncoder() },
            onRunDeg    = { deg, spd -> vm.hub.runMediumMotorDegrees(deg, spd) }
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared card shell
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HubCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Connection card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ConnectionCard(state: ConnState, onScan: () -> Unit, onDisconnect: () -> Unit) {
    val (statusText, statusColor) = when (state) {
        ConnState.Disconnected      -> "● Disconnected"    to Color.Red
        ConnState.Scanning          -> "⟳ Scanning…"       to TextGray
        is ConnState.Connecting     -> "⟳ Connecting to ${state.name}…" to Orange
        ConnState.Connected         -> "● Connected"        to Green
    }
    HubCard("LEGO Move Hub 88006") {
        Text(statusText, color = statusColor, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onScan,
                enabled = state is ConnState.Disconnected,
                modifier = Modifier.weight(1f)
            ) { Text("Scan & Connect") }
            OutlinedButton(
                onClick  = onDisconnect,
                enabled  = state is ConnState.Connected,
                modifier = Modifier.weight(1f)
            ) { Text("Disconnect") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Speed slider helper
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MotorSlider(label: String, speed: Int, onSpeed: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.width(72.dp), fontSize = 13.sp)
            Text(
                "%4d".format(speed),
                fontFamily = FontFamily.Monospace,
                fontSize   = 13.sp,
                modifier   = Modifier.width(36.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("-100", fontSize = 11.sp, color = TextGray)
            Slider(
                value         = (speed + 100).toFloat(),
                onValueChange = { onSpeed((it - 100).toInt()) },
                valueRange    = 0f..200f,
                modifier      = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            )
            Text("100", fontSize = 11.sp, color = TextGray)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Built-in motors card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BuiltInMotorsCard(
    speedA: Int, speedB: Int,
    onSpeedA: (Int) -> Unit, onSpeedB: (Int) -> Unit,
    onBrake: () -> Unit
) {
    HubCard("Built-in Motors  (Ports A / B)") {
        MotorSlider("Motor A", speedA, onSpeedA)
        Spacer(Modifier.height(6.dp))
        MotorSlider("Motor B", speedB, onSpeedB)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onBrake, modifier = Modifier.fillMaxWidth()) {
            Text("⏹  Brake Both")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LED card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun LedCard(onColor: (Int) -> Unit) {
    HubCard("Hub LED Color") {
        LED_PALETTE.chunked(6).forEach { row ->
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { led ->
                    Button(
                        onClick  = { onColor(led.index) },
                        colors   = ButtonDefaults.buttonColors(containerColor = led.color),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape    = RoundedCornerShape(6.dp)
                    ) {
                        Text(led.label, color = led.textColor, fontSize = 9.sp,
                             maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Color & Distance sensor card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ColorSensorCard(
    state:       ColorSensorState,
    onSubscribe: () -> Unit,
    onMode:      (Int) -> Unit
) {
    val animColor by animateColorAsState(
        targetValue    = state.colorHex,
        animationSpec  = tween(300),
        label          = "swatch"
    )

    HubCard("Color & Distance Sensor 88007  –  Port C") {
        // Status + subscribe button
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.detected) "Port C — sensor detected ✓" else "Port C — not detected",
                    color    = if (state.detected) Green else TextGray,
                    fontSize = 12.sp
                )
            }
            Button(onClick = onSubscribe) {
                Text(if (state.subscribed) "Unsubscribe" else "Subscribe")
            }
        }

        Spacer(Modifier.height(10.dp))

        // Mode radio buttons
        val modes = listOf(
            "Color"    to LegoHubManager.COLOR_DIST_MODE_COLOR,
            "Prox"     to LegoHubManager.COLOR_DIST_MODE_PROX,
            "Combined" to LegoHubManager.COLOR_DIST_MODE_COMBINED,
            "RGB"      to LegoHubManager.COLOR_DIST_MODE_RGB
        )
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Mode:", fontSize = 13.sp)
            modes.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.mode == value,
                        onClick  = { onMode(value) },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(label, fontSize = 12.sp,
                         modifier = Modifier
                             .clickable { onMode(value) }
                             .padding(start = 2.dp, end = 8.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Color swatch + info
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(animColor)
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(state.colorName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (state.colorIndex >= 0)
                    Text("Index: ${state.colorIndex}", fontSize = 13.sp, color = TextGray)
                if (state.mode == LegoHubManager.COLOR_DIST_MODE_RGB) {
                    Text(
                        "#${state.rgbR.toString(16).padStart(2,'0').uppercase()}" +
                                "${state.rgbG.toString(16).padStart(2,'0').uppercase()}" +
                                "${state.rgbB.toString(16).padStart(2,'0').uppercase()}" +
                                "  (${state.rgbR}, ${state.rgbG}, ${state.rgbB})",
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Distance row
        if (state.mode != LegoHubManager.COLOR_DIST_MODE_COLOR) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Distance: ", fontSize = 14.sp)
                Text(
                    if (state.distance >= 0) state.distance.toString() else "—",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
                if (state.distance >= 0) {
                    Text(" / 10", fontSize = 13.sp, color = TextGray,
                         modifier = Modifier.padding(bottom = 3.dp))
                    Spacer(Modifier.width(10.dp))
                    LinearProgressIndicator(
                        progress = { state.distance / 10f },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .align(Alignment.CenterVertically)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Raw bytes
        Text("Raw: ${state.rawHex}", fontFamily = FontFamily.Monospace,
             fontSize = 11.sp, color = TextGray)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Medium Linear Motor card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MediumMotorCard(
    state:       MotorDState,
    onSubscribe: () -> Unit,
    onMode:      (Int) -> Unit,
    onSpeed:     (Int) -> Unit,
    onBrake:     () -> Unit,
    onFloat:     () -> Unit,
    onReset:     () -> Unit,
    onRunDeg:    (Int, Int) -> Unit
) {
    var sliderSpeed by remember { mutableStateOf(0) }
    var degreesText by remember { mutableStateOf("") }
    var speedText   by remember { mutableStateOf("50") }

    HubCard("Medium Linear Motor 88008  –  Port D") {
        // Status + subscribe
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.detected) "Port D — motor detected ✓" else "Port D — not detected",
                    color    = if (state.detected) Green else TextGray,
                    fontSize = 12.sp
                )
            }
            Button(onClick = onSubscribe) {
                Text(if (state.subscribed) "Unsubscribe" else "Subscribe")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Speed slider
        MotorSlider("Speed", sliderSpeed) { spd ->
            sliderSpeed = spd; onSpeed(spd)
        }

        Spacer(Modifier.height(8.dp))

        // Brake / Float / Reset
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { onBrake(); sliderSpeed = 0 }, modifier = Modifier.weight(1f)) {
                Text("⏹ Brake")
            }
            OutlinedButton(onClick = { onFloat(); sliderSpeed = 0 }, modifier = Modifier.weight(1f)) {
                Text("◎ Float")
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text("↺ Reset")
            }
        }

        Spacer(Modifier.height(10.dp))

        // Run exact degrees
        Text("Run exact degrees:", fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = degreesText,
                onValueChange = { degreesText = it },
                label         = { Text("degrees") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine    = true,
                modifier      = Modifier.weight(1f)
            )
            OutlinedTextField(
                value         = speedText,
                onValueChange = { speedText = it },
                label         = { Text("speed") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine    = true,
                modifier      = Modifier.width(80.dp)
            )
            Button(onClick = {
                val d = degreesText.toIntOrNull() ?: return@Button
                val s = speedText.toIntOrNull() ?: 50
                onRunDeg(d, s)
            }) { Text("Go") }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))

        // Feedback mode
        Text("Feedback", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        val feedbackModes = listOf(
            "Pos (rel)"  to LegoHubManager.MOTOR_MODE_POS,
            "Abs (°)"    to LegoHubManager.MOTOR_MODE_APOS,
            "Power"      to LegoHubManager.MOTOR_MODE_POWER
        )
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            feedbackModes.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.mode == value,
                        onClick  = { onMode(value) },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(label, fontSize = 12.sp,
                         modifier = Modifier
                             .clickable { onMode(value) }
                             .padding(start = 2.dp, end = 8.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Position display
        val (posLabel, progressMax, progressVal) = when (state.mode) {
            LegoHubManager.MOTOR_MODE_POS  -> Triple("Position (rel)", 360, (state.position % 360 + 360) % 360)
            LegoHubManager.MOTOR_MODE_APOS -> Triple("Abs angle", 359, state.position.coerceIn(0, 359))
            else                           -> Triple("Power", 200, state.position + 100)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$posLabel: ", fontSize = 14.sp)
            Text(state.position.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (state.mode == LegoHubManager.MOTOR_MODE_APOS)
                Text(" °", fontSize = 16.sp, color = TextGray,
                     modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progressVal.toFloat() / progressMax },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
        )

        Spacer(Modifier.height(6.dp))
        Text("Raw: ${state.rawHex}", fontFamily = FontFamily.Monospace,
             fontSize = 11.sp, color = TextGray)
    }
}
