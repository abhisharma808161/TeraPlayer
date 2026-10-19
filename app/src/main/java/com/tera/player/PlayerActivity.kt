package com.tera.player

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONObject

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var status: TextView
    private val tried = HashSet<Int>()
    private var files = listOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        view = PlayerView(this)
        status = TextView(this).apply {
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; text = "Loading..."
            setPadding(48, 48, 48, 48)
        }
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        frame.addView(view, FrameLayout.LayoutParams(mp, mp))
        frame.addView(status, FrameLayout.LayoutParams(mp, mp))
        setContentView(frame)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, frame).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val link = intent.getStringExtra("link") ?: run { finish(); return }
        val ndus = getSharedPreferences("cfg", MODE_PRIVATE).getString("ndus", "") ?: ""
        Thread {
            try {
                val json = Resolver.resolve(link, ndus)
                runOnUiThread { start(json) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Error: " + e.message }
            }
        }.start()
    }

    private fun start(json: JSONObject) {
        val arr = json.getJSONArray("files")
        files = (0 until arr.length()).map { arr.getJSONObject(it) }
        val h = json.optJSONObject("headers")
        val map = HashMap<String, String>()
        h?.keys()?.forEach { k -> if (k != "User-Agent") map[k] = h.getString(k) }
        val dsf = DefaultHttpDataSource.Factory()
            .setUserAgent(h?.optString("User-Agent") ?: "Mozilla/5.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(map)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsf)).build()
        player = p
        view.player = p
        status.text = ""
        status.isClickable = false
        status.visibility = android.view.View.GONE

        p.setMediaItems(files.mapIndexed { i, f -> item(f, f.optString("mp4").isNotEmpty()) })
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val i = p.currentMediaItemIndex
                val f = files.getOrNull(i)
                if (f != null && f.optString("mp4").isNotEmpty() && f.optString("hls").isNotEmpty() && tried.add(i)) {
                    val pos = p.currentPosition
                    p.replaceMediaItem(i, item(f, false))
                    p.seekTo(i, pos)
                    p.prepare(); p.play()
                } else {
                    status.visibility = android.view.View.VISIBLE
                    status.text = "Play nahi hua: " + error.errorCodeName +
                            "\n(cookie expire ho gayi ho sakti hai)"
                }
            }
        })
        p.prepare()
        p.playWhenReady = true
    }

    private fun item(f: JSONObject, mp4: Boolean): MediaItem {
        val b = MediaItem.Builder()
            .setUri(if (mp4) f.getString("mp4") else f.getString("hls"))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(f.optString("name")).build())
        if (!mp4) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        return b.build()
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { super.onDestroy(); player?.release(); player = null }
}
