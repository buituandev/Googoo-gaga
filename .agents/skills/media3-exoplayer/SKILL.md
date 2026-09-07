---
name: media3-exoplayer
description: Provide expert guidance on implementing Android media playback with Jetpack Media3 ExoPlayer latest stable version (1.10.1 as of mid-2026). Covers dependency setup, ExoPlayer creation and configuration, MediaItem and playlist handling, UI integration (PlayerView/Compose), lifecycle management, error handling, track selection, and especially background playback using MediaSessionService. Use for any query involving ExoPlayer implementation, Media3 migration, best practices for robust media apps, or code samples for player features.
---

# Jetpack Media3 ExoPlayer Skill

This skill encodes current best practices (July 2026) distilled from official Android developer documentation for the latest stable Media3 1.10.1. Always cross-check the [official releases page](https://developer.android.com/jetpack/androidx/releases/media3) for updates, as 1.11.x betas exist with new features like improved track selection priority.

**Core Principle:** Use `ExoPlayer` (implementation of `Player` interface) + `MediaSession` for modern, system-integrated playback. Prefer `MediaSessionService` for any app that needs background or notification controls.

## 1. Dependency Setup (Always Use Matching Versions)

See `references/getting-started.md` for full details and recommended modules (exoplayer, exoplayer-dash/hls, ui, ui-compose-material3, session, common).

**Rule:** All `androidx.media3:*` artifacts **must** use the exact same version string (e.g. "1.10.1"). Mixing versions causes runtime issues.

Enable Java 8 compatibility in `build.gradle`.

Add `INTERNET` permission for remote streams. Add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + `WAKE_LOCK` (optional) when using background playback.

## 2. Basic Player Creation and Playback

Use `ExoPlayer.Builder(context).build()`.

Key configuration points on Builder:
- `.setTrackSelector(DefaultTrackSelector(context))` for custom track prefs (language, quality, etc.)
- `.setLoadControl(...)` to tune buffering
- `.setAudioAttributes(...)` for focus handling
- `.setWakeMode(C.WAKE_MODE_LOCAL)` etc.

**Never access player from background threads** — it is bound to the Looper of the thread it was created on (recommend main thread).

Prepare with `MediaItem` (supports URI, progressive, DASH, HLS, SmoothStreaming, RTSP, etc. out of box with correct modules).

Full step-by-step and code: **read references/getting-started.md**

## 3. Lifecycle Management (Critical — Follow Strictly)

Incorrect lifecycle is the #1 source of bugs and leaks with media players.

**Best Practice Summary:**
- Create player in `onCreate()` (or ViewModel with app context for retention across config changes).
- **Prepare and start playback only after the UI is visible** (onStart for API 24+, onResume for older).
- **Release the player in onStop()** (API 24+) / onPause() to free decoders promptly.
- For long-running playback (music apps): Move player + MediaSession into a `MediaSessionService` (see below). UI connects via `MediaController`.
- Use `ViewModel` to hold player instance if you want playback to survive configuration changes without a Service.

Detailed patterns, ViewModel example, and warnings: **read references/lifecycle-best-practices.md**

**Golden Rule:** Do not prepare() the player while the app is in the background.

## 4. UI Integration

**Classic XML:**
```xml
<androidx.media3.ui.PlayerView
    android:id="@+id/player_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:use_controller="true"
    app:keep_screen_on="true" />
```
Then `playerView.player = player`

PlayerView handles surface, subtitles, controls automatically.

**Jetpack Compose (recommended for new UIs):**
Use artifacts from `media3-ui-compose-material3`. Components like `PlayerSurface`, Material3 playback controls, etc.

See official UI guide for customization (overriding PlayerControlView layout, themed controls, etc.).

## 5. Background Playback & System Integration (Strongly Recommended)

For any non-trivial media app:

1. Create `PlaybackService : MediaSessionService`
2. Initialize `ExoPlayer` + `MediaSession` inside the service's `onCreate()`.
3. Declare service in manifest with `foregroundServiceType="mediaPlayback"`.
4. From Activity/Fragment, connect using `MediaController.Builder(...).buildAsync()` and bind the controller to your PlayerView.
5. Control playback **through the MediaController** (not direct player reference from UI).
6. The service automatically publishes a MediaStyle notification with artwork/title from `MediaItem.mediaMetadata`.

**Full production-ready implementation, manifest, onTaskRemoved handling, resumption, custom notifications, and controller connection code:** **read references/background-playback.md**

This architecture gives you:
- Playback continues when app is backgrounded or screen off.
- Lock screen / notification / Bluetooth / Android Auto controls work out of the box.
- Proper foreground service lifecycle.

## 6. Error Handling, Listeners & Analytics

Always attach a `Player.Listener`:

```kotlin
player.addListener(object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) { ... }
    override fun onPlaybackStateChanged(state: Int) { ... }
    override fun onIsPlayingChanged(isPlaying: Boolean) { ... }
    override fun onTracksChanged(tracks: Tracks) { ... }
    // Many more useful callbacks
})
```

Use `AnalyticsListener` for detailed metrics (bandwidth, decoder info, etc.).

Handle `PlaybackException` gracefully (network, decoder, source errors have specific `errorCode`).

## 7. Track Selection & Advanced Configuration

- Prefer `DefaultTrackSelector` + `TrackSelectionParameters` (set on player or builder).
- Common: preferred languages, max video bitrate/size, audio channel count, renderer disabling.
- Listen to `Player.Listener.onTrackSelectionParametersChanged` and `onTracksChanged`.
- For dynamic quality switching or ABR: ExoPlayer handles it; customize via `TrackSelectionParameters.Builder`.

See `DefaultTrackSelector` Javadoc and Media3 track selection docs for full API.

## 8. Other Common Patterns & Best Practices

- **Playlists**: Use `addMediaItem`, `setMediaItems(list)`, `removeMediaItem`. Playlists are mutable live. Use `MediaItem` for clipping (`ClippingConfiguration`), start position, etc.
- **Metadata & Artwork**: Always populate `MediaItem.Builder().setMediaMetadata(...)` — used by notification, lockscreen, Cast, etc.
- **Preloading / Prewarming** (advanced, Media3 1.6+): Use `DefaultPreloadManager` or experimental renderer prewarming for faster item transitions. See release notes and dedicated blogs.
- **Offline / Downloads**: Use `DownloadManager` + `DownloadService` (separate modules/patterns).
- **Custom Renderers / Effects**: Extend `DefaultRenderersFactory`, use `ExoPlayer.setVideoEffects(...)` or Transformer for editing.
- **Testing**: Use `TestExoPlayerBuilder` or Robolectric + shadow players. Official demo app is excellent for reference.
- **ProGuard / R8**: Usually no extra rules required for core Media3. Add keep rules only if you see obfuscation issues with custom classes.
- **Migration from old ExoPlayer 2.x**: Use the official migration script from the ExoPlayer GitHub release tag. Package names changed to `androidx.media3.*`. `PlayerView` → `StyledPlayerView` was old; now it's `PlayerView` in media3-ui.

## 9. When to Read References

- **references/getting-started.md** — Core hello-world steps, dependency examples, MediaItem creation, basic player + view attachment.
- **references/lifecycle-best-practices.md** — Detailed Activity/Fragment/ViewModel patterns, what to do on config changes, error listener examples.
- **references/background-playback.md** — Complete MediaSessionService implementation, manifest, MediaController connection from UI, notification behavior, resumption, best practices for production apps.

## 10. Official Sources (Always Verify)

- Releases & changelog: https://developer.android.com/jetpack/androidx/releases/media3
- Getting started: https://developer.android.com/media/media3/exoplayer/hello-world
- Basic app guide: https://developer.android.com/media/implement/playback-app
- Background + Session: https://developer.android.com/media/media3/session/background-playback and control-playback
- Full demo: https://github.com/androidx/media/tree/release/demos/main/
- Javadoc: https://developer.android.com/reference/androidx/media3/exoplayer/package-summary

**Usage in this skill:** When asked to generate code or troubleshoot a Media3/ExoPlayer implementation, load the relevant reference file(s) first for accurate, up-to-date patterns. Prioritize `MediaSessionService` architecture for any app beyond simple in-foreground video players.

This skill focuses on practical, production-quality implementation rather than exhaustive API coverage.
