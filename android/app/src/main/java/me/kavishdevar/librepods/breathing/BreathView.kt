package me.kavishdevar.librepods.breathing

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.content.res.ResourcesCompat
import me.kavishdevar.librepods.R
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The pause layer. The phone, the pinned-shortcut route and the preview all draw it through
 * [renderFrame], so what you preview is exactly what you get.
 *
 * Built so it never flickers:
 *  - it is opaque from its very first frame, in a dark shade of the SAME hue as the gradient, so the
 *    first thing you see is never an unrelated color;
 *  - the gradient rises through that base; nothing is ever drawn twice at partial opacity;
 *  - it clears by fading, or lifting away, finishing exactly when the run ends, never with a hard cut;
 *  - motion follows the display's frame clock, so a dropped frame never changes how long it lasts;
 *  - nothing is measured or rebuilt while it animates: type, shaders and shapes are prepared once.
 */
class BreathView(context: Context) : View(context) {
    var destination = ""
        set(value) { field = value; invalidate() }
    /** What this pause says. The verse reference and your own words are one steady phrase. */
    var saying: Saying = BreathWords.sayingFor(BreathWords.Verse, "")
        set(value) { if (value != field) { field = value; words = null; describe(); invalidate() } }
    var onFirstFrame: (() -> Unit)? = null
    var onSystemBarAppearance: ((Int) -> Unit)? = null
    var renderAt: Long? = null // Preview/evidence clock; real pauses always use the display's frame clock.
    var awaitingAppearance = false
    var options = BreathOptions()
        set(value) {
            val changed = field != value
            val waiting = awaitingAppearance
            field = value
            awaitingAppearance = false
            if (changed) { rebuildTimeline(); gradient = null; words = null; describe() }
            if (changed || waiting) invalidate()
        }
    var systemReduced = false
        set(value) { if (field != value) { field = value; rebuildTimeline(); invalidate() } }
    val motionReduced: Boolean get() = systemReduced || options.still

    private var timeline = BreathTimeline(BreathOptions.DEFAULT_MS, BreathTransition.Fade, false)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheet = Path()
    private val edge = Path()
    private val mask = Path()
    private val headFace = face(R.font.barlow_condensed_extrabold, Typeface.BOLD)
    private val noteFace = face(R.font.barlow_condensed_semibold, Typeface.NORMAL)

    private var start: Long? = null
    private var lastT = 0f
    private var lastBarAppearance = -1
    private var gradient: Gradient? = null
    private var words: Words? = null
    private var nameWidth = -1f
    private var name = ""
    private var touching = false
    private var touchX = 0f
    private var touchY = 0f
    private var touchLevel = 0f

    private class Gradient(val width: Int, val height: Int, val sheet: Shader, val rim: Shader, val rimVertical: Shader, val glow: Shader, val glowRadius: Float)
    private class Words(
        val headSize: Float, val noteSize: Float, val margin: Float, val firstBaseline: Float, val lineHeight: Float,
        val inLines: List<String>, val outLines: List<String>, val inNote: String, val outNote: String, val noteGap: Float,
    )

    init {
        setOnApplyWindowInsetsListener { _, insets ->
            val system = insets.getInsets(WindowInsets.Type.systemBars())
            if (system.left != paddingLeft || system.top != paddingTop || system.right != paddingRight || system.bottom != paddingBottom) {
                setPadding(system.left, system.top, system.right, system.bottom)
                words = null
            }
            insets
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        describe()
    }

    private fun face(id: Int, style: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.create("sans-serif-condensed", style)

    private fun rebuildTimeline() { timeline = BreathTimeline(options.durationMs, options.transition, motionReduced) }

    private fun describe() {
        val said = if (saying.inHead == saying.outHead) saying.inHead else "${saying.inHead} Then ${saying.outHead}"
        contentDescription = "Breathing pause, ${options.seconds.roundToInt()} seconds. $said"
    }

    /** Reuse the prepared native layer without carrying a previous pause's clock. */
    fun restart() {
        start = null; lastT = 0f; lastBarAppearance = -1
        touching = false; touchLevel = 0f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); gradient = null; words = null }
    override fun onConfigurationChanged(newConfig: Configuration?) { super.onConfigurationChanged(newConfig); words = null }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { touching = true; touchX = event.x; touchY = event.y }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
        }
        invalidate() // a frame is needed even when the picture is otherwise still
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(canvas: Canvas) = renderFrame(canvas)

    fun renderFrame(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val preview = renderAt != null
        // drawingTime is Android's own timestamp for this frame (steady, unlike "now"). Off-screen draws have none.
        val frameNow = if (preview) 0L else drawingTime.takeIf { it > 0L } ?: SystemClock.uptimeMillis()
        if (start == null) { start = frameNow; lastT = 0f; onFirstFrame?.invoke() }
        val elapsed = renderAt ?: (frameNow - (start ?: frameNow)).coerceAtLeast(0L)
        if (awaitingAppearance) { drawNeutral(canvas, w, h); return }

        val tl = timeline
        val t = tl.fraction(elapsed)
        val d = resources.displayMetrics.density
        val palette = options.palette
        val reduce = motionReduced
        val g = gradient?.takeIf { it.width == width && it.height == height } ?: buildGradient(palette).also { gradient = it }
        val sheetP = tl.sheet(t)
        val alpha = tl.layerAlpha(t)
        val lift = tl.lift(t)
        val drop = tl.drop(t)
        val iris = tl.iris(t)
        val slide = tl.slide(t)
        val zoom = tl.zoom(t)

        // Where the gradient's leading edge is, and how much it curves. Curve is zero when still.
        val front = h * (1.02f - 1.24f * sheetP)
        val bend = if (reduce) 0f else sin(PI * sheetP).toFloat() * minOf(w * 0.16f, h * 0.055f)
        edge.reset(); edge.moveTo(0f, front + bend)
        edge.cubicTo(w * 0.35f, front + bend, w * 0.67f, front - bend, w, front - bend)
        sheet.set(edge); sheet.lineTo(w, h); sheet.lineTo(0f, h); sheet.close()
        publishBars(
            (if (palette.darkInk && front + bend < paddingTop / 2f) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0) or
                (if (palette.darkInk && front + bend < h - paddingBottom / 2f) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0),
        )

        // One offscreen layer for the whole picture while it fades, so nothing is blended twice.
        val layerSave = if (alpha < 0.999f) canvas.saveLayerAlpha(0f, 0f, w, h, (alpha * 255f).roundToInt().coerceIn(0, 255)) else canvas.save()
        if (lift > 0f) {
            val edgeY = h * (1f - lift)
            val b = sin(PI * lift).toFloat() * minOf(w * 0.14f, h * 0.05f)
            mask.reset(); mask.moveTo(0f, 0f); mask.lineTo(w, 0f); mask.lineTo(w, edgeY - b)
            mask.cubicTo(w * 0.67f, edgeY - b, w * 0.35f, edgeY + b, 0f, edgeY + b); mask.close()
            canvas.clipPath(mask)
        } else if (drop > 0f) {
            // The curtain falls: what is left is the part below a curved, descending edge.
            val edgeY = h * drop
            val b = sin(PI * drop).toFloat() * minOf(w * 0.14f, h * 0.05f)
            mask.reset(); mask.moveTo(0f, h); mask.lineTo(w, h); mask.lineTo(w, edgeY - b)
            mask.cubicTo(w * 0.67f, edgeY - b, w * 0.35f, edgeY + b, 0f, edgeY + b); mask.close()
            canvas.clipPath(mask)
        } else if (iris > 0f) {
            // A lens opens from the middle: everything outside a growing circle is what remains.
            mask.reset(); mask.addCircle(w / 2f, h / 2f, irisRadius(w, h) * iris, Path.Direction.CW)
            canvas.clipOutPath(mask)
        }
        val content = canvas.save()
        if (lift > 0f) canvas.translate(0f, -lift * h * 0.10f)
        if (drop > 0f) canvas.translate(0f, drop * h * 0.10f)
        if (slide > 0f) canvas.translate(slide * w, 0f)
        if (zoom > 0f) { val scale = 1f + 0.12f * zoom; canvas.scale(scale, scale, w / 2f, h / 2f) }

        fill.shader = null; fill.color = palette.deep
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = g.sheet
        canvas.drawPath(sheet, fill)
        fill.shader = null

        if (!reduce && sheetP > 0.01f && sheetP < 0.99f) drawRim(canvas, edge, g, d, minOf(1f, sheetP * 10f) * minOf(1f, (1f - sheetP) * 8f), false)

        // A soft light follows a finger, so there is something to do besides wait.
        touchLevel += ((if (touching && !reduce) 1f else 0f) - touchLevel) * (if (touching) 0.35f else 0.12f)
        if (touchLevel < 0.01f) touchLevel = 0f else {
            glow.shader = g.glow; glow.alpha = (touchLevel * 255f).roundToInt().coerceIn(0, 255)
            val gs = canvas.save(); canvas.translate(touchX, touchY)
            canvas.drawCircle(0f, 0f, g.glowRadius, glow)
            canvas.restoreToCount(gs)
        }

        // Each side of the moving edge has its own accessible ink; no muddy text scrim or fade.
        val inkBase = canvas.save()
        canvas.clipOutPath(sheet)
        drawWords(canvas, w, h, BreathPalette.BASE_INK, elapsed, tl)
        canvas.restoreToCount(inkBase)
        val inkSheet = canvas.save()
        canvas.clipPath(sheet)
        drawWords(canvas, w, h, palette.ink, elapsed, tl)
        canvas.restoreToCount(inkSheet)
        drawProgress(canvas, w, h, t, d, if (front + bend < h - paddingBottom / 2f) palette.ink else BreathPalette.BASE_INK)
        canvas.restoreToCount(content)

        // Light catching the leading edge of whichever transition is running.
        if (lift > 0f || drop > 0f) {
            val p = maxOf(lift, drop)
            val edgeY = if (lift > 0f) h * (1f - lift) else h * drop
            val b = sin(PI * p).toFloat() * minOf(w * 0.14f, h * 0.05f)
            edge.reset(); edge.moveTo(w, edgeY - b); edge.cubicTo(w * 0.67f, edgeY - b, w * 0.35f, edgeY + b, 0f, edgeY + b)
            drawRim(canvas, edge, g, d, minOf(1f, p * 10f) * minOf(1f, (1f - p) * 8f), false)
        } else if (iris > 0f) {
            edge.reset(); edge.addCircle(w / 2f, h / 2f, irisRadius(w, h) * iris, Path.Direction.CW)
            drawRim(canvas, edge, g, d, minOf(1f, iris * 10f) * minOf(1f, (1f - iris) * 8f), false)
        } else if (slide > 0f) {
            edge.reset(); edge.moveTo(slide * w, 0f); edge.lineTo(slide * w, h)
            drawRim(canvas, edge, g, d, minOf(1f, slide * 10f) * minOf(1f, (1f - slide) * 8f), true)
        }
        canvas.restoreToCount(layerSave)

        if (!preview) {
            cues(t)
            schedule(elapsed, t, reduce)
        }
    }

    /** Far enough to clear every corner, so the opened lens ends with nothing left. */
    private fun irisRadius(w: Float, h: Float): Float = kotlin.math.hypot(w, h) / 2f * 1.06f

    private fun buildGradient(palette: BreathPalette): Gradient {
        val w = width.toFloat(); val h = height.toFloat()
        val radius = minOf(w, h) * 0.55f
        return Gradient(
            width, height,
            LinearGradient(0f, h, w * 0.35f, 0f, palette.gradientColors, null, Shader.TileMode.CLAMP),
            LinearGradient(0f, 0f, w, 0f, intArrayOf(0x00ffffff, 0xd9ffffff.toInt(), 0x00ffffff), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP),
            LinearGradient(0f, 0f, 0f, h, intArrayOf(0x00ffffff, 0xd9ffffff.toInt(), 0x00ffffff), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP),
            RadialGradient(0f, 0f, radius, intArrayOf(0x3dffffff, 0x17ffffff, 0x00ffffff), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP),
            radius,
        )
    }

    /** Light catching a glass edge: a wide soft halo, a tighter one, and a thin bright line, brightest in the middle. */
    private fun drawRim(canvas: Canvas, path: Path, g: Gradient, d: Float, visible: Float, vertical: Boolean) {
        if (visible <= 0.01f) return
        rim.shader = if (vertical) g.rimVertical else g.rim
        rim.strokeWidth = 22f * d; rim.alpha = (255f * 0.07f * visible).roundToInt(); canvas.drawPath(path, rim)
        rim.strokeWidth = 8f * d; rim.alpha = (255f * 0.16f * visible).roundToInt(); canvas.drawPath(path, rim)
        rim.strokeWidth = 1.5f * d; rim.alpha = (255f * 0.85f * visible).roundToInt(); canvas.drawPath(path, rim)
        rim.shader = null
    }

    /** A thin line that shortens as the pause runs: knowing how long is left is what stops waiting from annoying. */
    private fun drawProgress(canvas: Canvas, w: Float, h: Float, t: Float, d: Float, ink: Int) {
        val layout = words ?: return
        val y = h - maxOf(paddingBottom + 22f * d, 38f * d)
        val right = w - maxOf(paddingRight.toFloat(), layout.margin)
        val length = (right - layout.margin) * (1f - t)
        fill.color = ink; fill.alpha = 110
        canvas.drawRoundRect(layout.margin, y, layout.margin + length, y + 2.5f * d, 2f * d, 2f * d, fill)
        fill.alpha = 255
    }

    private fun drawNeutral(canvas: Canvas, w: Float, h: Float) {
        // A cold launch from a pinned shortcut paints the neutral instruction while saved appearance loads.
        fill.shader = null; fill.color = BreathPalette.BASE
        canvas.drawRect(0f, 0f, w, h, fill)
        publishBars(0)
        drawWords(canvas, w, h, BreathPalette.BASE_INK, 600L, timeline)
    }

    private fun layoutWords(w: Float, h: Float): Words {
        val d = resources.displayMetrics.density
        val scale = resources.configuration.fontScale.coerceIn(1f, 1.6f)
        val inLines = BreathWords.split(saying.inHead)
        val outLines = BreathWords.split(saying.outHead)
        val lines = maxOf(inLines.size, outLines.size)
        val compact = h < w * 1.25f
        val margin = maxOf(paddingLeft.toFloat(), minOf(30f * d, w * 0.08f))
        val avail = w - margin - maxOf(paddingRight.toFloat(), margin)
        // Size by the widest line of EITHER moment, so the type never changes size when the words swap.
        text.typeface = headFace; text.letterSpacing = -0.01f; text.textSize = 100f
        val widest = (inLines + outLines).maxOf { text.measureText(it) }
        val byWidth = avail / widest * 100f
        val block = if (compact) h * 0.56f else h * 0.40f
        val byHeight = block / (lines * 0.92f + 0.55f)
        val size = minOf(byWidth, byHeight, 150f * d * scale)
        val lineHeight = size * 0.92f
        val noteSize = maxOf(13f * d * scale, size * 0.17f)
        val first = (if (compact) 0.46f else 0.41f) * h - (lines - 1) * lineHeight * 0.5f
        return Words(size, noteSize, margin, first, lineHeight, inLines, outLines, saying.inNote, saying.outNote, size * 0.20f)
    }

    private fun drawWords(canvas: Canvas, w: Float, h: Float, ink: Int, elapsed: Long, tl: BreathTimeline) {
        val layout = words ?: layoutWords(w, h).also { words = it }
        val d = resources.displayMetrics.density
        text.color = ink; text.textAlign = Paint.Align.LEFT; text.letterSpacing = -0.01f
        text.typeface = headFace; text.textSize = layout.headSize
        val leaving = tl.leaving(elapsed)
        val showOut = elapsed >= tl.switchMs() + BreathTimeline.OUT_DELAY_MS
        // First moment: arrives, then leaves as the exhale begins. Second moment: arrives after it.
        // A word that is the same in both moments ("BREATHE" in "Breathe in." / "Breathe out.") stays
        // still; only the word that changes leaves and arrives. Otherwise it would ghost as it swapped.
        for ((i, line) in layout.inLines.withIndex()) {
            val stays = layout.outLines.getOrNull(i) == line
            val a = tl.arriving(elapsed, BreathTimeline.Phase.In, i * LINE_STAGGER_MS)
            val rise = if (stays) a.rise else a.rise + leaving.rise
            drawLine(canvas, line, layout.margin, layout.firstBaseline + (i + rise) * layout.lineHeight, if (stays) a.alpha else a.alpha * leaving.alpha)
        }
        if (showOut) for ((i, line) in layout.outLines.withIndex()) {
            if (layout.inLines.getOrNull(i) == line) continue
            val a = tl.arriving(elapsed, BreathTimeline.Phase.Out, i * LINE_STAGGER_MS)
            drawLine(canvas, line, layout.margin, layout.firstBaseline + (i + a.rise) * layout.lineHeight, a.alpha)
        }
        val noteY = layout.firstBaseline + (maxOf(layout.inLines.size, layout.outLines.size) - 1) * layout.lineHeight + layout.noteGap + layout.noteSize
        text.typeface = noteFace; text.textSize = layout.noteSize; text.letterSpacing = 0.14f
        val inNote = tl.arriving(elapsed, BreathTimeline.Phase.In, BreathTimeline.NOTE_DELAY_MS)
        if (layout.inNote.isNotBlank()) drawLine(canvas, layout.inNote.uppercase(), layout.margin, noteY + (inNote.rise + leaving.rise) * layout.lineHeight, inNote.alpha * leaving.alpha * NOTE_ALPHA)
        if (showOut && layout.outNote.isNotBlank()) {
            val outNote = tl.arriving(elapsed, BreathTimeline.Phase.Out, BreathTimeline.NOTE_DELAY_MS)
            drawLine(canvas, layout.outNote.uppercase(), layout.margin, noteY + outNote.rise * layout.lineHeight, outNote.alpha * NOTE_ALPHA)
        }
        if (options.showAppName && destination.isNotBlank()) {
            val available = width - layout.margin - maxOf(paddingRight.toFloat(), layout.margin)
            text.letterSpacing = 0.10f; text.textSize = maxOf(12f * d, layout.noteSize * 0.9f)
            if (nameWidth != available || name.isEmpty()) {
                name = TextUtils.ellipsize(destination.uppercase(), text, available, TextUtils.TruncateAt.END).toString(); nameWidth = available
            }
            drawLine(canvas, name, layout.margin, h - maxOf(paddingBottom + 52f * d, 70f * d), NOTE_ALPHA)
        }
    }

    private fun drawLine(canvas: Canvas, line: String, x: Float, y: Float, alpha: Float) {
        val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
        if (a == 0) return
        text.alpha = a
        canvas.drawText(line, x, y, text)
    }

    private fun publishBars(appearance: Int) {
        if (appearance != lastBarAppearance) { lastBarAppearance = appearance; onSystemBarAppearance?.invoke(appearance) }
    }

    /** One soft tick as the words change to "out", one confirming tick just before the end. */
    private fun cues(t: Float) {
        if (options.haptics) {
            if (lastT < BreathTimeline.EXHALE_AT && t >= BreathTimeline.EXHALE_AT) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (lastT < BreathTimeline.FINISH_CUE_AT && t >= BreathTimeline.FINISH_CUE_AT) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        lastT = t
    }

    /** Next frame with the display; a still layer sleeps until something actually changes. */
    private fun schedule(elapsed: Long, t: Float, reduce: Boolean) {
        if (!isAttachedToWindow || elapsed >= options.durationMs) return
        // Sleep only while the words are settled and nothing is moving; the swap and the exit need every frame.
        val quiet = reduce && !touching && touchLevel == 0f && t < BreathTimeline.EXHALE_AT &&
            elapsed > BreathTimeline.ARRIVE_MS + 3 * LINE_STAGGER_MS
        if (!quiet) { postInvalidateOnAnimation(); return }
        postInvalidateDelayed((timeline.switchMs() - elapsed).coerceAtLeast(1L))
    }

    private companion object {
        const val LINE_STAGGER_MS = 70L
        const val NOTE_ALPHA = 0.78f
    }
}
