package org.aionlink.open

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the car dashboard shows; kept in memory only and never exported. */
data class CarInfo(
    val mediaTitle: String? = null, val mediaArtist: String? = null, val mediaPlaying: Boolean = false,
    val mediaApp: String? = null, val navTitle: String? = null, val navText: String? = null, val navIcon: Bitmap? = null
)

/**
 * Notification access granted by the owner lets the car dashboard read the active media session (title, artist,
 * play/pause/skip) and the ongoing navigation notification of Maps or Waze. This is the public Android route for a
 * companion display; no screen content is captured. Nothing read here is logged or exported.
 */
class CarInfoListener : NotificationListenerService() {
    companion object {
        private val NAVIGATION = setOf("com.google.android.apps.maps", "com.waze", "com.google.android.apps.mapslite")
        private val mutableInfo = MutableStateFlow(CarInfo())
        val info = mutableInfo.asStateFlow()
        @Volatile private var controller: MediaController? = null
        @Volatile private var instance: CarInfoListener? = null
        val connected get() = instance != null

        fun granted(context: Context): Boolean =
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.contains(ComponentName(context, CarInfoListener::class.java).flattenToString()) == true

        fun playPause() = controller?.let { c ->
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        }
        fun next() = controller?.transportControls?.skipToNext()
        fun previous() = controller?.transportControls?.skipToPrevious()
    }

    private val main = Handler(Looper.getMainLooper())
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onSessionDestroyed() = refreshSessions()
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { refreshSessions() }

    override fun onListenerConnected() {
        instance = this
        try {
            getSystemService(MediaSessionManager::class.java)
                .addOnActiveSessionsChangedListener(sessionsChanged, ComponentName(this, CarInfoListener::class.java), main)
        } catch (_: SecurityException) { }
        refreshSessions()
        try { activeNotifications?.forEach(::onNotificationPosted) } catch (_: SecurityException) { }
    }

    override fun onListenerDisconnected() {
        try { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsChanged) } catch (_: Exception) { }
        controller?.unregisterCallback(callback); controller = null
        if (instance === this) instance = null
    }

    private fun refreshSessions() {
        val sessions = try {
            getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(this, CarInfoListener::class.java))
        } catch (_: SecurityException) { emptyList() }
        val chosen = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback, main)
        }
        publishMedia()
    }

    private fun publishMedia() {
        val c = controller
        val metadata = c?.metadata
        val label = c?.packageName?.let { name ->
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(name, 0)).toString() } catch (_: Exception) { null }
        }
        mutableInfo.value = mutableInfo.value.copy(
            mediaTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            mediaArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            mediaPlaying = c?.playbackState?.state == PlaybackState.STATE_PLAYING,
            mediaApp = label)
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        if (notification.packageName !in NAVIGATION) return
        val extras = notification.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (title == null && text == null) return
        val icon = try {
            (notification.notification.getLargeIcon()?.loadDrawable(this) as? BitmapDrawable)?.bitmap
        } catch (_: Exception) { null }
        mutableInfo.value = mutableInfo.value.copy(navTitle = title, navText = text, navIcon = icon)
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {
        if (notification.packageName in NAVIGATION && notification.isOngoing)
            mutableInfo.value = mutableInfo.value.copy(navTitle = null, navText = null, navIcon = null)
    }
}
