# Jetpack DataStore Best Practices

## Core Rules (Must Follow)
1. **Single Instance per file/process**: Never create multiple DataStore instances for the same file. Use extension property on Context for singleton.
2. **Immutability**: Data type T in DataStore<T> must be immutable (Proto is ideal).
3. **Repository Pattern**: Wrap DataStore in a Repository class injected via Hilt/Dagger. Expose Flows for reads, suspend functions for writes.
4. **Transactionality**: Use `edit {}` or `updateData {}` for atomic updates.
5. **Error Handling**: Use `CorruptionExceptionHandler` for file corruption (e.g. bad migration or crashes).
6. **Do not block main thread**: All operations are async via Flow / suspend.

## Preferences vs Proto
- **Preferences**: Simple key-value. Good for flags, counters.
- **Proto**: Typed, schema-enforced, efficient. Recommended for most cases.

## Migration from SharedPreferences
Use `SharedPreferencesMigration` in DataStore creation.

## Testing
- Use in-memory DataStore for unit tests.
- Robolectric or instrumented tests for real persistence.

## Multi-Process
Use `MultiProcessDataStore` if needed across processes.

Full details in official DataStore docs.
