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
import dev.thyra.core.network.RequestCredential
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class ServerValidation(val profile: ServerProfile, val capabilities: ServerCapabilities)
data class RestoredConnection(val profile: ServerProfile, val account: Account)

const val OFFICIAL_CLOUD_ID = "memoh-cloud"
const val OFFICIAL_CLOUD_ORIGIN = "https://app.memoh.net"
const val OFFICIAL_CLOUD_API = "$OFFICIAL_CLOUD_ORIGIN/api/memoh"
const val OFFICIAL_CLOUD_PLATFORM_API = "$OFFICIAL_CLOUD_ORIGIN/api/v1"

class AuthenticationExpiredException(message: String = "登录已过期，请重新登录") : IOException(message)

interface ThyraRepository {
  val persistedState: Flow<PersistedState>
  suspend fun addServer(displayName: String, address: String): ServerValidation
  suspend fun addOfficialCloud(): ServerProfile
  suspend fun sendCloudEmailCode(email: String)
  suspend fun authenticateOfficialCloud(email: String, code: String): Account
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

  override suspend fun addOfficialCloud(): ServerProfile {
    val currentState = selections.state.first()
    val existing = currentState.profiles.firstOrNull { it.id == OFFICIAL_CLOUD_ID }
    val profile = (existing ?: ServerProfile(
      id = OFFICIAL_CLOUD_ID,
      displayName = "Memoh Cloud",
      baseUrl = OFFICIAL_CLOUD_API,
      authMode = AuthMode.Cloud,
    )).copy(
      displayName = "Memoh Cloud",
      baseUrl = OFFICIAL_CLOUD_API,
      authMode = AuthMode.Cloud,
      lastUsedAt = System.currentTimeMillis(),
    )
    selections.update { current ->
      current.copy(
        profiles = current.profiles.filterNot { it.id == OFFICIAL_CLOUD_ID } + profile,
        selectedServerId = OFFICIAL_CLOUD_ID,
      )
    }
    return profile
  }

  override suspend fun sendCloudEmailCode(email: String) {
    val normalized = email.trim()
    require(normalized.isNotEmpty()) { "请输入邮箱" }
    service.sendCloudEmailCode(OFFICIAL_CLOUD_PLATFORM_API, normalized, Locale.getDefault().toLanguageTag())
  }

  override suspend fun authenticateOfficialCloud(email: String, code: String): Account {
    val normalizedEmail = email.trim()
    val normalizedCode = code.trim()
    require(normalizedEmail.isNotEmpty()) { "请输入邮箱" }
    require(normalizedCode.isNotEmpty()) { "请输入验证码" }
    val session = service.verifyCloudEmailCode(OFFICIAL_CLOUD_PLATFORM_API, normalizedEmail, normalizedCode)
    val teams = service.cloudTeams(OFFICIAL_CLOUD_PLATFORM_API, session.cookieHeader)
    val team = teams.firstOrNull() ?: throw IOException("此 Cloud 账号还没有可用团队")
    val account = service.cloudMe(OFFICIAL_CLOUD_PLATFORM_API, session.cookieHeader)
    credentials.put(
      OFFICIAL_CLOUD_ID,
      StoredCredential(sessionCookie = session.cookieHeader, teamId = team.id),
    )
    updateAuthMode(OFFICIAL_CLOUD_ID, AuthMode.Cloud)
    return account
  }

  override suspend fun authenticateWithPassword(profile: ServerProfile, identity: String, password: String): Account {
    val auth = service.login(profile.baseUrl, identity, password)
    credentials.put(profile.id, StoredCredential(auth.accessToken, auth.expiresAt))
    updateAuthMode(profile.id, AuthMode.Credentials)
    return service.me(profile.baseUrl, RequestCredential.Bearer(auth.accessToken))
  }

  override suspend fun authenticateWithToken(profile: ServerProfile, token: String): Account {
    val trimmed = token.trim()
    require(trimmed.isNotEmpty()) { "请输入访问令牌" }
    val account = service.me(profile.baseUrl, RequestCredential.Bearer(trimmed))
    credentials.put(profile.id, StoredCredential(trimmed))
    updateAuthMode(profile.id, AuthMode.AccessToken)
    return account
  }

  override suspend fun restoreSelected(): RestoredConnection? {
    val state = selections.state.first()
    val profile = state.profiles.firstOrNull { it.id == state.selectedServerId } ?: return null
    val account = if (profile.authMode == AuthMode.Cloud) {
      cloudAuthorized(profile) { credential -> service.cloudMe(credential.platformBaseUrl, credential.cookieHeader) }
    } else {
      authorized(profile) { credential -> service.me(profile.baseUrl, credential) }
    }
    return RestoredConnection(profile, account)
  }

  override suspend fun loadAgents(profile: ServerProfile): List<Agent> =
    authorized(profile) { credential -> service.agents(profile.baseUrl, credential) }

  override suspend fun loadSessions(profile: ServerProfile, agentId: String): List<ChatSession> =
    authorized(profile) { credential -> service.sessions(profile.baseUrl, credential, agentId) }
      .distinctBy(ChatSession::id)

  override suspend fun createSession(profile: ServerProfile, agentId: String): ChatSession =
    authorized(profile) { credential -> service.createSession(profile.baseUrl, credential, agentId) }

  override suspend fun loadMessages(
    profile: ServerProfile,
    agentId: String,
    sessionId: String,
  ): List<ChatTurn> = authorized(profile) { credential -> service.messages(profile.baseUrl, credential, agentId, sessionId) }

  override fun openChat(profile: ServerProfile, agentId: String, sessionId: String): ChatConnection {
    if (credentials.get(profile.id) == null) throw AuthenticationExpiredException()
    // The provider is evaluated by every WebSocket handshake, so self-hosted
    // reconnects see refreshed JWTs and Cloud reconnects request a ticket from
    // the current encrypted session without coupling network to Android storage.
    return service.openChatSocket(profile.baseUrl, { requestCredential(profile) }, agentId, sessionId)
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

  private suspend fun <T> authorized(profile: ServerProfile, call: suspend (RequestCredential) -> T): T {
    if (profile.authMode == AuthMode.Cloud) return cloudAuthorized(profile, call)
    var stored = credentials.get(profile.id) ?: throw AuthenticationExpiredException()
    if (stored.isExpiringSoon()) {
      stored = refreshCredential(profile, stored)
    }
    val token = stored.token ?: throw AuthenticationExpiredException()
    try {
      return call(RequestCredential.Bearer(token))
    } catch (failure: ApiException) {
      if (!failure.isUnauthorized) throw failure
    }

    val refreshed = refreshCredential(profile, stored)
    return try {
      call(RequestCredential.Bearer(refreshed.token ?: throw AuthenticationExpiredException()))
    } catch (failure: ApiException) {
      if (failure.isUnauthorized) {
        credentials.remove(profile.id)
        throw AuthenticationExpiredException()
      }
      throw failure
    }
  }

  private suspend fun refreshCredential(profile: ServerProfile, stored: StoredCredential): StoredCredential {
    val token = stored.token ?: throw AuthenticationExpiredException()
    return try {
      val refreshed = service.refresh(profile.baseUrl, token)
      StoredCredential(refreshed.accessToken, refreshed.expiresAt).also { credentials.put(profile.id, it) }
    } catch (failure: ApiException) {
      if (failure.isUnauthorized) {
        credentials.remove(profile.id)
        throw AuthenticationExpiredException()
      }
      throw failure
    }
  }

  private suspend fun <T> cloudAuthorized(
    profile: ServerProfile,
    call: suspend (RequestCredential.Cloud) -> T,
  ): T {
    val credential = requestCredential(profile) as? RequestCredential.Cloud ?: throw AuthenticationExpiredException()
    return try {
      call(credential)
    } catch (failure: ApiException) {
      if (failure.isUnauthorized) {
        credentials.remove(profile.id)
        throw AuthenticationExpiredException()
      }
      throw failure
    }
  }

  private fun requestCredential(profile: ServerProfile): RequestCredential? {
    val stored = credentials.get(profile.id) ?: return null
    return if (profile.authMode == AuthMode.Cloud) {
      val cookie = stored.sessionCookie?.takeIf { it.isNotBlank() } ?: return null
      val teamId = stored.teamId?.takeIf { it.isNotBlank() } ?: return null
      RequestCredential.Cloud(cookie, teamId, OFFICIAL_CLOUD_PLATFORM_API)
    } else {
      stored.token?.takeIf { it.isNotBlank() }?.let { RequestCredential.Bearer(it) }
    }
  }
}

private fun StoredCredential.isExpiringSoon(): Boolean {
  val expiry = expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
  return expiry.isBefore(Instant.now().plusSeconds(300))
}
