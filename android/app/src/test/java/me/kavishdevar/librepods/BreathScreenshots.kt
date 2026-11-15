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
    @Test fun colorPresetsAndStillFrames() {
        BreathPalette.entries.forEach { palette ->
            frames("${palette.key}-",options=BreathOptions(palette))
        }
        val first=render(0L,BreathOptions(BreathPalette.Lagoon,true),200,400)
        val last=render(999L,BreathOptions(BreathPalette.Lagoon,true),200,400)
        org.junit.Assert.assertTrue("still mode must not animate",first.sameAs(last))
        first.recycle();last.recycle()
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
        for (time in listOf(0L,100L,300L,600L,900L,1000L)) {
            val bitmap=render(time,options,width,height)
            File("build/screenshots/breathe").mkdirs()
            File("build/screenshots/breathe/$prefix$time.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
}
