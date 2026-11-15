package me.kavishdevar.librepods.breathing

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsetsController
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import kotlin.math.PI
import kotlin.math.sin

/** One opaque sheet and two inks. Preview and launch share this exact drawing path. */
class BreathView(context: Context) : View(context) {
    var destination = ""
        set(value) { field=value; labelWidth=-1f; invalidate() }
    var onFirstFrame: (() -> Unit)? = null
    var onSystemBarAppearance: ((Int) -> Unit)? = null
    var renderAt: Long? = null // Preview/evidence clock; launches always use elapsedRealtime.
    var awaitingAppearance=false
    var options = BreathOptions()
        set(value) { field=value; awaitingAppearance=false; gradient=null; invalidate() }
    private val systemReduced = GlintComfort.reduceMotion(context)
    val motionReduced: Boolean get() = systemReduced || options.still
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isDither=true }
    private val sheet = Path()
    private val titleFace = Typeface.create("sans-serif", Typeface.BOLD)
    private val bodyFace = Typeface.create("sans-serif", Typeface.NORMAL)
    private var start: Long? = null
    private var gradient: Gradient? = null
    private data class Gradient(val width: Int, val height: Int, val shader: Shader)
    private var labelWidth=-1f
    private var labelSize=0f
    private var label = ""
    private var lastBarAppearance=-1

    init {
        setOnApplyWindowInsetsListener { _, insets ->
            val system=insets.getInsets(android.view.WindowInsets.Type.systemBars())
            setPadding(system.left,system.top,system.right,system.bottom)
            insets
        }
        contentDescription="Breathe in. One second."
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable=true
        isFocusable=true
    }
    override fun onTouchEvent(event: MotionEvent): Boolean = true
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(canvas: Canvas) = renderFrame(canvas)

    fun renderFrame(canvas: Canvas) {
        val now=SystemClock.elapsedRealtime()
        if (start==null) { start=now; onFirstFrame?.invoke() }
        val elapsed=renderAt ?: (now-start!!)
        val t=(elapsed/BreathSession.DURATION_MS.toFloat()).coerceIn(0f,1f)
        val w=width.toFloat(); val h=height.toFloat()
        if (w<=0f || h<=0f) return
        val reduce=motionReduced
        val progress=if (reduce) 1f else t*t*(3f-2f*t)
        val palette=options.palette
        // A cold launch paints the neutral instruction immediately while saved appearance loads.
        paint.shader=null; paint.color=BreathPalette.BASE
        canvas.drawRect(0f,0f,w,h,paint)
        if (awaitingAppearance) { publishBars(0); drawWords(canvas,w,h,BreathPalette.BASE_INK); return }
        val g=gradient?.takeIf { it.width==width && it.height==height } ?: Gradient(width,height,
            LinearGradient(0f,h,w*0.35f,0f,palette.gradientColors,null,Shader.TileMode.CLAMP)
        ).also { gradient=it }

        // Keep every draw bounded: the embedded preview must not erase surrounding controls.
        val front=h*(1.01f-1.18f*progress)
        val bend=if (reduce) 0f else sin(PI*t).toFloat()*minOf(w*0.16f,h*0.055f)
        val bars=(if (palette.darkInk && front+bend < paddingTop/2f) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0) or
            (if (palette.darkInk && front+bend < h-paddingBottom/2f) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0)
        publishBars(bars)
        sheet.reset(); sheet.moveTo(0f,front+bend)
        sheet.cubicTo(w*0.35f,front+bend,w*0.67f,front-bend,w,front-bend)
        sheet.lineTo(w,h); sheet.lineTo(0f,h); sheet.close()
        paint.shader=g.shader
        canvas.drawPath(sheet,paint)
        paint.shader=null

        // Each side of the moving edge has its own accessible ink; no muddy text scrim or fade.
        val baseSave=canvas.save()
        canvas.clipOutPath(sheet)
        drawWords(canvas,w,h,BreathPalette.BASE_INK)
        canvas.restoreToCount(baseSave)
        val save=canvas.save()
        canvas.clipPath(sheet)
        drawWords(canvas,w,h,palette.ink)
        canvas.restoreToCount(save)
        if (renderAt==null && !reduce && elapsed<BreathSession.DURATION_MS && isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun publishBars(appearance: Int) {
        if (appearance!=lastBarAppearance) { lastBarAppearance=appearance; onSystemBarAppearance?.invoke(appearance) }
    }

    private fun drawWords(canvas: Canvas,w: Float,h: Float,ink: Int) {
        val d=resources.displayMetrics.density
        val scale=resources.configuration.fontScale.coerceAtMost(2f)
        val compact=h < w*1.25f
        val margin=maxOf(paddingLeft.toFloat(),minOf(32*d,w*0.08f))
        val available=w-margin-maxOf(paddingRight.toFloat(),margin)
        val titleSize=if (compact) minOf(46*d*scale,available/6f,h*0.18f)
            else minOf(64*d*scale,available/3.8f,h*0.11f)
        val baseline=if (compact) h*0.48f else h*0.44f
        paint.color=ink; paint.alpha=255; paint.textAlign=Paint.Align.LEFT
        paint.typeface=titleFace; paint.textSize=titleSize
        if (compact) canvas.drawText("Breathe in.",margin,baseline,paint)
        else {
            canvas.drawText("Breathe",margin,baseline,paint)
            canvas.drawText("in.",margin,baseline+titleSize*1.02f,paint)
        }
        paint.typeface=bodyFace
        paint.textSize=minOf(14*d*scale,available/17f,h*0.06f)
        val captionY=baseline+titleSize*if (compact) 0.92f else 1.85f
        canvas.drawText("One second.",margin,captionY,paint)
        if (destination.isNotBlank()) {
            paint.textSize=minOf(12*d*scale,available/22f)
            if (labelWidth!=available || labelSize!=paint.textSize) {
                label=TextUtils.ellipsize(destination,paint,available,TextUtils.TruncateAt.END).toString()
                labelWidth=available; labelSize=paint.textSize
            }
            canvas.drawText(label,margin,h-maxOf(32*d,paddingBottom+20*d),paint)
        }
    }
}
