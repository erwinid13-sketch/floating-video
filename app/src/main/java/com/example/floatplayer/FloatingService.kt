package com.example.floatplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

class FloatingService : Service() {

    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private var player: ExoPlayer? = null
    private var params: WindowManager.LayoutParams? = null
    private var aspect = 16f / 9f
    private var muted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        val uri = intent?.getStringExtra("uri")?.let(Uri::parse)
        if (uri == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        removeOverlay()
        showOverlay(uri)
        return START_NOT_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("float", "Floating Video", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "float")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Floating Video berjalan")
            .setContentText("Video diputar berulang di atas aplikasi lain")
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, n)
        }
    }

    private fun showOverlay(uri: Uri) {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = windowManager

        val exo = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        aspect = videoSize.width.toFloat() / videoSize.height
                        val p = params ?: return
                        p.height = (p.width / aspect).toInt()
                        root?.let { windowManager.updateViewLayout(it, p) }
                    }
                }
            })
            prepare()
            playWhenReady = true
        }
        player = exo

        val lp = WindowManager.LayoutParams(
            dp(240), (dp(240) * 9) / 16,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(100)
        }
        params = lp

        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val surface = SurfaceView(this)
        exo.setVideoSurfaceView(surface)
        frame.addView(
            surface,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        val dragListener = View.OnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchX).toInt()
                    lp.y = startY + (e.rawY - touchY).toInt()
                    windowManager.updateViewLayout(frame, lp)
                    true
                }
                else -> false
            }
        }
        frame.setOnTouchListener(dragListener)
        surface.setOnTouchListener(dragListener)

        fun chip(label: String, action: (TextView) -> Unit): TextView =
            TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(150, 0, 0, 0))
                setOnClickListener { action(this) }
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { leftMargin = dp(2) }
            }

        fun resize(factor: Float) {
            val w = (lp.width * factor).toInt().coerceIn(dp(120), dp(420))
            lp.width = w
            lp.height = (w / aspect).toInt()
            windowManager.updateViewLayout(frame, lp)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(chip("−") { resize(0.85f) })
            addView(chip("+") { resize(1.18f) })
            addView(chip("🔊") { t ->
                muted = !muted
                exo.volume = if (muted) 0f else 1f
                t.text = if (muted) "🔇" else "🔊"
            })
            addView(chip("✕") { stopSelf() })
        }
        frame.addView(
            bar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        root = frame
        windowManager.addView(frame, lp)
    }

    private fun removeOverlay() {
        root?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        root = null
        player?.release()
        player = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}
