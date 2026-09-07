# Lifecycle Best Practices for Media3 ExoPlayer

Proper lifecycle management is critical to avoid resource leaks, crashes, and poor UX (e.g., playback stopping on rotation or backgrounding).

## Basic In-Activity Playback (Foreground Only)

**Recommended pattern** (API 24+ / Nougat and above):

In your Activity or Fragment:

```kotlin
class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player_view)
        
        // Create player early but do NOT prepare yet
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
    }

    override fun onStart() {
        super.onStart()
        player?.let {
            // Prepare and set media here or lazily when user starts playback
            if (it.mediaItemCount == 0) {
                it.setMediaItem(createMediaItem())
            }
            it.prepare()
            it.play()
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null  // or keep reference if using ViewModel
    }

    // For API < 24, use onResume / onPause instead of onStart/onStop for prepare/release
}
```

**Key Rules:**
- **Do NOT prepare() the player before the Activity is in the foreground** (i.e., before onStart/onResume). This prevents unnecessary resource allocation and potential ANRs or battery drain.
- Release in `onStop()` (API 24+) or `onPause()` (lower) to free resources when user leaves the screen.
- For configuration changes (rotation): Either retain player in `ViewModel` (but ViewModel survives config change, Activity doesn't), or handle `savedInstanceState` for position, or better — use a retained Fragment or Service for long playback.
- Many modern apps keep the player alive across config changes by using a `ViewModel` that holds the ExoPlayer instance (created with application context).

## Using ViewModel for Player Retention (Recommended for robust apps)

```kotlin
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val player: ExoPlayer = ExoPlayer.Builder(application).build()
    
    // Expose methods or LiveData for UI state if needed
    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
```

Then in Activity:
```kotlin
private val viewModel: PlayerViewModel by viewModels()
...
playerView.player = viewModel.player
```

This way player survives config changes. Manage prepare/play in onStart, but release only when ViewModel is cleared (app process death or explicit).

## Error Handling
Implement `Player.Listener`:

```kotlin
player.addListener(object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
        // Log, show toast/UI error, fallback to next item, etc.
        Log.e("Player", "Playback error", error)
        // error.errorCode, error.cause, etc.
    }
    
    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> ...
            Player.STATE_READY -> ...
            Player.STATE_ENDED -> ...
        }
    }
})
```

## Track Selection Best Practices
```kotlin
val trackSelector = DefaultTrackSelector(this).apply {
    setParameters(
        buildUponParameters()
            .setPreferredAudioLanguage("en")
            .setPreferredVideoLanguage("en")
            .setMaxVideoSizeSd()  // or custom constraints
            .setAllowVideoMixedMimeTypeAdaptiveness(true)
            // etc.
    )
}

val player = ExoPlayer.Builder(context)
    .setTrackSelector(trackSelector)
    .build()
```

Listen to `onTracksChanged` or use `player.currentTracks`.

For dynamic changes: `player.trackSelectionParameters = ...`

## Other Tips
- Use `player.setWakeMode(C.WAKE_MODE_LOCAL)` or higher for audio focus / screen on (or rely on PlayerView).
- For audio-only: Consider `setAudioAttributes` on Builder for focus handling.
- Always handle `onPlayerError` and `onPlayerErrorChanged`.
- For live streams / DASH/HLS: Use appropriate MediaSource.Factory if customizing.
- Test on low-end devices for buffering (use DefaultLoadControl customizations if needed).

See official docs and demo for full patterns.
