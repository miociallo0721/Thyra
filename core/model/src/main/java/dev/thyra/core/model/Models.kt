package dev.thyra.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerProfile(
  val id: String,
  val displayName: String,
  val baseUrl: String,
  val authMode: AuthMode = AuthMode.Credentials,
  val lastUsedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class AuthMode { Credentials, AccessToken, Cloud }

data class ServerCapabilities(
  val version: String? = null,
  val commitHash: String? = null,
  val containerBackend: String? = null,
)

data class Account(
  val id: String,
  val username: String,
  val displayName: String,
  val avatarUrl: String? = null,
)

data class Agent(
  val id: String,
  val name: String,
  val displayName: String,
  val avatarUrl: String? = null,
  val status: String = "unknown",
  val enabled: Boolean = true,
  val permissions: Set<String> = emptySet(),
)

data class ChatSession(
  val id: String,
  val agentId: String,
  val botAgentId: String? = null,
  val title: String,
  val type: String = "chat",
  val updatedAt: String? = null,
  val createdAt: String? = null,
)

enum class ChatRole { User, Assistant, System }

sealed interface ChatBlock {
  data class Text(val content: String, val id: Int? = null) : ChatBlock
  data class Reasoning(val content: String, val id: Int? = null, val durationMs: Long? = null) : ChatBlock
  data class Tool(
    val id: Int,
    val name: String,
    val input: String? = null,
    val output: String? = null,
    val running: Boolean = false,
    val failed: Boolean = false,
    val elapsedSeconds: Double? = null,
    val toolCallId: String? = null,
  ) : ChatBlock
  data class Notice(val content: String, val isError: Boolean = false) : ChatBlock
}

data class ChatTurn(
  val id: String,
  val role: ChatRole,
  val text: String = "",
  val blocks: List<ChatBlock> = emptyList(),
  val timestamp: String? = null,
  val position: Long? = null,
  val isPending: Boolean = false,
)

enum class SocketStatus { Disconnected, Connecting, Connected, Reconnecting, Expired, Forbidden }

data class RuntimeCursor(val epoch: String, val sequence: Long)

data class LiveChatState(
  val cursor: RuntimeCursor? = null,
  val activeRunId: String? = null,
  val activeTurn: ChatTurn? = null,
  val runStatus: String? = null,
  val errorMessage: String? = null,
  val needsSnapshot: Boolean = false,
) {
  val isGenerating: Boolean
    get() = runStatus in setOf("admitting", "running", "waiting_decision", "aborting", "finishing")
}

sealed interface AppDestination {
  data object Connection : AppDestination
  data object Authentication : AppDestination
  data object Agents : AppDestination
  data object Sessions : AppDestination
  data object Chat : AppDestination
}

data class ThyraUiState(
  val restoring: Boolean = true,
  val destination: AppDestination = AppDestination.Connection,
  val profiles: List<ServerProfile> = emptyList(),
  val selectedProfile: ServerProfile? = null,
  val account: Account? = null,
  val serverCapabilities: ServerCapabilities? = null,
  val agents: List<Agent> = emptyList(),
  val selectedAgent: Agent? = null,
  val sessions: List<ChatSession> = emptyList(),
  val selectedSession: ChatSession? = null,
  val history: List<ChatTurn> = emptyList(),
  val live: LiveChatState = LiveChatState(),
  val socketStatus: SocketStatus = SocketStatus.Disconnected,
  val busy: Boolean = false,
  val cloudEmailCodeSent: Boolean = false,
  val errorMessage: String? = null,
) {
  val visibleTurns: List<ChatTurn>
    get() {
      val activeTurn = live.activeTurn ?: return history
      val replacedIndex = history.indexOfLast { it.id == activeTurn.id }
      return history.filterIndexed { index, _ -> index != replacedIndex } + activeTurn
    }
}
