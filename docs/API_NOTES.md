# Memoh API notes

Last audited: 2026-09-19
Repository: `felinics/Memoh`
Commit: `22752cd8da77e60427c0a03bd8b9238802781ec9`

The OpenAPI files in `spec/swagger.json` and `spec/swagger.yaml`, Go handlers, and current Web chat client were inspected. These notes are a compatibility record, not an assumption that future Memoh versions are unchanged.

## Base and health

- Direct backend routes are rooted at the configured server address.
- Hosted Web commonly proxies those routes under `/api`.
- `GET /ping` is unauthenticated and returns `status`, `version`, `commit_hash`, `container_backend`, and capability fields.
- `HEAD /health` returns an empty 200 response. There is no JSON `GET /health` handler.

Thyra validates with `/ping`, not with an arbitrary successful page.

## Authentication

- `POST /auth/login` body: `{ "username": "...", "password": "..." }`.
- The response contains `access_token`, `token_type`, and `expires_at`; no refresh token exists.
- `POST /auth/refresh` requires the still-valid bearer JWT and returns another access token.
- `GET /users/me` verifies a supplied/restored token.
- Protected HTTP and WebSocket routes accept `Authorization: Bearer <token>`. The server also accepts a `token` query parameter, but Thyra avoids placing credentials in URLs.

Because an expired JWT cannot call `/auth/refresh`, Thyra refreshes shortly before expiration and otherwise requires a new login.

## Milestone REST endpoints

```text
GET  /bots
GET  /bots/{bot_id}/agents
GET  /bots/{bot_id}/sessions?types=chat,discuss,acp_agent&limit=100
POST /bots/{bot_id}/sessions
GET  /bots/{bot_id}/messages?session_id={session_id}&limit=100
```

Top-level Thyra Agent rows currently map to Memoh Bots, which are the workspace/chat identity exposed by Homem. Memoh's nested Bot Agents remain a later runtime selector.

The create-session body uses `channel_type: local`, matching the current Memoh Web implementation. DTOs ignore unknown optional fields so additive server changes do not crash decoding.

## Canonical chat WebSocket

Connect to:

```text
GET /bots/{bot_id}/web/ws
```

Send:

```json
{
  "type": "message",
  "invocation_id": "client UUID",
  "session_id": "session UUID",
  "text": "Hello"
}
```

Subscribe to the session runtime on the same socket:

```json
{
  "type": "runtime_subscribe",
  "session_id": "session UUID",
  "cursor": { "epoch": "...", "seq": 10 }
}
```

Important server events:

- `run_accepted` / `run_rejected` acknowledge the invocation intent;
- `runtime_snapshot` is authoritative full live state;
- `runtime_delta` mutates that state with a matching epoch and consecutive sequence;
- `runtime_dropped` means the client must resubscribe for a new snapshot;
- `control_ack` acknowledges abort/approval/input controls;
- `error` is visible but must not clear the current transcript.

The server does not replay missing deltas from a cursor. The cursor identifies client position; a gap must be recovered with a snapshot.

Abort is WebSocket-only:

```json
{
  "type": "abort",
  "session_id": "session UUID",
  "run_id": "run UUID",
  "control_id": "client UUID"
}
```

`POST /bots/{bot_id}/web/messages` is a legacy admission path and does not carry canonical streamed output. Thyra does not use it.

## Error handling

Newer application errors use `application/problem+json` with `code` and `detail`; older handlers may return `message` or `reason`. The client accepts all three human-readable fields and branches on HTTP 401 for credential recovery.

Relevant conditions include unauthorized/forbidden, busy or conflicting session runtime, provider rate limit/quota/auth errors, runtime interruption, malformed frames, and unavailable external runtimes.

## Source pointers

- health: `internal/handlers/ping.go`
- login/refresh: `internal/handlers/auth.go`, `internal/auth/jwt.go`
- sessions: `internal/handlers/session.go`
- history: `internal/handlers/message.go`
- WebSocket envelope/control: `internal/handlers/local_channel.go`
- runtime snapshot/delta: `internal/handlers/runtime_ws.go`, `internal/agent/runtime/session/types.go`
- current Web client: `apps/web/src/composables/api/useChat.*.ts`
