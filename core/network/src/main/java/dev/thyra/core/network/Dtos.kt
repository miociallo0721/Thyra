package dev.thyra.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable internal data class CloudEmailCodeRequestDto(
  val email: String,
  @SerialName("preferred_locale") val preferredLocale: String,
)

@Serializable internal data class CloudEmailCodeVerifyDto(val email: String, val code: String)

@Serializable internal data class CloudTeamListDto(val teams: List<CloudTeamMembershipDto> = emptyList())

@Serializable internal data class CloudTeamMembershipDto(
  val team: CloudTeamDto = CloudTeamDto(),
  val role: String = "",
)

@Serializable internal data class CloudTeamDto(
  @SerialName("team_id") val teamId: String = "",
  val name: String = "",
  val slug: String = "",
)

@Serializable internal data class WebSocketTicketDto(val ticket: String)

@Serializable internal data class PingDto(
  val status: String = "",
  val version: String? = null,
  @SerialName("commit_hash") val commitHash: String? = null,
  @SerialName("container_backend") val containerBackend: String? = null,
)

@Serializable internal data class LoginRequestDto(val username: String, val password: String)

@Serializable internal data class LoginResponseDto(
  @SerialName("access_token") val accessToken: String,
  @SerialName("expires_at") val expiresAt: String? = null,
  @SerialName("token_type") val tokenType: String = "Bearer",
)

@Serializable internal data class AccountDto(
  val id: String = "",
  val username: String = "",
  @SerialName("display_name") val displayName: String = "",
  @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable internal data class BotListDto(val items: List<BotDto> = emptyList())

@Serializable internal data class BotDto(
  val id: String = "",
  val name: String = "",
  @SerialName("display_name") val displayName: String = "",
  @SerialName("avatar_url") val avatarUrl: String? = null,
  val status: String = "unknown",
  @SerialName("is_active") val isActive: Boolean = true,
  @SerialName("current_user_permissions") val permissions: List<String> = emptyList(),
)

@Serializable internal data class SessionListDto(
  val items: List<SessionDto> = emptyList(),
  @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable internal data class SessionDto(
  val id: String = "",
  @SerialName("bot_id") val botId: String = "",
  @SerialName("bot_agent_id") val botAgentId: String? = null,
  val title: String = "",
  val type: String? = null,
  @SerialName("updated_at") val updatedAt: String? = null,
  @SerialName("created_at") val createdAt: String? = null,
)

@Serializable internal data class CreateSessionDto(
  val title: String = "",
  @SerialName("channel_type") val channelType: String = "local",
  val type: String = "chat",
  @SerialName("bot_agent_id") val botAgentId: String? = null,
)

@Serializable internal data class TurnListDto(val items: List<TurnDto> = emptyList())

@Serializable internal data class TurnDto(
  @SerialName("turn_id") val turnId: String,
  @SerialName("turn_position") val turnPosition: Long? = null,
  val role: String,
  val text: String = "",
  val messages: List<MessageDto> = emptyList(),
  val timestamp: String? = null,
)

@Serializable internal data class MessageDto(
  val id: Int? = null,
  val type: String = "text",
  val content: String = "",
  val name: String? = null,
  val input: JsonElement? = null,
  val output: JsonElement? = null,
  val running: Boolean = false,
  @SerialName("tool_call_id") val toolCallId: String? = null,
  @SerialName("elapsed_time_seconds") val elapsedTimeSeconds: Double? = null,
  @SerialName("reasoning_timing") val reasoningTiming: ReasoningTimingDto? = null,
)

@Serializable internal data class ReasoningTimingDto(@SerialName("duration_ms") val durationMs: Long? = null)

@Serializable internal data class ApiErrorDto(
  val code: String? = null,
  val message: String? = null,
  val reason: String? = null,
  val detail: String? = null,
)
