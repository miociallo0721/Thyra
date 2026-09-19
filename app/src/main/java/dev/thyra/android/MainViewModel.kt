package dev.thyra.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thyra.core.data.AuthenticationExpiredException
import dev.thyra.core.data.PersistedState
import dev.thyra.core.data.ThyraRepository
import dev.thyra.core.model.Account
import dev.thyra.core.model.Agent
import dev.thyra.core.model.AppDestination
import dev.thyra.core.model.ChatBlock
import dev.thyra.core.model.ChatRole
import dev.thyra.core.model.ChatSession
import dev.thyra.core.model.ChatTurn
import dev.thyra.core.model.LiveChatState
import dev.thyra.core.model.ServerProfile
import dev.thyra.core.model.SocketStatus
import dev.thyra.core.model.ThyraUiState
import dev.thyra.core.network.ChatConnection
import dev.thyra.core.network.ChatStreamReducer
import java.util.UUID
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
  private val repository: ThyraRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(ThyraUiState())
  val uiState: StateFlow<ThyraUiState> = _uiState.asStateFlow()

  private var socket: ChatConnection? = null
  private var socketEventsJob: Job? = null
  private var socketStatusJob: Job? = null
  private var openSessionJob: Job? = null
  private var sessionOpeningGeneration = 0L
  private var demoMode = false

  init {
    viewModelScope.launch {
      repository.persistedState.collect { persisted ->
        _uiState.update { it.copy(profiles = persisted.profiles) }
      }
    }
    restore()
  }

  private fun restore() = viewModelScope.launch {
    val persisted = repository.persistedState.first()
    val selected = persisted.profiles.firstOrNull { it.id == persisted.selectedServerId }
    if (selected == null) {
      _uiState.update { it.copy(restoring = false, destination = AppDestination.Connection) }
      return@launch
    }
    _uiState.update { it.copy(selectedProfile = selected, profiles = persisted.profiles) }
    try {
      val restored = repository.restoreSelected() ?: throw AuthenticationExpiredException()
      restoreWorkspace(restored.profile, restored.account, persisted)
    } catch (_: AuthenticationExpiredException) {
      _uiState.update {
        it.copy(restoring = false, destination = AppDestination.Authentication, errorMessage = "请重新登录 ${selected.displayName}")
      }
    } catch (failure: Throwable) {
      if (failure is CancellationException) throw failure
      _uiState.update {
        it.copy(restoring = false, destination = AppDestination.Authentication, errorMessage = failure.userMessage())
      }
    }
  }

  private suspend fun restoreWorkspace(profile: ServerProfile, account: Account, persisted: PersistedState) {
    val agents = repository.loadAgents(profile)
    val rememberedAgent = agents.firstOrNull { it.id == persisted.selectedAgentByServer[profile.id] }
    if (rememberedAgent == null) {
      _uiState.update {
        it.copy(
          restoring = false,
          destination = AppDestination.Agents,
          selectedProfile = profile,
          account = account,
          agents = agents,
          errorMessage = null,
        )
      }
      return
    }
    val sessions = repository.loadSessions(profile, rememberedAgent.id)
    val rememberedSessionId = persisted.selectedSessionByAgent[persisted.sessionKey(profile.id, rememberedAgent.id)]
    val rememberedSession = sessions.firstOrNull { it.id == rememberedSessionId }
    _uiState.update {
      it.copy(
        restoring = false,
        destination = if (rememberedSession == null) AppDestination.Sessions else AppDestination.Chat,
        selectedProfile = profile,
        account = account,
        agents = agents,
        selectedAgent = rememberedAgent,
        sessions = sessions,
        selectedSession = rememberedSession,
        errorMessage = null,
      )
    }
    if (rememberedSession != null) startOpenSession(profile, rememberedAgent, rememberedSession)
  }

  fun addServer(displayName: String, address: String) = launchAction {
    cancelSessionOpening()
    closeSocket()
    val validation = repository.addServer(displayName, address)
    _uiState.update {
      it.copy(
        destination = AppDestination.Authentication,
        selectedProfile = validation.profile,
        serverCapabilities = validation.capabilities,
      )
    }
  }

  fun selectProfile(profile: ServerProfile) = launchAction {
    cancelSessionOpening()
    closeSocket()
    repository.selectServer(profile.id)
    _uiState.update { it.copy(selectedProfile = profile, destination = AppDestination.Authentication) }
    try {
      val restored = repository.restoreSelected() ?: return@launchAction
      val persisted = repository.persistedState.first()
      restoreWorkspace(restored.profile, restored.account, persisted)
    } catch (_: AuthenticationExpiredException) {
      // The authentication screen is already visible.
    } catch (failure: Throwable) {
      if (failure is CancellationException) throw failure
      throw failure
    }
  }

  fun loginWithPassword(identity: String, password: String) = launchAction {
    val profile = requireNotNull(_uiState.value.selectedProfile)
    val account = repository.authenticateWithPassword(profile, identity, password)
    finishLogin(profile, account)
  }

  fun loginWithToken(token: String) = launchAction {
    val profile = requireNotNull(_uiState.value.selectedProfile)
    val account = repository.authenticateWithToken(profile, token)
    finishLogin(profile, account)
  }

  private suspend fun finishLogin(profile: ServerProfile, account: Account) {
    val agents = repository.loadAgents(profile)
    _uiState.update {
      it.copy(destination = AppDestination.Agents, account = account, agents = agents, selectedAgent = null, sessions = emptyList())
    }
  }

  fun refreshAgents() = launchAction {
    val profile = requireNotNull(_uiState.value.selectedProfile)
    _uiState.update { it.copy(agents = repository.loadAgents(profile)) }
  }

  fun selectAgent(agent: Agent) = launchAction {
    cancelSessionOpening()
    closeSocket()
    val profile = requireNotNull(_uiState.value.selectedProfile)
    repository.selectAgent(profile.id, agent.id)
    val sessions = if (demoMode) demoSessions(agent.id) else repository.loadSessions(profile, agent.id)
    _uiState.update {
      it.copy(destination = AppDestination.Sessions, selectedAgent = agent, sessions = sessions, selectedSession = null, history = emptyList())
    }
  }

  fun refreshSessions() = launchAction {
    val state = _uiState.value
    val profile = requireNotNull(state.selectedProfile)
    val agent = requireNotNull(state.selectedAgent)
    val sessions = if (demoMode) demoSessions(agent.id) else repository.loadSessions(profile, agent.id)
    _uiState.update { it.copy(sessions = sessions) }
  }

  fun createSession() {
    val generation = cancelSessionOpening()
    closeSocket()
    launchAction {
      val state = _uiState.value
      val profile = requireNotNull(state.selectedProfile)
      val agent = requireNotNull(state.selectedAgent)
      val session = if (demoMode) ChatSession("demo-new", agent.id, title = "新对话")
      else repository.createSession(profile, agent.id)
      if (!isCurrentSessionOpening(generation)) return@launchAction
      _uiState.update { it.copy(sessions = listOf(session) + it.sessions.filterNot { item -> item.id == session.id }) }
      startOpenSession(profile, agent, session, generation = generation)
    }
  }

  fun selectSession(session: ChatSession) {
    val state = _uiState.value
    startOpenSession(requireNotNull(state.selectedProfile), requireNotNull(state.selectedAgent), session)
  }

  private fun startOpenSession(
    profile: ServerProfile,
    agent: Agent,
    session: ChatSession,
    preserveTranscript: Boolean = false,
    generation: Long? = null,
  ) {
    val openingGeneration = generation ?: cancelSessionOpening()
    if (!isCurrentSessionOpening(openingGeneration)) return
    closeSocket()
    _uiState.update {
      it.copy(
        destination = AppDestination.Chat,
        selectedSession = session,
        history = if (preserveTranscript) it.history else emptyList(),
        live = LiveChatState(),
        socketStatus = if (demoMode) SocketStatus.Connected else SocketStatus.Connecting,
      )
    }
    openSessionJob = viewModelScope.launch {
      try {
        val history = if (demoMode) demoHistory(session.id) else repository.loadMessages(profile, agent.id, session.id)
        if (!isCurrentSessionOpening(openingGeneration)) return@launch
        if (!demoMode) repository.selectSession(profile.id, agent.id, session.id)
        if (!isCurrentSessionOpening(openingGeneration)) return@launch
        _uiState.update { it.copy(history = history, live = LiveChatState()) }
        if (!demoMode) {
          val connection = repository.openChat(profile, agent.id, session.id)
          if (isCurrentSessionOpening(openingGeneration)) attachSocket(connection, openingGeneration) else connection.close()
        }
      } catch (failure: AuthenticationExpiredException) {
        if (!isCurrentSessionOpening(openingGeneration)) return@launch
        _uiState.update { it.copy(destination = AppDestination.Authentication, errorMessage = failure.userMessage()) }
      } catch (failure: Throwable) {
        if (failure is CancellationException) throw failure
        if (!isCurrentSessionOpening(openingGeneration)) return@launch
        _uiState.update { it.copy(socketStatus = SocketStatus.Disconnected, errorMessage = failure.userMessage()) }
      }
    }
  }

  private fun attachSocket(connection: ChatConnection, generation: Long) {
    if (!isCurrentSessionOpening(generation)) {
      connection.close()
      return
    }
    closeSocket()
    if (!isCurrentSessionOpening(generation)) {
      connection.close()
      return
    }
    socket = connection
    socketEventsJob = viewModelScope.launch {
      connection.events.collect { event ->
        if (socket !== connection || !isCurrentSessionOpening(generation)) return@collect
        val previous = _uiState.value.live
        val next = ChatStreamReducer.reduce(previous, event)
        _uiState.update { it.copy(live = next, errorMessage = next.errorMessage ?: it.errorMessage) }
        connection.updateCursor(next.cursor)
        if (!previous.needsSnapshot && next.needsSnapshot) connection.requestSnapshot()
        val justFinished = previous.isGenerating && !next.isGenerating && next.runStatus in TERMINAL_STATES
        if (justFinished) {
          delay(200)
          refreshHistoryAfterRun(connection, generation)
        }
      }
    }
    socketStatusJob = viewModelScope.launch {
      connection.status.collect { status ->
        if (socket !== connection || !isCurrentSessionOpening(generation)) return@collect
        _uiState.update { it.copy(socketStatus = status) }
        if (status == SocketStatus.Expired) {
          _uiState.value.selectedProfile?.let { repository.logout(it.id) }
          if (socket !== connection || !isCurrentSessionOpening(generation)) return@collect
          _uiState.update { it.copy(destination = AppDestination.Authentication, errorMessage = "登录已过期，请重新登录") }
        }
      }
    }
  }

  private suspend fun refreshHistoryAfterRun(connection: ChatConnection, generation: Long) {
    val state = _uiState.value
    val profile = state.selectedProfile ?: return
    val agent = state.selectedAgent ?: return
    val session = state.selectedSession ?: return
    try {
      val turns = repository.loadMessages(profile, agent.id, session.id)
      if (socket === connection && isCurrentSessionOpening(generation)) {
        _uiState.update { it.copy(history = turns, live = LiveChatState()) }
      }
    } catch (failure: Throwable) {
      if (failure is CancellationException) throw failure
      if (socket === connection && isCurrentSessionOpening(generation)) {
        _uiState.update { it.copy(errorMessage = failure.userMessage()) }
      }
    }
  }

  fun sendMessage(text: String) {
    if (demoMode) {
      sendDemoMessage(text)
      return
    }
    val connection = socket ?: return
    if (_uiState.value.socketStatus != SocketStatus.Connected || _uiState.value.live.isGenerating) return
    val invocationId = UUID.randomUUID().toString()
    val optimistic = ChatTurn(
      id = "local:$invocationId",
      role = ChatRole.User,
      text = text,
      isPending = true,
    )
    _uiState.update {
      it.copy(
        history = it.history + optimistic,
        live = it.live.copy(runStatus = "admitting", errorMessage = null),
        errorMessage = null,
      )
    }
    connection.sendMessage(text, invocationId)
  }

  fun stopGeneration() {
    val runId = _uiState.value.live.activeRunId ?: return
    socket?.abort(runId)
    _uiState.update { it.copy(live = it.live.copy(runStatus = "aborting")) }
  }

  fun exploreDemo() {
    cancelSessionOpening()
    closeSocket()
    demoMode = true
    val profile = ServerProfile("demo", "Thyra 演示", "https://demo.invalid")
    _uiState.value = ThyraUiState(
      restoring = false,
      destination = AppDestination.Agents,
      profiles = _uiState.value.profiles,
      selectedProfile = profile,
      account = Account("demo", "demo", "演示用户"),
      agents = demoAgents(),
    )
  }

  private fun sendDemoMessage(text: String) {
    if (_uiState.value.live.isGenerating) return
    val id = UUID.randomUUID().toString()
    _uiState.update {
      it.copy(
        history = it.history + ChatTurn("demo-user-$id", ChatRole.User, text),
        live = LiveChatState(
          activeRunId = "demo-run-$id",
          activeTurn = ChatTurn("demo-assistant-$id", ChatRole.Assistant, isPending = true),
          runStatus = "running",
        ),
      )
    }
    viewModelScope.launch {
      val chunks = listOf("这是 Thyra 的", "原生流式对话演示。\n\n", "消息、**Markdown**、代码和活动", "会按稳定的列表项逐步更新。")
      var content = ""
      for (chunk in chunks) {
        delay(180)
        content += chunk
        _uiState.update { state ->
          state.copy(live = state.live.copy(activeTurn = state.live.activeTurn?.copy(blocks = listOf(ChatBlock.Text(content, 1)))))
        }
      }
      val completed = _uiState.value.live.activeTurn?.copy(isPending = false)
      _uiState.update { state ->
        state.copy(history = state.history + listOfNotNull(completed), live = LiveChatState(), socketStatus = SocketStatus.Connected)
      }
    }
  }

  fun back() {
    when (_uiState.value.destination) {
      AppDestination.Chat -> {
        cancelSessionOpening()
        closeSocket()
        _uiState.update { it.copy(destination = AppDestination.Sessions, selectedSession = null, history = emptyList(), live = LiveChatState()) }
      }
      AppDestination.Sessions -> _uiState.update { it.copy(destination = AppDestination.Agents, selectedAgent = null, sessions = emptyList()) }
      AppDestination.Agents, AppDestination.Authentication -> {
        cancelSessionOpening()
        closeSocket()
        demoMode = false
        _uiState.update { it.copy(destination = AppDestination.Connection, errorMessage = null) }
      }
      AppDestination.Connection -> Unit
    }
  }

  fun switchServer() {
    cancelSessionOpening()
    closeSocket()
    demoMode = false
    _uiState.update { it.copy(destination = AppDestination.Connection, selectedAgent = null, selectedSession = null, errorMessage = null) }
  }

  fun logout() {
    if (demoMode) {
      switchServer()
      return
    }
    launchAction {
      cancelSessionOpening()
      val profile = requireNotNull(_uiState.value.selectedProfile)
      closeSocket()
      repository.logout(profile.id)
      _uiState.update {
        it.copy(
          destination = AppDestination.Authentication,
          account = null,
          agents = emptyList(),
          selectedAgent = null,
          sessions = emptyList(),
          selectedSession = null,
          history = emptyList(),
          live = LiveChatState(),
          socketStatus = SocketStatus.Disconnected,
        )
      }
    }
  }

  fun onAppBackgrounded() {
    if (demoMode || (socket == null && openSessionJob == null)) return
    cancelSessionOpening()
    closeSocket()
    _uiState.update { it.copy(socketStatus = SocketStatus.Disconnected) }
  }

  fun onAppForegrounded() {
    val state = _uiState.value
    if (demoMode || socket != null || state.destination != AppDestination.Chat) return
    val profile = state.selectedProfile ?: return
    val agent = state.selectedAgent ?: return
    val session = state.selectedSession ?: return
    startOpenSession(profile, agent, session, preserveTranscript = true)
  }

  private fun launchAction(block: suspend () -> Unit) = viewModelScope.launch {
    _uiState.update { it.copy(busy = true, errorMessage = null) }
    try {
      block()
    } catch (failure: AuthenticationExpiredException) {
      _uiState.update { it.copy(destination = AppDestination.Authentication, errorMessage = failure.userMessage()) }
    } catch (failure: Throwable) {
      if (failure is CancellationException) throw failure
      _uiState.update { it.copy(errorMessage = failure.userMessage()) }
    } finally {
      _uiState.update { it.copy(busy = false, restoring = false) }
    }
  }

  private fun closeSocket() {
    val connection = socket
    socket = null
    socketEventsJob?.cancel()
    socketStatusJob?.cancel()
    socketEventsJob = null
    socketStatusJob = null
    connection?.close()
  }

  private fun cancelSessionOpening(): Long {
    sessionOpeningGeneration += 1
    openSessionJob?.cancel()
    openSessionJob = null
    return sessionOpeningGeneration
  }

  private fun isCurrentSessionOpening(generation: Long): Boolean = generation == sessionOpeningGeneration

  override fun onCleared() {
    cancelSessionOpening()
    closeSocket()
    super.onCleared()
  }

  companion object {
    private val TERMINAL_STATES = setOf("completed", "aborted", "errored", "lost")
  }
}

private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "操作失败，请稍后重试"

private fun demoAgents() = listOf(
  Agent("demo-shio", "shio", "Shio", status = "ready", permissions = setOf("chat")),
  Agent("demo-atelier", "atelier", "Atelier", status = "ready", permissions = setOf("chat")),
)

private fun demoSessions(agentId: String) = listOf(
  ChatSession("demo-session", agentId, title = "欢迎来到 Thyra", updatedAt = "刚刚"),
  ChatSession("demo-workspace", agentId, title = "检查工作区状态", updatedAt = "昨天"),
)

private fun demoHistory(sessionId: String) = if (sessionId == "demo-new") emptyList() else listOf(
  ChatTurn("demo-u1", ChatRole.User, "Thyra 能做什么？"),
  ChatTurn(
    "demo-a1",
    ChatRole.Assistant,
    blocks = listOf(
      ChatBlock.Text("Thyra 是通往 Memoh Agent 与工作区的原生 Android 入口。\n\n- 连接多个服务器\n- 恢复对话\n- 实时查看 Agent 输出\n- 将工具活动保持在需要时才展开"),
      ChatBlock.Tool(1, "search", input = "{\"query\":\"workspace\"}", output = "Found 4 files"),
      ChatBlock.Tool(2, "read_file", input = "README.md", output = "# Workspace"),
    ),
  ),
)
