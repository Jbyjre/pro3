package me.kavishdevar.librepods.breathing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import kotlin.math.PI
import kotlin.math.sin

/** A full-screen color sheet. Production launch and preview use this exact paint path. */
class BreathView(context: Context) : View(context) {
    var destination = ""
    var onFirstFrame: (() -> Unit)? = null
    var renderAt: Long? = null // Preview/evidence clock only; real launches use elapsedRealtime.
    var options = BreathOptions()
        set(value) { field = value; gradients = null; invalidate() }
    private val systemReduced = GlintComfort.reduceMotion(context)
    val motionReduced: Boolean get() = systemReduced || options.still
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wave = Path()
    private val titleFace = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bodyFace = Typeface.create("sans-serif", Typeface.NORMAL)
    private var start: Long? = null
    private var gradients: Gradients? = null
    private data class Gradients(val width: Int, val height: Int, val color: Shader, val center: Shader, val bottom: Shader)

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

    fun renderFrame(canvas: Canvas) {
        val now = SystemClock.elapsedRealtime()
        if (start == null) { start = now; onFirstFrame?.invoke() }
        val elapsed = renderAt ?: (now-start!!)
        val t = (elapsed/BreathSession.DURATION_MS.toFloat()).coerceIn(0f,1f)
        val reduce = motionReduced
        val progress = if (reduce) 1f else smooth(t)
        // The sheet stays filled until the activity hands over at the same one-second deadline.
        val fade = 1f
        val w=width.toFloat(); val h=height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d=resources.displayMetrics.density
        val compact=h/d < 600f
        val centerY=h * if (compact) 0.48f else 0.47f
        val g=gradients?.takeIf { it.width==width && it.height==height } ?: Gradients(width,height,
            LinearGradient(0f,h,w,0f,options.palette.primary,options.palette.secondary,Shader.TileMode.CLAMP),
            RadialGradient(w/2f,centerY,w*0.90f,
                intArrayOf(0x70000000,0x44000000,Color.TRANSPARENT),floatArrayOf(0f,0.4f,1f),Shader.TileMode.CLAMP),
            LinearGradient(0f,h*0.73f,0f,h,Color.TRANSPARENT,0x78000000,Shader.TileMode.CLAMP)).also { gradients=it }
        paint.shader=null; paint.color=0xff0c1425.toInt(); paint.alpha=255
        // Bound every draw: the preview must never paint over its surrounding controls.
        canvas.drawRect(0f,0f,w,h,paint)
        val front=if (reduce) -h*0.2f else h*(1.12f-1.40f*progress)
        val sway=if (reduce) 0f else sin(PI*t).toFloat()*h*0.055f
        fun sheet(y: Float, alpha: Float) {
            wave.reset(); wave.moveTo(0f,y+sway)
            wave.cubicTo(w*0.28f,y-sway,w*0.72f,y+sway*0.35f,w,y-sway)
            wave.lineTo(w,h); wave.lineTo(0f,h); wave.close()
            paint.shader=g.color; paint.alpha=(255*alpha*fade).toInt().coerceIn(0,255)
            canvas.drawPath(wave,paint)
        }
        sheet(front-h*0.045f,0.20f)
        sheet(front,0.94f)
        paint.alpha=255; paint.shader=g.center; canvas.drawRect(0f,0f,w,h,paint)
        paint.shader=g.bottom; canvas.drawRect(0f,0f,w,h,paint)
        paint.shader=null; paint.color=Color.WHITE; paint.textAlign=Paint.Align.CENTER
        val scale=resources.configuration.fontScale.coerceAtMost(2f)
        val titleSize=minOf(36*d*scale,w/6.6f,h*0.13f)
        paint.typeface=titleFace; paint.textSize=titleSize; paint.alpha=(255*fade).toInt()
        canvas.drawText("Breathe in.",w/2f,centerY,paint)
        paint.typeface=bodyFace; paint.textSize=minOf(14*d*scale,w/23f,h*0.065f)
        paint.alpha=(230*fade).toInt()
        canvas.drawText("One second, just for you",w/2f,centerY+titleSize*0.85f,paint)
        if (destination.isNotBlank()) {
            paint.textSize=minOf(12*d*scale,w/28f); paint.alpha=(255*fade).toInt()
            val label=android.text.TextUtils.ellipsize(destination,android.text.TextPaint(paint),w*0.8f,android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(label,w/2f,h-maxOf(40*d,paddingBottom+20*d),paint)
        }
        if (renderAt==null && !reduce && elapsed<BreathSession.DURATION_MS && isAttachedToWindow) postInvalidateOnAnimation()
    }
    private fun smooth(t: Float)=t*t*(3f-2f*t)
}
