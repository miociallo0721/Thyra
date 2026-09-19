package dev.thyra.core.data

import dev.thyra.core.model.Account
import dev.thyra.core.model.Agent
import dev.thyra.core.model.ChatSession
import dev.thyra.core.model.ChatTurn
import dev.thyra.core.model.RuntimeCursor
import dev.thyra.core.model.ServerCapabilities
import dev.thyra.core.model.ServerProfile
import dev.thyra.core.model.SocketStatus
import dev.thyra.core.network.ApiException
import dev.thyra.core.network.AuthCredential
import dev.thyra.core.network.ChatConnection
import dev.thyra.core.network.MemohService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DefaultThyraRepositoryTest {
  @Test
  fun passwordLogin_persistsCredentialOutsideProfileState() = runTest {
    val selections = InMemorySelections(PersistedState())
    val credentials = InMemoryCredentials()
    val service = FakeMemohService()
    val repository = DefaultThyraRepository(service, selections, credentials)
    val validation = repository.addServer("Home", "https://memoh.example")

    repository.authenticateWithPassword(validation.profile, "alice", "secret")

    assertEquals("jwt-1", credentials.get(validation.profile.id)?.token)
    assertEquals("https://memoh.example", selections.state.first().profiles.single().baseUrl)
    assertNotNull(selections.state.first().selectedServerId)
  }

  @Test
  fun unauthorizedRequest_refreshesOnceAndRetries() = runTest {
    val profile = ServerProfile("server", "Home", "https://memoh.example")
    val selections = InMemorySelections(PersistedState(listOf(profile), selectedServerId = profile.id))
    val credentials = InMemoryCredentials().apply { put(profile.id, StoredCredential("old")) }
    val service = FakeMemohService().apply { failAgentsToken = "old" }
    val repository = DefaultThyraRepository(service, selections, credentials)

    val agents = repository.loadAgents(profile)

    assertEquals("Shio", agents.single().displayName)
    assertEquals("refreshed", credentials.get(profile.id)?.token)
    assertEquals(1, service.refreshCalls)
  }
}

private class InMemorySelections(initial: PersistedState) : SelectionStore {
  private val mutable = MutableStateFlow(initial)
  override val state: Flow<PersistedState> = mutable
  override suspend fun update(transform: (PersistedState) -> PersistedState) { mutable.value = transform(mutable.value) }
}

private class InMemoryCredentials : CredentialStore {
  private val values = mutableMapOf<String, StoredCredential>()
  override fun get(serverId: String) = values[serverId]
  override fun put(serverId: String, credential: StoredCredential) { values[serverId] = credential }
  override fun remove(serverId: String) { values.remove(serverId) }
}

private class FakeMemohService : MemohService {
  var failAgentsToken: String? = null
  var refreshCalls = 0
  override suspend fun discover(input: String) = "https://memoh.example" to ServerCapabilities("dev")
  override suspend fun ping(baseUrl: String) = ServerCapabilities("dev")
  override suspend fun login(baseUrl: String, username: String, password: String) = AuthCredential("jwt-1")
  override suspend fun refresh(baseUrl: String, token: String): AuthCredential { refreshCalls++; return AuthCredential("refreshed") }
  override suspend fun me(baseUrl: String, token: String) = Account("u1", "alice", "Alice")
  override suspend fun agents(baseUrl: String, token: String): List<Agent> {
    if (token == failAgentsToken) throw ApiException(401, "auth.expired", "expired")
    return listOf(Agent("a1", "shio", "Shio"))
  }
  override suspend fun sessions(baseUrl: String, token: String, botId: String) = emptyList<ChatSession>()
  override suspend fun createSession(baseUrl: String, token: String, botId: String, title: String) = ChatSession("s1", botId, title = "New")
  override suspend fun messages(baseUrl: String, token: String, botId: String, sessionId: String) = emptyList<ChatTurn>()
  override fun openChatSocket(baseUrl: String, token: String, botId: String, sessionId: String): ChatConnection = NoopConnection()
}

private class NoopConnection : ChatConnection {
  override val events: SharedFlow<JsonObject> = MutableSharedFlow()
  override val status: StateFlow<SocketStatus> = MutableStateFlow(SocketStatus.Connected)
  override fun updateCursor(value: RuntimeCursor?) = Unit
  override fun requestSnapshot() = Unit
  override fun sendMessage(text: String, invocationId: String) = invocationId
  override fun abort(runId: String, controlId: String) = Unit
  override fun close() = Unit
}
