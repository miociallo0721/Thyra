# Thyra

Thyra is a native Android doorway to Memoh agents and their workspaces. It is built with Kotlin and Jetpack Compose; it is not a WebView wrapper for the Memoh dashboard.

## Current checkpoint

The first vertical slice is implemented:

- add and validate multiple custom Memoh servers;
- authenticate with username/password or an existing access token;
- encrypt credentials with an Android Keystore AES-GCM key;
- restore the selected server, agent, and session after restart;
- list Memoh Bots as the top-level agents and list/create their sessions;
- load session history and render Markdown, code blocks, reasoning, and collapsed tool activity;
- stream the current run over Memoh's WebSocket runtime snapshot/delta protocol;
- recover the socket with bounded backoff and request a fresh snapshot after a sequence gap;
- stop an active generation with the WebSocket abort control;
- use phone navigation or an adaptive sessions/chat split on wide screens;
- explore the UI without a server through an isolated demo mode.

Files, terminal, desktop, memory, schedules, model management, and integrations remain later milestones. See [docs/ROADMAP.md](docs/ROADMAP.md).

## Build

Requirements:

- JDK 17 or newer (the Gradle toolchain compiles with Java 17);
- Android SDK platform 36;
- network access for the first Gradle dependency download.

On Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Connect to Memoh

1. Start Thyra and enter the public Memoh API address.
2. A direct backend address such as `https://host:8080` works. For a reverse-proxied Web deployment, use `https://host/api`; Thyra also probes that suffix automatically when the entered root is not a Memoh API.
3. Sign in with the Memoh username/password endpoint or paste an access token.
4. Select a Bot, then open or create a session.

Production builds reject cleartext HTTP through the Android network security policy. Debug builds permit HTTP for explicit local development only. TLS certificate verification is never disabled.

## Protocol baseline

The implementation was checked against `felinics/Memoh` commit `22752cd8da77e60427c0a03bd8b9238802781ec9` on 2026-09-19. Protocol details and compatibility decisions live in [docs/API_NOTES.md](docs/API_NOTES.md).

The product interaction study used `iebb/homem` commit `49f589cd55606ed9d44bec8224e51170e2f16035`. No Homem source code or artwork is included.

## Project structure

```text
:app                 application, Hilt graph, state restoration, navigation
:core:model          transport-independent models and UI state
:core:network        Memoh REST/WebSocket protocol and stream reducer
:core:data           repositories, DataStore selections, secure credentials
:core:designsystem   restrained Thyra theme and shared UI primitives
:feature:connection  server discovery and authentication UI
:feature:agents      Bot/agent list
:feature:sessions    session list and creation entry point
:feature:chat        transcript, Markdown, activities, composer
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the state and trust boundaries.
