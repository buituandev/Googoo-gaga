# Coil + Compose Performance & Best Practices

## Core Recommendations

1. **Prefer AsyncImage** over rememberAsyncImagePainter or SubcomposeAsyncImage in most scenarios, especially lists.
   - AsyncImage automatically determines the correct load size from constraints + ContentScale → better downsampling and memory usage.

2. **In LazyColumn / LazyGrid / LazyRow**:
   - Always use **AsyncImage**.
   - Avoid **SubcomposeAsyncImage** (subcomposition is expensive).
   - For rememberAsyncImagePainter, supply a SizeResolver (e.g. `rememberConstraintsSizeResolver()`) so Coil knows the target size.

3. **Sizing & Downsampling**:
   - Let Coil handle it via constraints.
   - Explicit `.size(width, height)` in ImageRequest when you know the target.
   - Use transformations (resize, crop) only when necessary.

4. **Caching**:
   - Coil has built-in memory + disk cache.
   - Customize via custom ImageLoader (MemoryCache, DiskCache sizes, max size, etc.).
   - Use meaningful cache keys for shared elements or reuse.

5. **Placeholders & States**:
   - Always provide good `placeholder`, `error`, `fallback`.
   - Use `onLoading`, `onSuccess`, `onError` callbacks for side effects (analytics, etc.).
   - Crossfade (`crossfade(true)`) for polished UX.

6. **Memory Optimization**:
   - Coil is lightweight and efficient.
   - Avoid loading huge images; let downsampling do its job.
   - In lists, images are automatically cancelled/paused when off-screen.
   - Consider prefetching strategies if needed (via ImageLoader or manual preloading requests).

7. **Other Tips**:
   - Use `LocalContext.current` safely.
   - For Compose Multiplatform compatibility notes (resources via Res.getUri if needed).
   - Test with large lists and different screen densities.
   - Combine with Coil's OkHttp or Ktor network layer for best networking performance.
   - Monitor with Android Profiler / Compose Layout Inspector for recompositions.

## Common Pitfalls to Avoid

- Using SubcomposeAsyncImage in scrolling lists.
- Not providing size hints → loads at full resolution unnecessarily.
- Forgetting contentDescription for accessibility.
- Overusing heavy transformations on every recomposition.

## Advanced

- Custom ImageLoader with interceptors, custom decoders, or cache policies.
- Prefetch images ahead in lists using Coil's request queuing.
- Integration with shared element transitions via cache keys.

Follow these for smooth, performant image loading in Compose UIs.

Sources: Official Coil Compose docs + Android performance guidance.
