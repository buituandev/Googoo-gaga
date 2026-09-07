# Getting Started with Jetpack Media3 ExoPlayer

**Latest Stable Version (as of July 2026):** 1.10.1 (released May 12, 2026). Always verify the latest stable version at https://developer.android.com/jetpack/androidx/releases/media3 before starting. There is a 1.11.0-beta01 available but prefer stable for production.

Use consistent version across all `androidx.media3:*` dependencies.

## Add Dependencies

In `app/build.gradle.kts` (Kotlin DSL recommended):

```kotlin
dependencies {
    val media3Version = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    // For DASH adaptive streaming
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    // For HLS
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    // UI components (PlayerView)
    implementation("androidx.media3:media3-ui:$media3Version")
    // Compose Material3 UI (newer)
    implementation("androidx.media3:media3-ui-compose-material3:$media3Version")
    // For MediaSession and background playback
    implementation("androidx.media3:media3-session:$media3Version")
    // Common utilities
    implementation("androidx.media3:media3-common:$media3Version")
}
```

For Groovy `build.gradle`:

```groovy
def media3Version = '1.10.1'
implementation "androidx.media3:media3-exoplayer:$media3Version"
// ... same for others
```

Enable Java 8+:

```groovy
android {
    compileOptions {
        targetCompatibility JavaVersion.VERSION_1_8
        sourceCompatibility JavaVersion.VERSION_1_8
    }
}
```

Add Google Maven repository if not present (usually automatic with Android Studio).

## Core Steps (from official hello-world)

1. **Create ExoPlayer** (use Builder for configuration):
   ```kotlin
   val player = ExoPlayer.Builder(context).build()
   ```
   - Access only from the application thread (usually main Looper).
   - Configure with `.setTrackSelector(...)`, `.setLoadControl(...)`, `.setRenderersFactory(...)` etc. as needed.

2. **Attach to UI**:
   - XML: `<androidx.media3.ui.PlayerView android:id="@+id/player_view" ... />`
   - Code: `playerView.player = player`
   - Or for Compose: Use `PlayerSurface` or Material3 composables from media3-ui-compose-material3.

3. **Prepare Media**:
   ```kotlin
   val mediaItem = MediaItem.fromUri("https://example.com/video.mp4")
   // or with metadata
   val mediaItem = MediaItem.Builder()
       .setUri(uri)
       .setMediaMetadata(
           MediaMetadata.Builder()
               .setTitle("Title")
               .setArtist("Artist")
               .setArtworkUri(artworkUri)
               .build()
       )
       .build()
   player.setMediaItem(mediaItem)
   player.prepare()
   player.play()
   ```

4. **Handle Playlist**:
   - `player.addMediaItem(...)`, `player.removeMediaItem(...)`, `player.moveMediaItem(...)`
   - Playlists survive prepare; modify live.

5. **Release**:
   ```kotlin
   player.release()
   ```
   Always release to free decoders, especially in lifecycle callbacks.

## Threading Model
ExoPlayer operations must happen on a single thread (the one associated with its Looper, default main). Use `player.getApplicationLooper()`.

See official Javadoc for details.

## Demo App
Full working example: https://github.com/androidx/media/tree/release/demos/main/ (PlayerActivity)

## Further Reading in this Skill
- See references/lifecycle-best-practices.md
- See references/background-playback.md
- Official: https://developer.android.com/media/media3/exoplayer/hello-world
