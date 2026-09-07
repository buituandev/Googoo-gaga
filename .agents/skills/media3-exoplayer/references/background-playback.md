# Background Playback with MediaSession and MediaSessionService (Best Practice)

For playback that continues when the app is backgrounded or screen is off (especially audio, but also video in some cases), wrap the player in a `MediaSessionService` (foreground service). This is the recommended architecture in Media3.

## 1. Add Dependencies
Already covered in getting-started.md — include `androidx.media3:media3-session:$media3Version`

## 2. Create PlaybackService (Kotlin)

```kotlin
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.exoplayer.ExoPlayer
import android.content.Intent
import androidx.media3.common.util.UnstableApi

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
        
        // Optional: Set a custom MediaNotification.Provider for customized notifications
        // setMediaNotificationProvider(MyCustomNotificationProvider())
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // Optional: Handle task removal (user swipes app from recent apps)
    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }
}
```

Java equivalent similar (see official docs for exact).

## 3. AndroidManifest.xml

```xml
<manifest ...>
    <!-- Required permissions -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <!-- For internet media -->
    <uses-permission android:name="android.permission.INTERNET" />
    <!-- Optional for wake lock if using setWakeMode -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application ...>
        <service
            android:name=".PlaybackService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
                <!-- For legacy MediaBrowser compatibility -->
                <action android:name="android.media.browse.MediaBrowserService" />
            </intent-filter>
        </service>

        <!-- Optional but recommended for media button handling -->
        <receiver
            android:name="androidx.media3.session.MediaButtonReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MEDIA_BUTTON" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

## 4. Connect UI (Activity) to the Service using MediaController

In your Activity/Fragment (e.g. onStart):

```kotlin
private var mediaController: MediaController? = null
private lateinit var controllerFuture: ListenableFuture<MediaController>

override fun onStart() {
    super.onStart()
    val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
    controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
    
    controllerFuture.addListener({
        try {
            mediaController = controllerFuture.get()
            // Bind to your PlayerView or custom UI
            playerView.setPlayer(mediaController)
            
            // Now use mediaController instead of direct player for commands
            // e.g. mediaController?.setMediaItem(...)
        } catch (e: Exception) {
            // Handle connection failure
        }
    }, MoreExecutors.directExecutor())
}

override fun onStop() {
    super.onStop()
    // Release the controller future
    MediaController.releaseFuture(controllerFuture)
    mediaController = null
    // Do NOT release the player here — it's in the service
}
```

Then, to start playback from UI:

```kotlin
val mediaItem = MediaItem.fromUri(uri)  // or with full metadata
mediaController?.setMediaItem(mediaItem)
mediaController?.prepare()
mediaController?.play()
```

The MediaSession automatically syncs state, metadata, and commands between the service player and connected controllers (including system notification, Bluetooth, Android Auto, etc.).

## 5. Important Best Practices & Notes

- **Foreground Service Type**: Must declare `android:foregroundServiceType="mediaPlayback"` (Android 10+ / API 29+). Required for media playback services.
- **Notification**: Automatically shown by Media3 when player has content. Uses MediaStyle. Customize title/artist/artwork via `MediaItem.setMediaMetadata(...)`. To hide/stop: clearMediaItems() or release player.
- **Auto-stop**: If paused/stopped/failed > ~10 min without user interaction, service leaves foreground and can be killed.
- **Playback Resumption** (good UX): Implement `MediaSession.Callback.onPlaybackResumption(...)` to restore last playlist/position after service restart or boot. Store state in SharedPrefs or DB.
- **Custom Commands**: Override `MediaSession.Callback` methods like `onConnect`, `onCustomCommand`, `onAddMediaItems` for advanced control (e.g., login-required content).
- **Trusted Controllers**: Use `controllerInfo.isTrusted()` to decide what capabilities to expose.
- **Legacy Compatibility**: Media3 sessions work with old `MediaControllerCompat` and platform sessions.
- **onTaskRemoved**: Call `pauseAllPlayersAndStopSelf()` to clean up when user removes from recents.
- **Multiple players**: MediaSessionService supports multiple sessions/players if needed (advanced).
- **Testing**: Test with notification shade, lock screen, Bluetooth devices, Android Auto if applicable.

## 6. When NOT to use Service
- Pure foreground video players (e.g. in-app only video) — simple Activity + release onStop is often enough.
- Short clips or one-shot playback.

For most music/podcast/video apps that users expect to continue in background, **MediaSessionService is the best practice**.

Official reference: https://developer.android.com/media/media3/session/background-playback
