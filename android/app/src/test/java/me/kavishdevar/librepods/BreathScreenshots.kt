package me.kavishdevar.librepods

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import me.kavishdevar.librepods.breathing.BreathView
import me.kavishdevar.librepods.breathing.BreathOptions
import me.kavishdevar.librepods.breathing.BreathPalette
import me.kavishdevar.librepods.breathing.BreathTransition
import me.kavishdevar.librepods.breathing.BreathWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class BreathScreenshots {
    @Test fun productionTransitionFrames() = frames("")
    @Test fun coldAppearanceDoesNotFlashTheDefaultColorBeforeTheSavedStillColor() {
        val view=BreathView(RuntimeEnvironment.getApplication()).apply { awaitingAppearance=true;renderAt=0 }
        view.layout(0,0,200,400)
        val bitmap=Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        org.junit.Assert.assertEquals(BreathPalette.BASE,bitmap.getPixel(5,395))
        view.options=BreathOptions(BreathPalette.Coral,true)
        view.draw(Canvas(bitmap))
        val loaded=bitmap.getPixel(5,395)
        org.junit.Assert.assertTrue(android.graphics.Color.red(loaded)>android.graphics.Color.blue(loaded))
        bitmap.recycle()
    }
    @Test fun colorPresetsAndStillFrames() {
        BreathPalette.entries.forEach { palette ->
            frames("${palette.key}-",options=BreathOptions(palette))
        }
        BreathPalette.entries.forEach { palette ->
            // A column left of the text margin: in still mode nothing on it may move before the layer clears.
            val first=render(0L,BreathOptions(palette,true),200,400)
            val last=render(999L,BreathOptions(palette,true),200,400)
            for (y in 0 until 400) org.junit.Assert.assertEquals("${palette.label} still mode must not animate (row $y)",first.getPixel(5,y),last.getPixel(5,y))
            first.recycle();last.recycle()
        }
    }
    @Test fun layerAdvancesAcrossTheScreen() {
        fun colored(time:Long):Int {
            val bitmap=render(time,BreathOptions(),200,400)
            var count=0
            for (y in 0 until 400) {
                val p=bitmap.getPixel(10,y)
                if (android.graphics.Color.blue(p)>80) count++
            }
            bitmap.recycle();return count
        }
        org.junit.Assert.assertTrue(colored(100)<colored(300))
        org.junit.Assert.assertTrue(colored(300)<colored(600))
        org.junit.Assert.assertTrue(colored(600)<colored(900))
    }
    @Test @Config(qualifiers = "w412dp-h915dp-night-xxhdpi")
    fun darkTransitionFrames() = frames("dark-")
    @Test fun landscapeAndLargeText() {
        RuntimeEnvironment.setFontScale(1.8f)
        try { frames("landscape-", 2745, 1236) } finally { RuntimeEnvironment.setFontScale(1f) }
    }
    @Test fun viewDoesNotClearTheSurroundingScreen() {
        val c=RuntimeEnvironment.getApplication()
        val view=BreathView(c).apply { renderAt=300 }
        view.layout(0,0,200,300)
        val bitmap=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.RED)
        canvas.translate(50f,100f)
        view.draw(canvas)
        org.junit.Assert.assertEquals(android.graphics.Color.RED,bitmap.getPixel(0,0))
        org.junit.Assert.assertEquals(android.graphics.Color.RED,bitmap.getPixel(500,800))
        bitmap.recycle()
    }
    private fun render(time:Long,options:BreathOptions,width:Int,height:Int):Bitmap {
        val view=BreathView(RuntimeEnvironment.getApplication()).apply { destination="Chess";renderAt=time;this.options=options }
        view.layout(0,0,width,height)
        return Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    private fun frames(prefix: String, width: Int = 1236, height: Int = 2745,options:BreathOptions=BreathOptions()) {
        for (time in listOf(0L,120L,400L,900L,1500L,1700L,2100L,2500L,2900L)) {
            val bitmap=render(time,options,width,height)
            File("build/screenshots/breathe").mkdirs()
            File("build/screenshots/breathe/$prefix$time.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }

    // ---- Guards against flicker: the things that made the old layer feel unstable. ----
    private fun alphaAt(time:Long,options:BreathOptions,x:Int=195,y:Int=5):Int {
        val bitmap=render(time,options,200,400)
        return android.graphics.Color.alpha(bitmap.getPixel(x,y)).also { bitmap.recycle() }
    }
    @Test fun theFirstFrameCoversEverythingInTheSameHueAsTheGradient() {
        BreathPalette.entries.forEach { palette ->
            val bitmap=render(0L,BreathOptions(palette),200,400)
            for (x in listOf(0,50,100,150,199)) for (y in listOf(0,100,200,300,399))
                org.junit.Assert.assertEquals("${palette.label} first frame must be opaque at $x,$y",255,android.graphics.Color.alpha(bitmap.getPixel(x,y)))
            val corner=bitmap.getPixel(195,5)
            val deep=palette.deep
            fun near(a:Int,b:Int)=kotlin.math.abs(a-b)<=2
            org.junit.Assert.assertTrue("${palette.label} base is its own deep tone",
                near(android.graphics.Color.red(corner),android.graphics.Color.red(deep)) &&
                near(android.graphics.Color.green(corner),android.graphics.Color.green(deep)) &&
                near(android.graphics.Color.blue(corner),android.graphics.Color.blue(deep)))
            bitmap.recycle()
        }
    }
    @Test fun fadeStaysSolidThenClearsSmoothlyAndEndsInvisibleNeverBlinking() {
        val options=BreathOptions(transition=BreathTransition.Fade)
        val alphas=(0..3000 step 100).map { alphaAt(it.toLong(),options) }
        org.junit.Assert.assertTrue("solid through the inhale",(0..1500 step 100).all { alphaAt(it.toLong(),options)==255 })
        for (i in 1 until alphas.size) org.junit.Assert.assertTrue("opacity never rises again (frame $i: ${alphas[i-1]} -> ${alphas[i]})",alphas[i]<=alphas[i-1])
        org.junit.Assert.assertEquals("gone exactly when the run ends",0,alphas.last())
        org.junit.Assert.assertTrue("a smooth ramp, not a cliff: biggest single step is under 45% of full",
            alphas.zipWithNext { a,b -> a-b }.max()<115)
    }
    @Test fun liftRevealsTheAppFromTheBottomUp() {
        val options=BreathOptions(transition=BreathTransition.Lift)
        val bitmap=render(2250L,options,200,400)
        org.junit.Assert.assertEquals("top is still covered",255,android.graphics.Color.alpha(bitmap.getPixel(100,5)))
        org.junit.Assert.assertEquals("bottom already shows the app",0,android.graphics.Color.alpha(bitmap.getPixel(100,395)))
        bitmap.recycle()
        org.junit.Assert.assertEquals("fully gone at the end",0,alphaAt(3000L,options,100,5))
    }
    @Test fun instantStaysSolidUntilTheVeryEnd() {
        val options=BreathOptions(transition=BreathTransition.Instant)
        for (t in listOf(0L,1500L,2500L,2990L)) org.junit.Assert.assertEquals("Instant at $t",255,alphaAt(t,options))
    }
    @Test fun theChosenLengthScalesTheWholeShapeAndAppNameCanBeShown() {
        val five=BreathOptions(durationMs=5_000)
        org.junit.Assert.assertEquals(255,alphaAt(2500L,five))
        org.junit.Assert.assertEquals(0,alphaAt(5000L,five))
        val named=render(1000L,BreathOptions(showAppName=true),400,800)
        val plain=render(1000L,BreathOptions(showAppName=false),400,800)
        org.junit.Assert.assertFalse("app name drawn only when asked",named.sameAs(plain))
        named.recycle();plain.recycle()
    }
    @Test fun everySayingAndBothMomentsAndTransitionRender() {
        for (index in BreathWords.sayings.indices) for ((moment,time) in listOf("in" to 900L,"out" to 1750L)) {
            val view=BreathView(RuntimeEnvironment.getApplication()).apply { sayingIndex=index;renderAt=time;options=BreathOptions(durationMs=3000) }
            view.layout(0,0,1236,2745)
            val bitmap=Bitmap.createBitmap(1236,2745,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            File("build/screenshots/breathe").mkdirs()
            File("build/screenshots/breathe/saying$index-$moment.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        for (transition in BreathTransition.entries) frames("${transition.key}-",options=BreathOptions(transition=transition))
    }
}
