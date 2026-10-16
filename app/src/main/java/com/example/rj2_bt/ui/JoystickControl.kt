package com.example.rj2_bt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SEND_INTERVAL_MS = 50L

// Max angle gap between the two servos at full left/right (split ±10° per servo).
// Was 30°; the arms sometimes bound at the wider gap.
private const val MAX_SERVO_DIFF = 20f

// Usable servo range. Driving to a full 0°/180° puts the servos against their
// end stops, where they hunt back and forth even with no new commands.
private const val SERVO_MIN = 10f
private const val SERVO_MAX = 170f

// Servo 2 faces the opposite way from Servo 1, so the same angle number turns
// its arm the other way. Reversing its angle makes both arms move together.
// The mapping is its own inverse: it converts arm -> servo and servo -> arm.
fun servo2Reversed(angle: Float) = 180f - angle

@Composable
fun JoystickControl(
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    enabled: Boolean = true,
    resetKey: Any? = null,
    onAngle1Change: (Float) -> Unit,
    onAngle2Change: (Float) -> Unit,
    onSend: () -> Unit,
) {
    // Thumb stays where it's released; only a resetKey change recenters it.
    // One state object for the composable's lifetime: the drag handler below
    // captures it, and remember(resetKey) would swap in a new one the handler
    // never writes to, freezing the drawn thumb while the servos still move.
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(resetKey) { thumbOffset = Offset.Zero }
    val scope = rememberCoroutineScope()
    var tickerJob by remember { mutableStateOf<Job?>(null) }

    fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    Canvas(
        modifier = modifier
            .size(size)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val outerR = this.size.width / 2f
                val thumbR = outerR * 0.22f
                val maxTravel = outerR - thumbR
                detectDragGestures(
                    onDragStart = {
                        tickerJob = scope.launch {
                            while (isActive) {
                                onSend()
                                delay(SEND_INTERVAL_MS)
                            }
                        }
                    },
                    onDragEnd = {
                        stopTicker()
                        onSend()
                    },
                    onDragCancel = {
                        stopTicker()
                        onSend()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    // Each axis travels the full square independently.
                    val newOffset = thumbOffset + dragAmount
                    thumbOffset = Offset(
                        newOffset.x.coerceIn(-maxTravel, maxTravel),
                        newOffset.y.coerceIn(-maxTravel, maxTravel),
                    )
                    // Y (inverted so up = SERVO_MAX) moves both servos together; X tilts
                    // them apart, right favoring Servo 1 and left favoring Servo 2.
                    val base = SERVO_MIN + (-thumbOffset.y / maxTravel + 1f) / 2f * (SERVO_MAX - SERVO_MIN)
                    val diff = thumbOffset.x / maxTravel * (MAX_SERVO_DIFF / 2f)
                    val arm1 = (base + diff).coerceIn(SERVO_MIN, SERVO_MAX)
                    val arm2 = (base - diff).coerceIn(SERVO_MIN, SERVO_MAX)
                    onAngle1Change(arm1)
                    onAngle2Change(servo2Reversed(arm2))
                }
            }
    ) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val outerR = this.size.width / 2f
        val thumbR = outerR * 0.22f

        // Corner radius matches the thumb so it sits snugly in each corner.
        drawRoundRect(
            color = if (enabled) Color(0xFF37474F) else Color(0xFF263238),
            cornerRadius = CornerRadius(thumbR),
        )
        drawLine(
            color = Color(0xFF546E7A),
            start = Offset(0f, center.y),
            end = Offset(this.size.width, center.y),
            strokeWidth = 2f,
        )
        drawLine(
            color = Color(0xFF546E7A),
            start = Offset(center.x, 0f),
            end = Offset(center.x, this.size.height),
            strokeWidth = 2f,
        )
        drawCircle(
            color = if (enabled) Color(0xFF1976D2) else Color(0xFF455A64),
            radius = thumbR,
            center = center + thumbOffset,
        )
        drawCircle(
            color = if (enabled) Color(0xFF64B5F6) else Color(0xFF546E7A),
            radius = thumbR * 0.4f,
            center = center + thumbOffset,
        )
    }
}
