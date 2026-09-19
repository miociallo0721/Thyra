# Thyra contributor guidance

Thyra is a native Android client. Do not add a WebView dashboard or guess Memoh endpoints.

Before changing an API feature, inspect the current `felinics/Memoh` OpenAPI specification, Go handler, and Web client. Record protocol changes in `docs/API_NOTES.md` with the inspected commit.

Keep dependencies pointing inward:

```text
features -> core:model + core:designsystem
core:data -> core:model + core:network
app -> features + core:data
```

Compose code must not invoke OkHttp directly. Keep credentials out of DataStore, Room, logs, URLs, screenshots, and fixtures. Production must keep cleartext traffic disabled and must never bypass TLS verification.

For streaming chat, preserve these invariants:

- `invocation_id` and `control_id` are stable across reconnect retries;
- a runtime delta is applied only when its epoch matches and its sequence is exactly the previous sequence plus one;
- a gap or `runtime_dropped` event triggers a fresh authoritative snapshot;
- settled REST history replaces optimistic/live turns after a run finishes;
- existing transcript content remains visible during connection errors.

Use semantic design tokens and flat content-first layouts. Tool activity and reasoning remain collapsed by default. All collection rows require stable keys and accessible labels.

Run both commands before handing off a change:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```
