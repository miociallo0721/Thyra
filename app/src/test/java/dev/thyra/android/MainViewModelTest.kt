package dev.thyra.android

import dev.thyra.core.data.AuthenticationExpiredException
import dev.thyra.core.data.PersistedState
import dev.thyra.core.data.RestoredConnection
import dev.thyra.core.data.ServerValidation
import dev.thyra.core.data.ThyraRepository
import dev.thyra.core.model.Account
import dev.thyra.core.model.Agent
import dev.thyra.core.model.ChatSession
import dev.thyra.core.model.ChatTurn
import dev.thyra.core.model.RuntimeCursor
import dev.thyra.core.model.ServerCapabilities
import dev.thyra.core.model.ServerProfile
import dev.thyra.core.model.SocketStatus
import dev.thyra.core.network.ChatConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun rapidSessionSwitch_lateFirstHistoryCannotReplaceLatestSessionOrAttachSocket() = runTest(dispatcher) {
    val profile = ServerProfile("server", "Home", "https://memoh.example")
    val agent = Agent("bot", "shio", "Shio")
    val first = ChatSession("first", agent.id, title = "First")
    val second = ChatSession("second", agent.id, title = "Second")
    val repository = SessionSwitchRepository(profile, agent, listOf(first, second))
    val viewModel = MainViewModel(repository)
    advanceUntilIdle()

    viewModel.selectProfile(profile)
    advanceUntilIdle()
    viewModel.selectAgent(agent)
    advanceUntilIdle()

    viewModel.selectSession(first)
    runCurrent()
    viewModel.selectSession(second)
    runCurrent()
    repository.history("second").complete(listOf(ChatTurn("second-turn", dev.thyra.core.model.ChatRole.User, "B")))
    advanceUntilIdle()
    repository.history("first").complete(listOf(ChatTurn("first-turn", dev.thyra.core.model.ChatRole.User, "A")))
    advanceUntilIdle()

    assertEquals("second", viewModel.uiState.value.selectedSession?.id)
    assertEquals(listOf("second-turn"), viewModel.uiState.value.history.map(ChatTurn::id))
    assertEquals(listOf("second"), repository.connections.map { it.sessionId })
    assertFalse(repository.connections.single().closed)
  }
}

private class SessionSwitchRepository(
  private val profile: ServerProfile,
  private val agent: Agent,
  private val sessions: List<ChatSession>,
) : ThyraRepository {
  private val state = MutableStateFlow(PersistedState(profiles = listOf(profile)))
  private val histories = mutableMapOf<String, CompletableDeferred<List<ChatTurn>>>()
  val connections = mutableListOf<FakeChatConnection>()

  override val persistedState: Flow<PersistedState> = state.asStateFlow()
  override suspend fun addServer(displayName: String, address: String) = ServerValidation(profile, ServerCapabilities())
  override suspend fun addOfficialCloud() = profile
  override suspend fun sendCloudEmailCode(email: String) = Unit
  override suspend fun authenticateOfficialCloud(email: String, code: String) = account()
  override suspend fun authenticateWithPassword(profile: ServerProfile, identity: String, password: String) = account()
  override suspend fun authenticateWithToken(profile: ServerProfile, token: String) = account()
  override suspend fun restoreSelected(): RestoredConnection? = RestoredConnection(profile, account())
  override suspend fun loadAgents(profile: ServerProfile) = listOf(agent)
  override suspend fun loadSessions(profile: ServerProfile, agentId: String) = sessions
  override suspend fun createSession(profile: ServerProfile, agentId: String) = sessions.first()
  override suspend fun loadMessages(profile: ServerProfile, agentId: String, sessionId: String) = history(sessionId).await()
  override fun openChat(profile: ServerProfile, agentId: String, sessionId: String): ChatConnection =
    FakeChatConnection(sessionId).also(connections::add)
  override suspend fun selectServer(serverId: String) { state.value = state.value.copy(selectedServerId = serverId) }
  override suspend fun selectAgent(serverId: String, agentId: String) = Unit
  override suspend fun selectSession(serverId: String, agentId: String, sessionId: String) = Unit
  override suspend fun logout(serverId: String) = Unit

  fun history(sessionId: String) = histories.getOrPut(sessionId) { CompletableDeferred() }
  private fun account() = Account("user", "alice", "Alice")
}

private class FakeChatConnection(val sessionId: String) : ChatConnection {
  private val mutableEvents = MutableSharedFlow<JsonObject>()
  private val mutableStatus = MutableStateFlow(SocketStatus.Connected)
  var closed = false
  override val events: Flow<JsonObject> = mutableEvents
  override val status: StateFlow<SocketStatus> = mutableStatus
  override fun updateCursor(value: RuntimeCursor?) = Unit
  override fun requestSnapshot() = Unit
  override fun sendMessage(text: String, invocationId: String) = invocationId
  override fun abort(runId: String, controlId: String) = Unit
  override fun close() { closed = true }
}
