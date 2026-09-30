/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 Glint contributors

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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.viewinterop.AndroidView

/**
 * LibrePods' own 3D AirPods clips (res/raw), played muted and looping on a TextureView.
 *
 * A still frame of the same clip sits underneath, so the box is never empty while the decoder
 * warms up, and the video fades in only once its first frame has arrived. With reduce motion
 * on (or where video can't play), only the still shows.
 *
 * [keyBlack] turns the clip's black background transparent on the GPU (used by the island's
 * clip, which was rendered on black), so the AirPods float on the glass instead of in a box.
 */
@Composable
fun PodsVideo(
    @RawRes video: Int,
    @DrawableRes poster: Int,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    loopFromMs: Int = 0,
    keyBlack: Boolean = false,
    play: Boolean = true,
) {
    val context = LocalContext.current
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val still = ImageBitmap.imageResource(poster)
    val playing = play && PodsVideoConfig.enabled && !reduceMotion
    var firstFrame by remember(playing) { mutableStateOf(false) }
    // Cross-fade from the still to the clip, so a transparent (keyed) clip never doubles up.
    val stillAlpha by animateFloatAsState(if (playing && firstFrame) 0f else 1f, tween(140), label = "still")
    Box(modifier.aspectRatio(aspectRatio)) {
        Image(
            still, contentDescription = null, contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = stillAlpha }
        )
        if (playing) {
            val key = remember(keyBlack) { if (keyBlack) blackKeyEffect() else null }
            AndroidView(
                factory = { VideoTexture(it, video, loopFromMs) { firstFrame = true } },
                onRelease = { it.release() },
                modifier = Modifier
                    .matchParentSize()
                    .then(if (key != null) Modifier.graphicsLayer { renderEffect = key } else Modifier)
            )
        }
    }
}

object PodsVideoConfig {
    /** Tests render the still frames only; there's no video decoder under Robolectric. */
    @Volatile var enabled: Boolean = true

    /** Where playback restarts; the connect clip's first frames are a blank fade-in. */
    const val CONNECTED_LOOP_FROM_MS = 200
    const val CONNECTED_ASPECT = 1050f / 354f
    const val ISLAND_ASPECT = 1f
}

// Same curve as the pre-keyed island still (island_poster.png): pixels darker than ~6% become
// clear, brighter than ~20% stay solid, and colour is scaled so it stays valid premultiplied.
private const val BLACK_KEY_AGSL = """
uniform shader content;
half4 main(float2 p) {
    half4 c = content.eval(p);
    half l = max(c.r, max(c.g, c.b));
    half a = smoothstep(0.06, 0.20, l);
    half s = min(1.0, a / max(l, 0.0001));
    return half4(c.rgb * s, a * c.a);
}
"""

private fun blackKeyEffect(): androidx.compose.ui.graphics.RenderEffect? = try {
    RenderEffect.createRuntimeShaderEffect(RuntimeShader(BLACK_KEY_AGSL), "content").asComposeRenderEffect()
} catch (e: Exception) {
    Log.w("PodsVideo", "Black key shader unavailable, showing the clip as is", e)
    null
}

@SuppressLint("ViewConstructor")
private class VideoTexture(
    context: Context,
    @param:RawRes private val video: Int,
    private val loopFromMs: Int,
    private val onFirstFrame: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var startAfterSeek = false
    private var revealed = false

    init {
        isOpaque = false
        alpha = 0f
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        release()
        val s = Surface(texture)
        surface = s
        val mp = MediaPlayer()
        player = mp
        try {
            context.resources.openRawResourceFd(video).use { fd ->
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            mp.setSurface(s)
            mp.setVolume(0f, 0f)
            mp.isLooping = loopFromMs == 0
            mp.setOnPreparedListener { p ->
                if (loopFromMs > 0) {
                    startAfterSeek = true
                    p.seekTo(loopFromMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                } else {
                    p.start()
                }
            }
            mp.setOnSeekCompleteListener { p ->
                if (startAfterSeek && player === p) {
                    startAfterSeek = false
                    p.start()
                }
            }
            mp.setOnCompletionListener { p ->
                // Loop past the clip's blank opening frames instead of flashing them.
                if (player === p) {
                    startAfterSeek = true
                    p.seekTo(loopFromMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w("PodsVideo", "Playback error $what/$extra; keeping the still frame")
                release()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w("PodsVideo", "Couldn't start the AirPods clip; keeping the still frame", e)
            release()
        }
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        if (!revealed) {
            revealed = true
            animate().alpha(1f).setDuration(140).start()
            onFirstFrame()
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    fun release() {
        startAfterSeek = false
        player?.let {
            player = null
            try { it.release() } catch (_: Exception) {}
        }
        surface?.let {
            surface = null
            it.release()
        }
    }
}
