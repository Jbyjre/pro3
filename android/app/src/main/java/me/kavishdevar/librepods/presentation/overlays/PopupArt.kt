package me.kavishdevar.librepods.presentation.overlays
import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

// Artwork shared by headphone pop-ups, music controls and navigation.
internal fun centreSquare(img: ImageBitmap): Pair<IntOffset, IntSize> {
    val side = minOf(img.width, img.height).coerceAtLeast(1)
    return IntOffset((img.width - side) / 2, (img.height - side) / 2) to IntSize(side, side)
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBudPair(c: Offset, size: Float, color: Color) {
    val head = size * 0.17f
    for (s in listOf(-1f, 1f)) {
        val hc = Offset(c.x + s * size * 0.24f, c.y - size * 0.2f)
        drawCircle(color, head, hc)
        // The stem hangs from the outer side of the head.
        val top = Offset(hc.x + s * head * 0.55f, hc.y + head * 0.25f)
        drawLine(color, top, Offset(top.x + s * size * 0.02f, c.y + size * 0.42f), size * 0.13f, StrokeCap.Round)
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNote(c: Offset, size: Float, color: Color) {
    val headR = size * 0.2f
    val head = Offset(c.x - size * 0.12f, c.y + size * 0.28f)
    drawCircle(color, headR, head)
    val stemX = head.x + headR * 0.9f
    drawLine(color, Offset(stemX, head.y), Offset(stemX, c.y - size * 0.42f), size * 0.1f, StrokeCap.Round)
    drawLine(color, Offset(stemX, c.y - size * 0.42f), Offset(stemX + size * 0.3f, c.y - size * 0.25f), size * 0.1f, StrokeCap.Round)
}

internal fun accentOfPixels(pixels: IntArray): Color {
    val hsv = FloatArray(3)
    var best = -1f
    var bestHsv: FloatArray? = null
    for (p in pixels) {
        android.graphics.Color.colorToHSV(p, hsv)
        if (hsv[2] < 0.25f) continue
        val score = hsv[1] * hsv[2]
        if (score > best) { best = score; bestHsv = hsv.copyOf() }
    }
    val h = bestHsv ?: return Color.White
    if (h[1] < 0.22f) return Color.White
    h[1] = h[1].coerceAtMost(0.7f)
    h[2] = h[2].coerceAtLeast(0.9f)
    return Color(android.graphics.Color.HSVToColor(h))
}

internal fun accentOf(art: ImageBitmap): Color = try {
    val small: Bitmap = art.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false).scale(12, 12)
    accentOfPixels(IntArray(144).also { small.getPixels(it, 0, 12, 0, 0, 12, 12) })
} catch (_: Exception) { Color.White }
