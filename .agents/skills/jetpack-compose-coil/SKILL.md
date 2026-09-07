---
name: jetpack-compose-coil
description: Comprehensive guidance on using Coil with Jetpack Compose for image loading — latest version (3.5.0), key composables (AsyncImage preferred), performance optimization in lists, caching, placeholders, sizing best practices, and common patterns. Use for any Compose image loading, Lazy list performance, or Coil implementation questions.
---

# Jetpack Compose + Coil Skill

**Coil** is the recommended modern image loading library for Jetpack Compose (Kotlin-first, coroutine-based, excellent caching and performance).

**Latest:** coil-compose 3.5.0 (with coil-network-okhttp).

## Setup & Basic Usage
See `references/getting-started.md`:
- Dependencies
- AsyncImage (recommended), rememberAsyncImagePainter, SubcomposeAsyncImage
- ImageRequest.Builder examples with crossfade, sizing, placeholders

## Performance & Best Practices (Critical for Smooth UIs)
See `references/performance-best-practices.md`:
- **Always prefer AsyncImage** in LazyColumn/LazyGrid.
- Automatic correct sizing & downsampling via constraints.
- Caching, request cancellation on scroll.
- Avoid subcomposition overhead.
- Placeholders, error states, cache keys for shared elements.
- Memory optimization tips.

## Key Rules
- Provide `contentDescription`.
- Use `ContentScale.Crop` / `FillBounds` etc. appropriately.
- Supply size hints when using painters in dynamic layouts.
- Customize ImageLoader for advanced caching/network control (singleton via Hilt/CompositionLocal).

Load the reference files for ready-to-use code snippets and detailed explanations.

Official documentation: https://coil-kt.github.io/coil/compose/
