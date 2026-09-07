# Jetpack DataStore Getting Started (v1.2.1)

**Latest Stable:** 1.2.1 (check https://developer.android.com/jetpack/androidx/releases/datastore for updates).

## Dependencies (build.gradle.kts)

**Preferences DataStore (key-value, like SharedPreferences but better):**
```kotlin
implementation("androidx.datastore:datastore-preferences:1.2.1")
```

**Proto / Typed DataStore (recommended for structured data):**
```kotlin
implementation("androidx.datastore:datastore:1.2.1")
```

Optional: RxJava support, core-only for KMP, etc.

For Proto: Add protobuf plugin + `protobuf-kotlin-lite`.

## Singleton Access (Best Practice)
```kotlin
// Top level in a file (e.g. DataStoreModule.kt)
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// Or for Proto:
val Context.userPreferencesDataStore: DataStore<UserPreferences> by dataStore(
    fileName = "user_prefs.pb",
    serializer = UserPreferencesSerializer
)
```

## Basic Usage

**Read (Flow):**
```kotlin
val counterFlow: Flow<Int> = context.settingsDataStore.data
    .map { preferences ->
        preferences[EXAMPLE_COUNTER] ?: 0
    }
```

**Write (suspend):**
```kotlin
context.settingsDataStore.edit { preferences ->
    preferences[EXAMPLE_COUNTER] = preferences[EXAMPLE_COUNTER] ?: 0 + 1
}
```

Use in Repository + ViewModel with `stateIn` or in Compose `collectAsState()`.

See official docs for full Proto serializer, corruption handler, etc.
