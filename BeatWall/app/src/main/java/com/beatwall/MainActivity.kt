package com.beatwall

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xFF08060F.toInt()); setPadding(64, 64, 64, 64)
        }
        fun add(text: String, onClick: () -> Unit) = root.addView(Button(this).apply {
            this.text = text; setOnClickListener { onClick() }
        })
        root.addView(TextView(this).apply {
            text = "BeatWall"; textSize = 34f; setTextColor(0xFF1DB954.toInt()); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Do the 3 steps in order"; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 16, 0, 32)
        })
        add("1. Allow audio (powers the visualizer)") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        add("2. Allow notification access (Spotify info + colors)") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        add("3. Set live wallpaper") {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, BeatWallService::class.java)))
        }
        setContentView(root)
    }
}
