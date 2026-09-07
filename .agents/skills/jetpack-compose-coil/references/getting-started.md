# Jetpack Compose + Coil Image Loading (Coil 3.5.0)

**Latest Version:** `io.coil-kt.coil3:coil-compose:3.5.0` (and `coil-network-okhttp:3.5.0` for networking).

## Dependencies (build.gradle.kts)

```kotlin
dependencies {
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0") // Recommended for OkHttp
}
```

## Key Composables

- **AsyncImage**: Preferred for most cases. Automatically sizes the image request based on the composable's constraints and ContentScale. Supports placeholder, error, crossfade, callbacks.

- **rememberAsyncImagePainter**: Returns a Painter. Good when you need a Painter or to observe state. Provide SizeResolver for correct sizing in dynamic layouts.

- **SubcomposeAsyncImage**: Slot-based API. **Avoid in LazyColumn/LazyGrid** due to subcomposition overhead.

## Basic Usage

```kotlin
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.layout.ContentScale

AsyncImage(
    model = "https://example.com/image.jpg",
    contentDescription = "Description",
    modifier = Modifier
        .size(200.dp)
        .clip(CircleShape),
    contentScale = ContentScale.Crop,
    placeholder = painterResource(R.drawable.placeholder),
    error = painterResource(R.drawable.error),
    fallback = painterResource(R.drawable.fallback)
)
```

Advanced with ImageRequest.Builder:

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data("https://example.com/image.jpg")
        .crossfade(true)
        .size(Size(800, 800)) // or use rememberConstraintsSizeResolver()
        .build(),
    contentDescription = null
)
```

For observing state with rememberAsyncImagePainter:

```kotlin
val painter = rememberAsyncImagePainter(
    model = "https://example.com/image.jpg",
    placeholder = painterResource(R.drawable.placeholder)
)
Image(painter = painter, contentDescription = null)
```

## Performance Best Practices

- Use **AsyncImage** inside LazyColumn/LazyGrid — it respects layout constraints for optimal downsampling.
- Provide explicit size or use `rememberConstraintsSizeResolver()` with rememberAsyncImagePainter for correct request sizing.
- Enable crossfade for smooth transitions.
- Coil automatically handles memory + disk caching, request cancellation on scroll, downsampling.
- Use stable keys in Lazy lists.
- For shared element transitions: Set matching `memoryCacheKey` / `placeholderMemoryCacheKey`.

## ImageLoader Configuration (Singleton)

Create a custom ImageLoader for advanced control (caching sizes, interceptors, etc.) and provide via CompositionLocal or Hilt.

See full docs for details.

Official: https://coil-kt.github.io/coil/compose/
