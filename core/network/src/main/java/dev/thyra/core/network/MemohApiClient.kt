package dev.thyra.core.network

import dev.thyra.core.model.Account
import dev.thyra.core.model.Agent
import dev.thyra.core.model.ChatBlock
import dev.thyra.core.model.ChatRole
import dev.thyra.core.model.ChatSession
import dev.thyra.core.model.ChatTurn
import dev.thyra.core.model.ServerCapabilities
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class AuthCredential(val accessToken: String, val expiresAt: String? = null)

interface MemohService {
  suspend fun discover(input: String): Pair<String, ServerCapabilities>
  suspend fun ping(baseUrl: String): ServerCapabilities
  suspend fun login(baseUrl: String, username: String, password: String): AuthCredential
  suspend fun refresh(baseUrl: String, token: String): AuthCredential
  suspend fun me(baseUrl: String, token: String): Account
  suspend fun agents(baseUrl: String, token: String): List<Agent>
  suspend fun sessions(baseUrl: String, token: String, botId: String): List<ChatSession>
  suspend fun createSession(baseUrl: String, token: String, botId: String, title: String = ""): ChatSession
  suspend fun messages(baseUrl: String, token: String, botId: String, sessionId: String): List<ChatTurn>
  fun openChatSocket(baseUrl: String, token: String, botId: String, sessionId: String): ChatConnection
}

class ApiException(
  val statusCode: Int,
  val errorCode: String? = null,
  message: String,
) : IOException(message) {
  val isUnauthorized: Boolean get() = statusCode == 401
}

class MemohApiClient(
  private val http: OkHttpClient = defaultHttpClient(),
  private val json: Json = defaultJson(),
) : MemohService {
  override suspend fun discover(input: String): Pair<String, ServerCapabilities> {
    var lastFailure: Throwable? = null
    for (candidate in UrlNormalizer.discoveryCandidates(input)) {
      try {
        return candidate to ping(candidate)
      } catch (failure: Throwable) {
        lastFailure = failure
      }
    }
    throw lastFailure ?: IOException("无法连接到 Memoh 服务器")
  }

  override suspend fun ping(baseUrl: String): ServerCapabilities {
    val response = execute(Request.Builder().url(endpoint(baseUrl, "/ping")).get().build())
    response.use {
      ensureSuccess(it)
      val dto = json.decodeFromString<PingDto>(it.body.string())
      if (dto.status != "ok") throw IOException("服务器没有返回 Memoh 健康状态")
      return ServerCapabilities(dto.version, dto.commitHash, dto.containerBackend)
    }
  }

  override suspend fun login(baseUrl: String, username: String, password: String): AuthCredential {
    val body = json.encodeToString(LoginRequestDto(username.trim(), password))
    val response = execute(
      Request.Builder()
        .url(endpoint(baseUrl, "/auth/login"))
        .post(body.toRequestBody(JSON_MEDIA_TYPE))
        .build(),
    )
    response.use {
      ensureSuccess(it)
      val dto = json.decodeFromString<LoginResponseDto>(it.body.string())
      return AuthCredential(dto.accessToken, dto.expiresAt)
    }
  }

  override suspend fun refresh(baseUrl: String, token: String): AuthCredential {
    val response = execute(authorized(Request.Builder().url(endpoint(baseUrl, "/auth/refresh")).post(EMPTY_BODY), token).build())
    response.use {
      ensureSuccess(it)
      val dto = json.decodeFromString<LoginResponseDto>(it.body.string())
      return AuthCredential(dto.accessToken, dto.expiresAt)
    }
  }

  override suspend fun me(baseUrl: String, token: String): Account {
    val response = execute(authorized(Request.Builder().url(endpoint(baseUrl, "/users/me")).get(), token).build())
    response.use {
      ensureSuccess(it)
      val dto = json.decodeFromString<AccountDto>(it.body.string())
      return Account(dto.id, dto.username, dto.displayName.ifBlank { dto.username }, dto.avatarUrl)
    }
  }

  override suspend fun agents(baseUrl: String, token: String): List<Agent> {
    val response = execute(authorized(Request.Builder().url(endpoint(baseUrl, "/bots")).get(), token).build())
    response.use {
      ensureSuccess(it)
      return json.decodeFromString<BotListDto>(it.body.string()).items.map { bot ->
        Agent(
          id = bot.id,
          name = bot.name,
          displayName = bot.displayName.ifBlank { bot.name },
          avatarUrl = bot.avatarUrl,
          status = bot.status,
          enabled = bot.isActive,
          permissions = bot.permissions.toSet(),
        )
      }
    }
  }

  override suspend fun sessions(baseUrl: String, token: String, botId: String): List<ChatSession> {
    val url = endpoint(baseUrl, "/bots/$botId/sessions") + "?types=chat,discuss,acp_agent&limit=100"
    val response = execute(authorized(Request.Builder().url(url).get(), token).build())
    response.use {
      ensureSuccess(it)
      return json.decodeFromString<SessionListDto>(it.body.string()).items.map(::toSession)
    }
  }

  override suspend fun createSession(baseUrl: String, token: String, botId: String, title: String): ChatSession {
    val requestBody = json.encodeToString(CreateSessionDto(title = title))
    val response = execute(
      authorized(Request.Builder().url(endpoint(baseUrl, "/bots/$botId/sessions")), token)
        .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
        .build(),
    )
    response.use {
      ensureSuccess(it)
      return toSession(json.decodeFromString<SessionDto>(it.body.string()))
    }
  }

  override suspend fun messages(baseUrl: String, token: String, botId: String, sessionId: String): List<ChatTurn> {
    val url = endpoint(baseUrl, "/bots/$botId/messages") + "?session_id=$sessionId&limit=100"
    val response = execute(authorized(Request.Builder().url(url).get(), token).build())
    response.use {
      ensureSuccess(it)
      return json.decodeFromString<TurnListDto>(it.body.string()).items.map(::toTurn)
    }
  }

  override fun openChatSocket(
    baseUrl: String,
    token: String,
    botId: String,
    sessionId: String,
  ): ChatConnection = MemohChatSocket(http, json, baseUrl, token, botId, sessionId)

  private suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) { http.newCall(request).execute() }

  private fun ensureSuccess(response: Response) {
    if (response.isSuccessful) return
    val body = runCatching { response.body.string() }.getOrDefault("")
    val problem = runCatching { json.decodeFromString<ApiErrorDto>(body) }.getOrNull()
    val message = problem?.message ?: problem?.detail ?: problem?.reason ?: when (response.code) {
      401 -> "登录已过期，请重新登录"
      403 -> "当前账号没有执行此操作的权限"
      404 -> "服务器不支持请求的接口"
      else -> "服务器返回 HTTP ${response.code}"
    }
    throw ApiException(response.code, problem?.code, message)
  }

  private fun toSession(dto: SessionDto) = ChatSession(
    id = dto.id,
    agentId = dto.botId,
    botAgentId = dto.botAgentId,
    title = dto.title.ifBlank { "新对话" },
    type = dto.type ?: "chat",
    updatedAt = dto.updatedAt,
    createdAt = dto.createdAt,
  )

  private fun toTurn(dto: TurnDto): ChatTurn {
    val role = when (dto.role) {
      "user" -> ChatRole.User
      "assistant" -> ChatRole.Assistant
      else -> ChatRole.System
    }
    val blocks = if (role == ChatRole.User) emptyList() else dto.messages.mapNotNull(::toBlock)
    return ChatTurn(dto.turnId, role, dto.text, blocks, dto.timestamp, dto.turnPosition)
  }

  private fun toBlock(message: MessageDto): ChatBlock? = when (message.type) {
    "text" -> ChatBlock.Text(message.content, message.id)
    "reasoning" -> ChatBlock.Reasoning(message.content, message.id, message.reasoningTiming?.durationMs)
    "tool", "tool_call" -> ChatBlock.Tool(
      id = message.id ?: 0,
      name = message.name ?: "tool",
      input = message.input?.toString(),
      output = message.output?.toString(),
      running = message.running,
      failed = message.output?.toString()?.contains("error", ignoreCase = true) == true,
      elapsedSeconds = message.elapsedTimeSeconds,
    )
    "error" -> ChatBlock.Notice(message.content, isError = true)
    "notice", "status", "command" -> ChatBlock.Notice(message.content)
    else -> null
  }

  companion object {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val EMPTY_BODY = ByteArray(0).toRequestBody(JSON_MEDIA_TYPE)

    fun defaultJson() = Json {
      ignoreUnknownKeys = true
      explicitNulls = false
      encodeDefaults = true
    }

    fun defaultHttpClient() = OkHttpClient.Builder()
      .retryOnConnectionFailure(true)
      .pingInterval(java.time.Duration.ofSeconds(20))
      .callTimeout(java.time.Duration.ofSeconds(30))
      .build()

    private fun endpoint(baseUrl: String, path: String) = baseUrl.trimEnd('/') + path
    private fun authorized(builder: Request.Builder, token: String) = builder.header("Authorization", "Bearer $token")
  }
}
