/*
    pro, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 pro contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package me.kavishdevar.librepods.presentation.glint

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A real 3D model of on-ear headphones shaped like the Beats Solo 4 (a slim arched headband, metal
 * sliders, oval cups with soft cushions and a round cap on each cup), drawn live: it turns slowly
 * like a product on a turntable, a finger can spin it (it glides to a stop and carries on turning),
 * and it's lit like a studio shot: a key light from the upper left, a soft fill, a rim light that
 * traces its edge (so a black pair still reads on the black island) and gloss on the band and the
 * sliders. Jake, 2026-10-11: "the actual model like a spinning model".
 *
 * Built from shapes in code (Beats' own 3D files aren't ours to use), in the three Solo 4 finishes
 * ([Finish], colours matched by eye). With Reduce motion it holds a three-quarter view.
 */
@Composable
fun Headphones3D(
    width: Dp,
    finish: Finish = Finish.MatteBlack,
    modifier: Modifier = Modifier,
    /** A finger can spin it (app pages; never inside the islands, whose drags are gestures). */
    draggable: Boolean = false,
    /** Turns on its own (off: a still three-quarter view). */
    spin: Boolean = true,
    /** Where it starts turned to (pictures show it from several sides). */
    startYaw: Float = HeadphoneModel.REST_YAW,
    /** On a dark background (the black island): a brighter rim, so a black pair still stands out. */
    onDark: Boolean = true,
) {
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val mesh = remember { HeadphoneModel.mesh }
    val renderer = remember(mesh) { HeadphoneRenderer(mesh) }
    var yaw by remember { mutableFloatStateOf(startYaw) }
    val scope = rememberCoroutineScope()
    // Tests render still pictures (the same switch that holds the AirPods clips still).
    val turning = spin && !reduce && PodsVideoConfig.enabled
    LaunchedEffect(turning) {
        if (!turning) return@LaunchedEffect
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L) yaw += HeadphoneModel.SPIN_RAD_PER_S * ((t - last) / 1e9f).coerceAtMost(0.05f)
            last = t
        }
    }
    Canvas(
        modifier
            .size(width, width * 0.86f)
            .then(
                if (draggable && !reduce) Modifier.pointerInput(Unit) {
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    val perPx = 3.2f / size.width.coerceAtLeast(1)
                    detectDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            // A flick glides on and slows to a stop, then the turntable carries on.
                            val speed = tracker.calculateVelocity().x * perPx
                            scope.launch {
                                var prev = 0f
                                Animatable(0f).animateDecay(speed, exponentialDecay(frictionMultiplier = 2.2f)) { yaw += value - prev; prev = value }
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        yaw += drag.x * perPx
                    }
                } else Modifier
            )
    ) {
        val tilt = if (reduce) Offset.Zero else GlassLight.tilt.value
        val pitch = HeadphoneModel.REST_PITCH + (tilt.y - GlassLight.REST_Y) * 0.25f
        drawIntoCanvas { c -> renderer.draw(c.nativeCanvas, size.width, size.height, yaw + tilt.x * 0.3f, pitch, finish, onDark) }
    }
}

/** The three Solo 4 finishes (by eye: Beats doesn't publish colour codes). */
enum class Finish(val label: String, val shell: Color, val gloss: Float) {
    MatteBlack("Matte Black", Color(0xFF1E1F22), 0.35f),
    SlateBlue("Slate Blue", Color(0xFF5E6F86), 0.5f),
    CloudPink("Cloud Pink", Color(0xFFE6C3C2), 0.55f),
}

/** The model's shape: built once, in a unit box (about -1..1), y up, x to the right. */
internal object HeadphoneModel {
    const val REST_YAW = -0.55f
    const val REST_PITCH = 0.18f
    /** One turn every 16 seconds: calm, like a turntable in a shop window. */
    const val SPIN_RAD_PER_S = (2.0 * PI / 16.0).toFloat()

    const val BAND = 0
    const val SLIDER = 1
    const val SHELL = 2
    const val CUSHION = 3
    const val CAP = 4

    class Mesh(val pos: FloatArray, val nrm: FloatArray, val mat: ByteArray, val tri: IntArray) {
        val vertices get() = pos.size / 3
        val triangles get() = tri.size / 3
    }

    val mesh: Mesh by lazy { build() }

    private class Builder {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val mat = ArrayList<Byte>(); val tri = ArrayList<Int>()
        val count get() = pos.size / 3

        /**
         * A smooth surface from a grid of points [p] (rows x cols, wrapping round the columns when
         * [wrap]); normals averaged from the faces around each point, so it shades smoothly.
         */
        fun grid(rows: Int, cols: Int, wrap: Boolean, material: Int, p: (Int, Int) -> FloatArray) {
            val base = count
            val pts = Array(rows) { r -> Array(cols) { c -> p(r, c) } }
            val n = Array(rows) { Array(cols) { FloatArray(3) } }
            val cc = if (wrap) cols else cols - 1
            for (r in 0 until rows - 1) for (c in 0 until cc) {
                val c2 = (c + 1) % cols
                val a = pts[r][c]; val b = pts[r][c2]; val d = pts[r + 1][c]
                val e1 = sub(b, a); val e2 = sub(d, a)
                val f = cross(e1, e2)
                for ((rr, ccc) in listOf(r to c, r to c2, r + 1 to c, r + 1 to c2)) add(n[rr][ccc], f)
            }
            for (r in 0 until rows) for (c in 0 until cols) {
                val q = pts[r][c]; val nn = norm(n[r][c])
                pos += q[0]; pos += q[1]; pos += q[2]
                nrm += nn[0]; nrm += nn[1]; nrm += nn[2]
                mat += material.toByte()
            }
            for (r in 0 until rows - 1) for (c in 0 until cc) {
                val c2 = (c + 1) % cols
                val i0 = base + r * cols + c; val i1 = base + r * cols + c2
                val i2 = base + (r + 1) * cols + c; val i3 = base + (r + 1) * cols + c2
                tri += i0; tri += i2; tri += i1
                tri += i1; tri += i2; tri += i3
            }
        }

        fun build() = Mesh(pos.toFloatArray(), nrm.toFloatArray(), mat.toByteArray(), tri.toIntArray())
    }

    private fun sub(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun add(into: FloatArray, v: FloatArray) { into[0] += v[0]; into[1] += v[1]; into[2] += v[2] }
    private fun norm(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    /** A superellipse's point at [a] (radians): flatter sides and rounder corners than an ellipse as [n] grows. */
    private fun superCos(a: Float, n: Float) = sign(cos(a)) * abs(cos(a)).pow(2f / n)
    private fun superSin(a: Float, n: Float) = sign(sin(a)) * abs(sin(a)).pow(2f / n)

    private fun build(): Mesh {
        val b = Builder()
        // ---- The headband: a slim, rounded-rectangle strip on an arch ----
        // The Solo 4's band comes down almost onto the cups: short sliders, cups just under its ends.
        val rx = 0.78f; val ry = 0.95f; val yc = -0.1f
        val from = Math.toRadians(10.0).toFloat(); val to = Math.toRadians(170.0).toFloat()
        val seg = 44; val prof = 12
        val halfW = 0.12f; val halfT = 0.035f
        b.grid(seg + 1, prof, wrap = true, BAND) { r, c ->
            val th = from + (to - from) * r / seg
            val cx = rx * cos(th); val cy = yc + ry * sin(th)
            // Tangent along the arch, and the outward normal in its plane.
            val tx = -rx * sin(th); val ty = ry * cos(th)
            val tl = sqrt(tx * tx + ty * ty)
            val nx = ty / tl; val ny = -tx / tl
            val a = 2f * PI.toFloat() * c / prof
            val u = superCos(a, 4f) * halfW   // across the band (z)
            val v = superSin(a, 4f) * halfT   // its thickness (outward)
            floatArrayOf(cx + nx * v, cy + ny * v, u)
        }
        // ---- The sliders: thin metal strips from the band down to each cup ----
        for (side in intArrayOf(-1, 1)) {
            val x0 = side * rx * cos(from)
            val yTop = yc + ry * sin(from) + 0.02f
            val yBot = -0.02f
            b.grid(10, 10, wrap = true, SLIDER) { r, c ->
                val y = yTop + (yBot - yTop) * r / 9f
                val a = 2f * PI.toFloat() * c / 10
                floatArrayOf(x0 + superCos(a, 6f) * 0.018f, y, superSin(a, 6f) * 0.045f)
            }
        }
        // ---- The cups: oval shells, flat outside with a soft rounded edge ----
        for (side in intArrayOf(-1, 1)) {
            val x0 = side * 0.80f
            val yC = -0.31f
            val ay = 0.33f; val bz = 0.27f
            val depth = 0.11f
            val rows = 14; val cols = 40
            b.grid(rows, cols, wrap = true, SHELL) { r, c ->
                // From the inner rim, round the edge, across the outer face to its centre.
                val beta = -PI.toFloat() / 2f + PI.toFloat() * r / (rows - 1)
                val k = abs(cos(beta)).pow(0.42f)
                val off = sign(sin(beta)) * abs(sin(beta)).pow(0.42f)
                val u = 2f * PI.toFloat() * c / cols
                floatArrayOf(x0 + side * depth * (off + 1f) * 0.5f, yC + ay * k * cos(u), bz * k * sin(u))
            }
            // ---- The cushion on the inside: a soft oval ring ----
            b.grid(10, cols, wrap = true, CUSHION) { r, c ->
                val w = 2f * PI.toFloat() * r / 9
                val u = 2f * PI.toFloat() * c / cols
                val rr = 0.06f
                val ky = ay - 0.07f + rr * cos(w); val kz = bz - 0.06f + rr * cos(w)
                floatArrayOf(x0 - side * (0.035f - rr * 0.9f * sin(w)), yC + ky * cos(u), kz * sin(u))
            }
            // ---- The round cap on the outside ----
            b.grid(6, 28, wrap = true, CAP) { r, c ->
                val rad = 0.13f * (1f - r / 5f)
                val u = 2f * PI.toFloat() * c / 28
                val bulge = 0.012f * (1f - (r / 5f) * (r / 5f))
                floatArrayOf(x0 + side * (depth + 0.004f + bulge), yC + rad * cos(u), rad * sin(u))
            }
        }
        return b.build()
    }
}

/** Draws [HeadphoneModel]'s mesh: turned, lit, sorted back to front, as one batch of triangles. */
internal class HeadphoneRenderer(private val m: HeadphoneModel.Mesh) {
    private val v = m.vertices
    private val screen = FloatArray(v * 2)
    private val depth = FloatArray(v)
    private val colors = IntArray(v)
    private val order = Array(m.triangles) { it }
    private val triDepth = FloatArray(m.triangles)
    private val indices = ShortArray(m.tri.size)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    init { require(v < Short.MAX_VALUE) { "too many vertices for one batch" } }

    fun draw(canvas: android.graphics.Canvas, w: Float, h: Float, yaw: Float, pitch: Float, finish: Finish, onDark: Boolean = true) {
        // On dark backgrounds the rim and the ambient light come up, so the outline always reads.
        val rimK = if (onDark) 0.95f else 0.5f
        val ambient = if (onDark) 0.3f else 0.2f
        val cy = cos(yaw); val sy = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
        val cam = 4.2f
        val f = minOf(w / 2.15f, h / 1.9f) * cam
        val ox = w / 2f; val oy = h * 0.55f
        val shell = finish.shell
        val metal = Color(0xFFD9DCE1)
        val cushion = androidx.compose.ui.graphics.lerp(shell, Color(0xFF0E0E10), 0.55f)
        val cap = androidx.compose.ui.graphics.lerp(shell, Color.White, 0.08f)
        // Lights in view space: key (upper left, in front), fill (right), rim (behind).
        val k = n3(-0.55f, 0.65f, 0.55f); val fl = n3(0.7f, 0.05f, 0.45f); val rim = n3(0.25f, 0.45f, -0.85f)
        for (i in 0 until v) {
            val x = m.pos[i * 3]; val y = m.pos[i * 3 + 1]; val z = m.pos[i * 3 + 2]
            // Turn round y, then tip toward you round x.
            val x1 = x * cy + z * sy; val z1 = -x * sy + z * cy
            val y2 = y * cp - z1 * sp; val z2 = y * sp + z1 * cp
            val nx0 = m.nrm[i * 3]; val ny0 = m.nrm[i * 3 + 1]; val nz0 = m.nrm[i * 3 + 2]
            val nx1 = nx0 * cy + nz0 * sy; val nz1 = -nx0 * sy + nz0 * cy
            val ny2 = ny0 * cp - nz1 * sp; val nz2 = ny0 * sp + nz1 * cp
            val d = cam - z2
            screen[i * 2] = ox + f * x1 / d
            screen[i * 2 + 1] = oy - f * y2 / d
            depth[i] = z2
            // Shading: ambient + key + fill, gloss (Blinn-Phong), and a rim along the silhouette.
            val vx = -x1; val vy = -y2; val vz = d
            val vl = sqrt(vx * vx + vy * vy + vz * vz)
            val ex = vx / vl; val ey = vy / vl; val ez = vz / vl
            // Two-sided: a face turned away is lit as if it faced you (the inside of the band).
            val facing = nx1 * ex + ny2 * ey + nz2 * ez
            val s = if (facing < 0f) -1f else 1f
            val nx = nx1 * s; val ny = ny2 * s; val nz = nz2 * s
            val mat = m.mat[i].toInt()
            val base = when (mat) {
                HeadphoneModel.SLIDER -> metal
                HeadphoneModel.CUSHION -> cushion
                HeadphoneModel.CAP -> cap
                else -> shell
            }
            val (gloss, shine) = when (mat) {
                HeadphoneModel.SLIDER -> 0.9f to 60f
                HeadphoneModel.BAND -> finish.gloss to 28f
                HeadphoneModel.CAP -> (finish.gloss + 0.25f) to 40f
                HeadphoneModel.CUSHION -> 0.08f to 6f
                else -> finish.gloss * 0.8f to 18f
            }
            val dk = maxOf(0f, nx * k[0] + ny * k[1] + nz * k[2])
            val df = maxOf(0f, nx * fl[0] + ny * fl[1] + nz * fl[2])
            val hx = k[0] + ex; val hy = k[1] + ey; val hz = k[2] + ez
            val hl = sqrt(hx * hx + hy * hy + hz * hz)
            val spec = maxOf(0f, (nx * hx + ny * hy + nz * hz) / hl).pow(shine) * gloss
            val edge = (1f - maxOf(0f, nx * ex + ny * ey + nz * ez)).pow(3f)
            val back = maxOf(0f, nx * rim[0] + ny * rim[1] + nz * rim[2])
            val rimLight = edge * (0.35f + 0.65f * back) * rimK
            val light = ambient + 0.72f * dk + 0.18f * df
            fun ch(c: Float) = (c * light + spec + rimLight).coerceIn(0f, 1f)
            colors[i] = Color(ch(base.red), ch(base.green), ch(base.blue), 1f).toArgb()
        }
        // Back to front (the farthest triangles first), so nearer parts cover farther ones.
        for (t in 0 until m.triangles) {
            triDepth[t] = depth[m.tri[t * 3]] + depth[m.tri[t * 3 + 1]] + depth[m.tri[t * 3 + 2]]
        }
        order.sortWith { a, b -> triDepth[a].compareTo(triDepth[b]) }
        var j = 0
        for (t in order) {
            indices[j++] = m.tri[t * 3].toShort(); indices[j++] = m.tri[t * 3 + 1].toShort(); indices[j++] = m.tri[t * 3 + 2].toShort()
        }
        canvas.drawVertices(
            android.graphics.Canvas.VertexMode.TRIANGLES, screen.size, screen, 0, null, 0, colors, 0,
            indices, 0, indices.size, paint,
        )
    }

    private fun n3(x: Float, y: Float, z: Float): FloatArray {
        val l = sqrt(x * x + y * y + z * z)
        return floatArrayOf(x / l, y / l, z / l)
    }
}
