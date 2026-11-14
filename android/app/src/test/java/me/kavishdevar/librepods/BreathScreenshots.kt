package me.kavishdevar.librepods

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import me.kavishdevar.librepods.breathing.BreathView
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
    private fun frames(prefix: String, width: Int = 1236, height: Int = 2745) {
        val c = RuntimeEnvironment.getApplication()
        for (time in listOf(0L,100L,300L,600L,900L,1000L)) {
            val view = BreathView(c).apply { destination = "Chess"; renderAt = time }
            view.layout(0,0,width,height)
            val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/screenshots/breathe").mkdirs()
            File("build/screenshots/breathe/$prefix$time.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
}
