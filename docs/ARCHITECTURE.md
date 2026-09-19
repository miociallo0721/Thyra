# Architecture

## Scope

This checkpoint intentionally implements the connection-to-chat vertical slice. The architecture leaves room for files, terminal, desktop, and other workspace panels without pretending those features already exist.

## Module boundaries

- `core:model` contains stable domain names such as `ServerProfile`, `Agent`, `ChatSession`, `ChatTurn`, and `LiveChatState`. It has no HTTP or Compose dependency.
- `core:network` owns all current Memoh wire knowledge. DTOs are internal and are mapped to domain models before leaving the module. `ChatStreamReducer` is the ordered runtime event reducer.
- `core:data` owns profile/selection persistence, Keystore-backed credential storage, proactive token refresh, and the repository boundary consumed by the application.
- `core:designsystem` provides the color, spacing, avatar, status, empty, and error treatments used by features.
- feature modules are stateless Compose surfaces. They receive domain state and event callbacks; they do not depend on the network layer.
- `app` owns the Hilt graph, `MainViewModel`, Navigation 3 routes, lifecycle-sized socket ownership, state restoration, and adaptive composition.

## Data flow

```text
Compose event
  -> MainViewModel intent
  -> ThyraRepository
  -> Memoh REST or WebSocket client
  -> DTO / runtime JSON mapping
  -> StateFlow<ThyraUiState>
  -> Compose
```

The UI follows unidirectional data flow. Feature composables never mutate shared state or call transport APIs.

## Server and authentication state

Non-secret state is serialized into Preferences DataStore:

- server profiles;
- selected server;
- most recently selected agent per server;
- most recently selected session per server/agent pair.

Access tokens are stored separately. `KeystoreCredentialStore` generates a non-exportable AES key in Android Keystore and stores only AES-GCM ciphertext, IV, and tag in a dedicated private SharedPreferences file. Android backup and device-transfer rules exclude that file.

The current Memoh refresh endpoint accepts the still-valid JWT and returns another JWT; there is no refresh token. Thyra proactively refreshes within five minutes of `expires_at`. A 401 causes one refresh-and-retry attempt. Failure removes the credential and returns to authentication.

## Server URL discovery

`UrlNormalizer` accepts only HTTP(S), rejects embedded credentials/query/fragment data, normalizes trailing slashes, and probes:

1. the entered base URL;
2. the same URL with `/api`, unless it already ends in `/api`.

`GET /ping` must decode as a Memoh capability response with `status=ok`; generic HTTP reachability is not treated as a valid server.

## Streaming model

One `ChatConnection` belongs to the visible session. It authenticates the WebSocket with an Authorization header and subscribes to that session's runtime.

Reliable outbound messages are held by stable IDs until the server acknowledges them. A reconnect resends the pending envelopes, letting Memoh's invocation/control idempotency avoid duplicate turns.

`ChatStreamReducer` accepts an authoritative `runtime_snapshot`, then only consecutive `runtime_delta` frames for the same epoch. It performs targeted message append/upsert operations. A gap never gets guessed through: the visible state is retained while the socket requests another snapshot.

After a terminal run status, the ViewModel reloads REST history. This reconciles optimistic user turns and live assistant output with the server's settled ordering.

## UI and adaptation

Navigation 3 represents the compact flow: connection → authentication → agents → sessions → chat. At 840 dp and above, sessions and chat share a split layout. The feature state remains keyed by server, agent, and session so wider workspace panels can be added without global mutable UI state.

The transcript uses stable turn IDs. Only the current assistant turn changes during streaming; settled rows remain stable. Markwon renders Markdown into a selectable native `TextView` hosted inside Compose—there is no WebView.

## Deliberate deferrals

Room is not included yet because this checkpoint has no useful offline cache to persist. Adding an empty database would create migration obligations without product value. Introduce Room with the first bounded metadata cache and keep large binary workspace data out of it.

Tool approvals and structured user questions are represented by upstream activity data but their response controls are deferred until the next chat-hardening iteration. Files, terminal, and desktop will receive separate feature modules behind the same agent/workspace identity.
