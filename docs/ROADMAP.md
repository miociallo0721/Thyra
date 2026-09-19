# Roadmap

## Implemented

- Native Kotlin/Compose application with edge-to-edge Material 3 foundations and a Thyra design layer.
- API 26 minimum and target/compile SDK 36.
- Multiple custom server profiles with `/ping` validation and `/api` discovery.
- Email-or-username/password login with email presented first, access-token login, proactive JWT refresh, expired-session handling, and logout/switch boundaries.
- Keystore-backed AES-GCM credential storage; non-secret state in DataStore.
- Bot list, session list, new session, session history, and restart restoration.
- Canonical Memoh WebSocket chat with subscription snapshots/deltas, stable reliable IDs, reconnect backoff, gap recovery, send, and abort.
- Selectable Markdown/code rendering, reasoning disclosure, grouped tool activity, local errors, status, and deliberate empty/loading states.
- Stable LazyColumn keys, bottom-aware streaming scroll, phone navigation, and a wide sessions/chat split.
- Isolated demo mode using the same domain/UI models.
- Unit tests for URL normalization, API decoding/auth headers, stream assembly/gaps, and repository refresh behavior.

## In progress

- Validation against a user-provided live Memoh deployment and physical Android device.
- Expanded malformed event, background/foreground, and instrumented Compose flow coverage.
- Interactive tool approval and structured user-question controls.

## Planned

1. Chat hardening: pagination, retry/edit/fork, attachments, queue/steer, model and reasoning controls.
2. Files: browse, read, transfer, rename, create/delete, then text editing.
3. Terminal: maintained terminal renderer, resize/keyboard/copy/paste, reconnect, state preservation.
4. Desktop: real Memoh display protocol, view-only first, then pointer/keyboard control.
5. Memory, schedules, models/providers, agent configuration, apps/integrations, and notifications.
6. Android sharesheet, deep links, shortcuts, widgets, drag/drop, and richer large-screen workspace composition.

## Intentionally deferred

- A general Room cache until concrete offline metadata has defined invalidation rules.
- The complete Homem free-form workspace compositor in the first milestone.
- Cleartext HTTP in production or custom trust-all TLS behavior.
- Silent fallback from a failed real server to demo data.
