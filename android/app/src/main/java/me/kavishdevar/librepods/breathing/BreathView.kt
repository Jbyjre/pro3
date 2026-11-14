package me.kavishdevar.librepods.breathing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Shared by the real launch and the preview. A single expanding, luminous bloom in one second. */
class BreathView(context: Context) : View(context) {
    var destination = ""
    var onFirstFrame: (() -> Unit)? = null
    var onComplete: (() -> Unit)? = null
    var renderAt: Long? = null // deterministic transition evidence, never used for launches
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var start: Long? = null
    private var finished = false
    private val reduce = GlintComfort.reduceMotion(context)
    private val dark = context.resources.configuration.uiMode and 48 == 32
    private val ink = if (dark) Color.rgb(242, 243, 238) else Color.rgb(31, 44, 43)
    init {
        setOnApplyWindowInsetsListener { _, insets ->
            val system = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            setPadding(system.left, system.top, system.right, system.bottom)
            insets
        }
        contentDescription = "Breathe in. One second."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
        isFocusable = true
    }
    override fun onTouchEvent(event: MotionEvent): Boolean = true
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(canvas: Canvas) = renderFrame(canvas)

    /** The same paint path is used directly by the Compose preview, without an embedded window. */
    fun renderFrame(canvas: Canvas) {
        val now = SystemClock.elapsedRealtime()
        if (start == null) { start = now; onFirstFrame?.invoke() }
        val elapsed = renderAt ?: (now - start!!)
        val t = (elapsed / 1000f).coerceIn(0f, 1f)
        val ease = 1f - (1f - t) * (1f - t) * (1f - t)
        paint.color = if (dark) Color.rgb(15, 28, 28) else Color.rgb(246, 247, 239)
        paint.alpha = 255
        // AndroidView can share its parent's canvas; never clear outside this view's bounds.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val d = resources.displayMetrics.density
        val unit = minOf(width / d, height / d)
        val cx = width / 2f
        val compact = height / d < 600f
        val cy = height * if (compact) 0.34f else 0.45f
        val radius = (if (compact) 0.78f else 1f) * unit * d * (if (reduce) 0.23f else 0.14f + ease * 0.11f)
        val colors = intArrayOf(0xff8edbcc.toInt(), 0xffafbbe9.toInt(), 0xffe2c489.toInt(), 0xff8edbcc.toInt())
        canvas.save()
        canvas.rotate(if (reduce) 0f else -12f + ease * 24f, cx, cy)
        // Translucent overlapping petals, with no rotation loop or loading indicator.
        for (i in 0..5) {
            val a = i * PI / 3
            paint.color = colors[i % 3]
            paint.alpha = if (dark) 135 else 120
            val spread = radius * 0.35f
            canvas.drawCircle(cx + cos(a).toFloat() * spread, cy + sin(a).toFloat() * spread, radius * 0.69f, paint)
        }
        paint.color = if (dark) 0xffceddd0.toInt() else 0xfff6f7ef.toInt()
        paint.alpha = 230
        canvas.drawCircle(cx, cy, radius * 0.32f, paint)
        canvas.restore()
        paint.alpha = 255
        paint.color = ink
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val scale = resources.configuration.fontScale.coerceAtMost(2f)
        paint.textSize = minOf(34 * d * scale, width / 7f)
        val titleY = if (compact) height * 0.69f else cy + unit * d * 0.40f
        canvas.drawText("Breathe in.", cx, titleY, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = minOf(14 * d * scale, width / 24f)
        paint.alpha = 170
        canvas.drawText("A little space for you", cx, titleY + 30 * d * scale, paint)
        if (destination.isNotBlank()) {
            paint.textSize = minOf(12 * d * scale, width / 30f)
            val label = android.text.TextUtils.ellipsize(destination, android.text.TextPaint(paint), width * 0.8f, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(label, cx, height - maxOf(48 * d, paddingBottom.toFloat() + 24 * d), paint)
        }
        if (renderAt == null && elapsed < BreathSession.DURATION_MS && isAttachedToWindow) postInvalidateOnAnimation()
        if (renderAt == null && elapsed >= BreathSession.DURATION_MS && !finished) { finished = true; onComplete?.invoke() }
    }
}
