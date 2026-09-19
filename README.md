# Thyra

[中文](#中文) | [English](#english)

## 中文

Thyra 是通往 Memoh Agent 与工作区的原生 Android 入口。应用使用 Kotlin 与 Jetpack Compose 构建，并非 Memoh 管理面板的 WebView 封装。

### 当前进度

首个可用的纵向功能切片已经完成：

- 添加并验证多个自定义 Memoh 服务器；
- 优先使用邮箱和密码登录，同时兼容用户名和密码或现有访问令牌；
- 使用 Android Keystore AES-GCM 密钥加密凭据；
- 应用重启后恢复选中的服务器、Agent 与会话；
- 将 Memoh Bot 作为顶层 Agent 展示，并列出或创建对应会话；
- 加载会话历史，渲染 Markdown、代码块、推理内容和默认折叠的工具活动；
- 通过 Memoh WebSocket runtime snapshot/delta 协议接收当前任务的流式输出；
- 使用有界退避恢复 WebSocket，并在序列出现缺口时请求新的权威快照；
- 通过 WebSocket abort 控制停止正在生成的任务；
- 在手机上使用分层导航，在宽屏设备上使用会话与聊天双栏布局；
- 通过隔离的演示模式在没有服务器的情况下体验界面。

文件、终端、远程桌面、记忆、计划任务、模型管理和集成功能属于后续里程碑。详见 [docs/ROADMAP.md](docs/ROADMAP.md)。

### 构建

环境要求：

- JDK 17 或更高版本（Gradle 工具链使用 Java 17 编译）；
- Android SDK Platform 36；
- 首次下载 Gradle 依赖时能够访问网络。

Windows：

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

### 连接 Memoh

1. 启动 Thyra，输入可公开访问的 Memoh API 地址。
2. 可以填写 `https://host:8080` 这样的后端直连地址。若使用反向代理部署 Web 服务，请填写 `https://host/api`；当输入的根地址不是 Memoh API 时，Thyra 也会自动探测 `/api` 后缀。
3. 优先使用 Memoh 邮箱和密码登录；也可以使用用户名和密码，或粘贴访问令牌。
4. 选择一个 Bot，然后打开或创建会话。

生产构建通过 Android 网络安全策略拒绝明文 HTTP。Debug 构建仅为明确的本地开发场景允许 HTTP，任何构建都不会禁用 TLS 证书验证。

### 协议基线

当前实现于 2026-09-19 对照 `felinics/Memoh` 提交 `22752cd8da77e60427c0a03bd8b9238802781ec9` 完成检查。协议细节与兼容性决策记录在 [docs/API_NOTES.md](docs/API_NOTES.md)。

产品交互研究参考了 `iebb/homem` 提交 `49f589cd55606ed9d44bec8224e51170e2f16035`，项目未包含 Homem 的源代码或美术资源。

### 项目结构

```text
:app                 应用入口、Hilt 依赖图、状态恢复与导航
:core:model          与传输层无关的领域模型和 UI 状态
:core:network        Memoh REST/WebSocket 协议与流式状态归并器
:core:data           Repository、DataStore 选择状态与安全凭据
:core:designsystem   Thyra 主题与共享 UI 基础组件
:feature:connection  服务器发现与身份验证界面
:feature:agents      Bot/Agent 列表
:feature:sessions    会话列表与新建会话入口
:feature:chat        对话记录、Markdown、活动与消息输入框
```

状态边界与信任边界详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## English

Thyra is a native Android doorway to Memoh agents and their workspaces. It is built with Kotlin and Jetpack Compose; it is not a WebView wrapper for the Memoh dashboard.

### Current checkpoint

The first vertical slice is implemented:

- add and validate multiple custom Memoh servers;
- sign in primarily with email/password while retaining username/password and access-token compatibility;
- encrypt credentials with an Android Keystore AES-GCM key;
- restore the selected server, agent, and session after restart;
- list Memoh Bots as the top-level agents and list or create their sessions;
- load session history and render Markdown, code blocks, reasoning, and collapsed tool activity;
- stream the current run over Memoh's WebSocket runtime snapshot/delta protocol;
- recover the socket with bounded backoff and request a fresh snapshot after a sequence gap;
- stop an active generation with the WebSocket abort control;
- use phone navigation or an adaptive sessions/chat split on wide screens;
- explore the UI without a server through an isolated demo mode.

Files, terminal, desktop, memory, schedules, model management, and integrations remain later milestones. See [docs/ROADMAP.md](docs/ROADMAP.md).

### Build

Requirements:

- JDK 17 or newer (the Gradle toolchain compiles with Java 17);
- Android SDK Platform 36;
- network access for the first Gradle dependency download.

On Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Connect to Memoh

1. Start Thyra and enter the public Memoh API address.
2. A direct backend address such as `https://host:8080` works. For a reverse-proxied Web deployment, use `https://host/api`; Thyra also probes that suffix automatically when the entered root is not a Memoh API.
3. Prefer a Memoh email and password; a username and password or an existing access token also works.
4. Select a Bot, then open or create a session.

Production builds reject cleartext HTTP through the Android network security policy. Debug builds permit HTTP for explicit local development only. TLS certificate verification is never disabled.

### Protocol baseline

The implementation was checked against `felinics/Memoh` commit `22752cd8da77e60427c0a03bd8b9238802781ec9` on 2026-09-19. Protocol details and compatibility decisions live in [docs/API_NOTES.md](docs/API_NOTES.md).

The product interaction study used `iebb/homem` commit `49f589cd55606ed9d44bec8224e51170e2f16035`. No Homem source code or artwork is included.

### Project structure

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
