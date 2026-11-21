package com.example.floatplayer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private var videoUri: Uri? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 16f }
        layout.addView(status)

        fun addButton(label: String, action: () -> Unit) {
            layout.addView(
                Button(this).apply { text = label; setOnClickListener { action() } },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        addButton("1. Pilih video") { pickVideo() }
        addButton("2. Mulai overlay") { startOverlay() }
        addButton("Stop overlay") { stopService(Intent(this, FloatingService::class.java)) }
        setContentView(layout)

        videoUri = getSharedPreferences("prefs", MODE_PRIVATE).getString("uri", null)?.let(Uri::parse)
        updateStatus()

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun updateStatus() {
        status.text = if (videoUri == null) "Belum ada video dipilih" else "Video: $videoUri"
    }

    private fun pickVideo() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, 100)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            videoUri = uri
            getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("uri", uri.toString()).apply()
            updateStatus()
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Aktifkan izin 'Tampil di atas aplikasi lain', lalu kembali", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        val uri = videoUri
        if (uri == null) {
            Toast.makeText(this, "Pilih video dulu", Toast.LENGTH_SHORT).show()
            return
        }
        startForegroundService(
            Intent(this, FloatingService::class.java).putExtra("uri", uri.toString())
        )
        Toast.makeText(this, "Overlay dimulai. Buka TikTok sekarang.", Toast.LENGTH_SHORT).show()
    }
}
