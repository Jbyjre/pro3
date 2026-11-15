package me.kavishdevar.librepods.presentation.overlays
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.services.IslandTimer

@Composable
internal fun TimerPanel(content: Color, secondary: Color, dark: Boolean, active: Boolean, reduceMotion: Boolean, onTouch: () -> Unit, onClose: () -> Unit) {
    val c = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("Time is up", color = content, style = MaterialTheme.typography.headlineSmall)
        Row {
            TextButton(onClick = { onTouch(); IslandTimer.cancel(c); onClose() }) { Text("Stop", color = content) }
            TextButton(onClick = { onTouch(); IslandTimer.addMinute(c); onClose() }) { Text("One more minute", color = content) }
        }
    }
}

internal fun DrawScope.drawTorch(c: Offset, size: Float, color: Color) {
    // A torch from the side, pointing up: a wide head, a body, and its switch.
    val u = size / 2f
    drawPath(Path().apply {
        moveTo(c.x - u * 0.62f, c.y - u)
        lineTo(c.x + u * 0.62f, c.y - u)
        lineTo(c.x + u * 0.62f, c.y - u * 0.62f)
        lineTo(c.x + u * 0.32f, c.y - u * 0.18f)
        lineTo(c.x + u * 0.32f, c.y + u)
        lineTo(c.x - u * 0.32f, c.y + u)
        lineTo(c.x - u * 0.32f, c.y - u * 0.18f)
        lineTo(c.x - u * 0.62f, c.y - u * 0.62f)
        close()
    }, color, style = Stroke(u * 0.16f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawCircle(color, u * 0.11f, Offset(c.x, c.y + u * 0.28f))
}

internal fun DrawScope.drawStopwatch(c: Offset, size: Float, color: Color) {
    val u = size / 2f
    val sw = u * 0.16f
    val face = Offset(c.x, c.y + u * 0.12f)
    drawCircle(color, u * 0.78f, face, style = Stroke(sw))
    drawLine(color, Offset(c.x - u * 0.26f, c.y - u * 0.92f), Offset(c.x + u * 0.26f, c.y - u * 0.92f), sw, StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y - u * 0.92f), Offset(c.x, c.y - u * 0.66f), sw, StrokeCap.Round)
    drawLine(color, face, Offset(face.x + u * 0.3f, face.y - u * 0.34f), sw, StrokeCap.Round)
}

