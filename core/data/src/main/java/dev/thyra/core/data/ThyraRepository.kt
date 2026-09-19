package dev.thyra.core.data

import android.content.Context
import dev.thyra.core.model.Account
import dev.thyra.core.model.Agent
import dev.thyra.core.model.AuthMode
import dev.thyra.core.model.ChatSession
import dev.thyra.core.model.ChatTurn
import dev.thyra.core.model.ServerCapabilities
import dev.thyra.core.model.ServerProfile
import dev.thyra.core.network.ApiException
import dev.thyra.core.network.ChatConnection
import dev.thyra.core.network.MemohApiClient
import dev.thyra.core.network.MemohService
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class ServerValidation(val profile: ServerProfile, val capabilities: ServerCapabilities)
data class RestoredConnection(val profile: ServerProfile, val account: Account)

class AuthenticationExpiredException(message: String = "登录已过期，请重新登录") : IOException(message)

interface ThyraRepository {
  val persistedState: Flow<PersistedState>
  suspend fun addServer(displayName: String, address: String): ServerValidation
  suspend fun authenticateWithPassword(profile: ServerProfile, identity: String, password: String): Account
  suspend fun authenticateWithToken(profile: ServerProfile, token: String): Account
  suspend fun restoreSelected(): RestoredConnection?
  suspend fun loadAgents(profile: ServerProfile): List<Agent>
  suspend fun loadSessions(profile: ServerProfile, agentId: String): List<ChatSession>
  suspend fun createSession(profile: ServerProfile, agentId: String): ChatSession
  suspend fun loadMessages(profile: ServerProfile, agentId: String, sessionId: String): List<ChatTurn>
  fun openChat(profile: ServerProfile, agentId: String, sessionId: String): ChatConnection
  suspend fun selectServer(serverId: String)
  suspend fun selectAgent(serverId: String, agentId: String)
  suspend fun selectSession(serverId: String, agentId: String, sessionId: String)
  suspend fun logout(serverId: String)
}

class DefaultThyraRepository(
  private val service: MemohService,
  private val selections: SelectionStore,
  private val credentials: CredentialStore,
) : ThyraRepository {
  constructor(context: Context) : this(
    service = MemohApiClient(),
    selections = DataStoreSelectionStore(context),
    credentials = KeystoreCredentialStore(context),
  )

  override val persistedState: Flow<PersistedState> = selections.state

  override suspend fun addServer(displayName: String, address: String): ServerValidation {
    val (baseUrl, capabilities) = service.discover(address)
    val currentState = selections.state.first()
    val existing = currentState.profiles.firstOrNull { it.baseUrl == baseUrl }
    val resolvedName = displayName.trim().ifBlank { existing?.displayName ?: URI(baseUrl).host }
    val profile = existing?.copy(displayName = resolvedName, lastUsedAt = System.currentTimeMillis())
      ?: ServerProfile(
        id = UUID.randomUUID().toString(),
        displayName = resolvedName,
        baseUrl = baseUrl,
      )
    selections.update { current ->
      current.copy(
        profiles = current.profiles.filterNot { it.id == profile.id || it.baseUrl == baseUrl } + profile,
        selectedServerId = profile.id,
      )
    }
    return ServerValidation(profile, capabilities)
  }

  override suspend fun authenticateWithPassword(profile: ServerProfile, identity: String, password: String): Account {
    val auth = service.login(profile.baseUrl, identity, password)
    credentials.put(profile.id, StoredCredential(auth.accessToken, auth.expiresAt))
    updateAuthMode(profile.id, AuthMode.Credentials)
    return service.me(profile.baseUrl, auth.accessToken)
  }

  override suspend fun authenticateWithToken(profile: ServerProfile, token: String): Account {
    val trimmed = token.trim()
    require(trimmed.isNotEmpty()) { "请输入访问令牌" }
    val account = service.me(profile.baseUrl, trimmed)
    credentials.put(profile.id, StoredCredential(trimmed))
    updateAuthMode(profile.id, AuthMode.AccessToken)
    return account
  }

  override suspend fun restoreSelected(): RestoredConnection? {
    val state = selections.state.first()
    val profile = state.profiles.firstOrNull { it.id == state.selectedServerId } ?: return null
    val account = authorized(profile) { token -> service.me(profile.baseUrl, token) }
    return RestoredConnection(profile, account)
  }

  override suspend fun loadAgents(profile: ServerProfile): List<Agent> =
    authorized(profile) { token -> service.agents(profile.baseUrl, token) }

  override suspend fun loadSessions(profile: ServerProfile, agentId: String): List<ChatSession> =
    authorized(profile) { token -> service.sessions(profile.baseUrl, token, agentId) }

  override suspend fun createSession(profile: ServerProfile, agentId: String): ChatSession =
    authorized(profile) { token -> service.createSession(profile.baseUrl, token, agentId) }

  override suspend fun loadMessages(
    profile: ServerProfile,
    agentId: String,
    sessionId: String,
  ): List<ChatTurn> = authorized(profile) { token -> service.messages(profile.baseUrl, token, agentId, sessionId) }

  override fun openChat(profile: ServerProfile, agentId: String, sessionId: String): ChatConnection {
    if (credentials.get(profile.id) == null) throw AuthenticationExpiredException()
    // The provider is evaluated by each WebSocket handshake, so reconnects use
    // a JWT refreshed by a concurrent REST request without coupling network to
    // the Android credential implementation.
    return service.openChatSocket(profile.baseUrl, { credentials.get(profile.id)?.token }, agentId, sessionId)
  }

  override suspend fun selectServer(serverId: String) {
    selections.update { it.copy(selectedServerId = serverId) }
  }

  override suspend fun selectAgent(serverId: String, agentId: String) {
    selections.update { it.copy(selectedAgentByServer = it.selectedAgentByServer + (serverId to agentId)) }
  }

  override suspend fun selectSession(serverId: String, agentId: String, sessionId: String) {
    selections.update {
      it.copy(selectedSessionByAgent = it.selectedSessionByAgent + (it.sessionKey(serverId, agentId) to sessionId))
    }
  }

  override suspend fun logout(serverId: String) {
    credentials.remove(serverId)
  }

  private suspend fun updateAuthMode(serverId: String, mode: AuthMode) {
    selections.update { current ->
      current.copy(
        profiles = current.profiles.map { profile ->
          if (profile.id == serverId) profile.copy(authMode = mode, lastUsedAt = System.currentTimeMillis()) else profile
        },
        selectedServerId = serverId,
      )
    }
  }

  private suspend fun <T> authorized(profile: ServerProfile, call: suspend (String) -> T): T {
    var stored = credentials.get(profile.id) ?: throw AuthenticationExpiredException()
    if (stored.isExpiringSoon()) {
      stored = refreshCredential(profile, stored)
    }
    try {
      return call(stored.token)
    } catch (failure: ApiException) {
      if (!failure.isUnauthorized) throw failure
    }

    val refreshed = refreshCredential(profile, stored)
    return try {
      call(refreshed.token)
    } catch (failure: ApiException) {
      if (failure.isUnauthorized) {
        credentials.remove(profile.id)
        throw AuthenticationExpiredException()
      }
      throw failure
    }
  }

  private suspend fun refreshCredential(profile: ServerProfile, stored: StoredCredential): StoredCredential {
    return try {
      val refreshed = service.refresh(profile.baseUrl, stored.token)
      StoredCredential(refreshed.accessToken, refreshed.expiresAt).also { credentials.put(profile.id, it) }
    } catch (failure: ApiException) {
      if (failure.isUnauthorized) {
        credentials.remove(profile.id)
        throw AuthenticationExpiredException()
      }
      throw failure
    }
  }
}

private fun StoredCredential.isExpiringSoon(): Boolean {
  val expiry = expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
  return expiry.isBefore(Instant.now().plusSeconds(300))
}
