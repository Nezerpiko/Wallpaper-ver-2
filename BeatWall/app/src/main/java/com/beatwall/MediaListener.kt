package com.beatwall

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.palette.graphics.Palette

/** Shared "now playing" state read by the wallpaper. */
object NowPlaying {
    @Volatile var title = ""
    @Volatile var artist = ""
    @Volatile var playing = false
    @Volatile var c1 = 0xFF1DB954.toInt()
    @Volatile var c2 = 0xFF7B2FF7.toInt()
}

/** Needs "Notification access" so Android lets us read Spotify's media session. */
class MediaListener : NotificationListenerService() {
    private var msm: MediaSessionManager? = null
    private var ctrl: MediaController? = null

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(m: MediaMetadata?) = update(m)
        override fun onPlaybackStateChanged(s: PlaybackState?) { NowPlaying.playing = s?.state == PlaybackState.STATE_PLAYING }
    }
    private val sessionsCb = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    override fun onListenerConnected() {
        val cn = ComponentName(this, MediaListener::class.java)
        msm = getSystemService(MediaSessionManager::class.java).also {
            it.addOnActiveSessionsChangedListener(sessionsCb, cn)
            pick(it.getActiveSessions(cn))
        }
    }

    override fun onListenerDisconnected() {
        msm?.removeOnActiveSessionsChangedListener(sessionsCb)
        ctrl?.unregisterCallback(cb)
    }

    private fun pick(list: List<MediaController>?) {
        ctrl?.unregisterCallback(cb)
        ctrl = list?.firstOrNull { it.packageName == "com.spotify.music" } ?: list?.firstOrNull()
        val c = ctrl
        if (c == null) { NowPlaying.playing = false; return }
        c.registerCallback(cb)
        update(c.metadata)
        NowPlaying.playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    private fun update(m: MediaMetadata?) {
        NowPlaying.title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        NowPlaying.artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (art != null) Palette.from(art).generate { p ->
            if (p == null) return@generate
            NowPlaying.c1 = p.getVibrantColor(p.getLightVibrantColor(0xFF1DB954.toInt()))
            NowPlaying.c2 = p.getDarkVibrantColor(p.getMutedColor(0xFF7B2FF7.toInt()))
        }
    }
}
