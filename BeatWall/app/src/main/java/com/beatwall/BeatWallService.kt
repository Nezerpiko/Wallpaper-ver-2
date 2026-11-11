package com.beatwall

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.graphics.ColorUtils
import kotlin.math.*

private const val BANDS = 48   // mirrored into 96 bars

class BeatWallService : WallpaperService() {
    override fun onCreateEngine(): Engine = E()

    inner class E : Engine() {
        private val h = Handler(Looper.getMainLooper())
        private var viz: Visualizer? = null
        @Volatile private var raw = FloatArray(BANDS)
        private val band = FloatArray(BANDS)
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private var t = 0f
        private var cc1 = NowPlaying.c1
        private var cc2 = NowPlaying.c2
        private var run = false
        private val tick = object : Runnable {
            override fun run() { draw(); if (run) h.postDelayed(this, 16) }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            run = visible
            if (visible) { startViz(); h.post(tick) } else { h.removeCallbacks(tick); stopViz() }
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) { run = false; h.removeCallbacks(tick); stopViz() }

        private fun startViz() {
            if (viz != null) return
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            try {
                val v = Visualizer(0)
                v.setEnabled(false)
                v.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
                v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(x: Visualizer?, d: ByteArray?, r: Int) {}
                    override fun onFftDataCapture(x: Visualizer?, d: ByteArray?, r: Int) { if (d != null) process(d) }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                v.setEnabled(true)
                viz = v
            } catch (e: Exception) { viz = null }
        }
        private fun stopViz() { try { viz?.setEnabled(false); viz?.release() } catch (_: Exception) {}; viz = null }

        private fun process(d: ByteArray) {
            val n = d.size / 2
            val out = FloatArray(BANDS)
            for (b in 0 until BANDS) {
                val s = max(1, n.toDouble().pow(b.toDouble() / BANDS).toInt())
                val e = min(n, max(s + 1, n.toDouble().pow((b + 1.0) / BANDS).toInt()))
                var sum = 0f
                for (k in s until e) sum += hypot(d[2 * k].toFloat(), d[2 * k + 1].toFloat())
                out[b] = min(1f, ln(1f + sum / (e - s)) / 5.2f * (0.8f + b * 0.012f))
            }
            raw = out
        }

        private fun draw() {
            val c = surfaceHolder.lockCanvas() ?: return
            try { render(c) } finally { surfaceHolder.unlockCanvasAndPost(c) }
        }

        private fun render(c: Canvas) {
            val w = c.width.toFloat(); val hh = c.height.toFloat(); val u = w / 1080f
            t += 0.016f
            val playing = NowPlaying.playing
            cc1 = ColorUtils.blendARGB(cc1, NowPlaying.c1, 0.04f)
            cc2 = ColorUtils.blendARGB(cc2, NowPlaying.c2, 0.04f)
            val r = raw
            for (i in 0 until BANDS) {
                val target = when {
                    !playing -> 0.08f + 0.05f * sin(t * 1.2f + i * 0.3f)
                    viz == null -> (0.3f + 0.25f * abs(sin(t * 3.1f + i * 0.5f))) * (0.7f + 0.3f * sin(t * 7f))
                    else -> r[i]
                }
                val k = if (target > band[i]) 0.6f else 0.12f
                band[i] += (target - band[i]) * k
            }
            val bass = (0 until 6).map { band[it] }.average().toFloat()

            p.shader = LinearGradient(0f, 0f, 0f, hh,
                intArrayOf(ColorUtils.blendARGB(0xFF05050A.toInt(), cc2, 0.35f + 0.1f * bass), 0xFF05050A.toInt()),
                null, Shader.TileMode.CLAMP)
            p.style = Paint.Style.FILL
            c.drawRect(0f, 0f, w, hh, p)
            p.shader = null

            val cx = w / 2f; val cy = hh * 0.66f; val r0 = (250f + 20f * bass) * u
            val bars = BANDS * 2
            for (i in 0 until bars) {
                val b = if (i < BANDS) i else bars - 1 - i
                val a = (2 * PI * i / bars - PI / 2 + t * 0.15).toFloat()
                val len = (30f + band[b] * 260f) * u
                val col = ColorUtils.blendARGB(cc1, cc2, b / BANDS.toFloat())
                val x1 = cx + r0 * cos(a); val y1 = cy + r0 * sin(a)
                val x2 = cx + (r0 + len) * cos(a); val y2 = cy + (r0 + len) * sin(a)
                p.style = Paint.Style.STROKE
                p.color = ColorUtils.setAlphaComponent(col, 70); p.strokeWidth = 16f * u
                c.drawLine(x1, y1, x2, y2, p)
                p.color = col; p.strokeWidth = 7f * u
                c.drawLine(x1, y1, x2, y2, p)
            }
            p.style = Paint.Style.FILL; p.color = ColorUtils.setAlphaComponent(cc1, 60)
            c.drawCircle(cx, cy, (r0 - 20f * u), p)
            p.color = cc1; c.drawCircle(cx, cy, (80f + 50f * bass) * u, p)

            for (i in 0 until BANDS) {
                val x = (i + 0.5f) * w / BANDS
                val len = (20f + band[i] * 150f) * u
                p.style = Paint.Style.STROKE; p.strokeWidth = 14f * u
                p.color = ColorUtils.blendARGB(cc1, cc2, i / BANDS.toFloat())
                c.drawLine(x, hh - 24f * u, x, hh - 24f * u - len, p)
            }

            if (NowPlaying.title.isNotBlank()) {
                p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.color = Color.WHITE
                p.textSize = 46f * u; c.drawText(NowPlaying.title.take(32), cx, cy + 560f * u, p)
                p.color = 0xB3FFFFFF.toInt(); p.textSize = 34f * u
                c.drawText(NowPlaying.artist.take(40), cx, cy + 610f * u, p)
            }
        }
    }
}
