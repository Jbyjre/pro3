package me.kavishdevar.librepods

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import me.kavishdevar.librepods.breathing.BreathView
import me.kavishdevar.librepods.breathing.BreathOptions
import me.kavishdevar.librepods.breathing.BreathPalette
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
            val first=render(0L,BreathOptions(palette,true),200,400)
            val last=render(999L,BreathOptions(palette,true),200,400)
            org.junit.Assert.assertTrue("${palette.label} still mode must not animate",first.sameAs(last))
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
        for (time in listOf(0L,250L,750L,1125L,1250L,1500L,2250L,2500L)) {
            val bitmap=render(time,options,width,height)
            File("build/screenshots/breathe").mkdirs()
            File("build/screenshots/breathe/$prefix$time.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
}
