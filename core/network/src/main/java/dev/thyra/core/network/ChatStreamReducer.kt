package dev.thyra.core.network

import dev.thyra.core.model.ChatBlock
import dev.thyra.core.model.ChatRole
import dev.thyra.core.model.ChatTurn
import dev.thyra.core.model.LiveChatState
import dev.thyra.core.model.RuntimeCursor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object ChatStreamReducer {
  fun reduce(state: LiveChatState, event: JsonObject): LiveChatState {
    return when (event.string("type")) {
      "run_accepted" -> state.copy(
        activeRunId = event.string("run_id"),
        runStatus = "admitting",
        errorMessage = null,
      )
      "run_rejected", "error" -> state.copy(
        runStatus = "errored",
        errorMessage = event.string("message") ?: "生成失败",
      )
      "runtime_snapshot" -> applySnapshot(state, event)
      "runtime_delta" -> applyDelta(state, event)
      "runtime_dropped" -> state.copy(needsSnapshot = true, errorMessage = event.string("message"))
      else -> state
    }
  }

  private fun applySnapshot(state: LiveChatState, event: JsonObject): LiveChatState {
    val epoch = event.string("epoch") ?: return state.copy(needsSnapshot = true)
    val seq = event.long("seq") ?: return state.copy(needsSnapshot = true)
    val snapshot = event.obj("snapshot") ?: return state.copy(needsSnapshot = true)
    val view = snapshot.obj("current_run_view")
    return state.copy(
      cursor = RuntimeCursor(epoch, seq),
      activeRunId = view?.string("run_id"),
      activeTurn = view?.let(::turnFromView),
      runStatus = view?.string("status"),
      errorMessage = view?.string("error"),
      needsSnapshot = false,
    )
  }

  private fun applyDelta(state: LiveChatState, event: JsonObject): LiveChatState {
    val epoch = event.string("epoch") ?: return state.copy(needsSnapshot = true)
    val seq = event.long("seq") ?: return state.copy(needsSnapshot = true)
    val previous = state.cursor
    if (previous == null || previous.epoch != epoch || seq != previous.sequence + 1) {
      return state.copy(needsSnapshot = true)
    }
    val delta = event.obj("delta") ?: return state.copy(cursor = RuntimeCursor(epoch, seq))
    val fullView = delta.obj("current_run_view")
    var turn = if (fullView != null) turnFromView(fullView) else state.activeTurn
    if (fullView == null && turn != null) {
      delta.array("message_appends")?.forEach { append ->
        val obj = append as? JsonObject ?: return@forEach
        turn = turn?.let { current -> current.copy(blocks = appendBlock(current.blocks, obj)) }
      }
      delta.array("message_upserts")?.forEach { upsert ->
        val obj = upsert as? JsonObject ?: return@forEach
        val block = blockFromMessage(obj) ?: return@forEach
        turn = turn?.let { current -> current.copy(blocks = upsertBlock(current.blocks, block)) }
      }
      if (delta.boolean("reset_messages") == true) turn = turn?.copy(blocks = emptyList())
    }
    val run = delta.obj("run")
    val status = fullView?.string("status") ?: run?.string("status") ?: state.runStatus
    return state.copy(
      cursor = RuntimeCursor(epoch, seq),
      activeRunId = fullView?.string("run_id") ?: run?.string("run_id") ?: state.activeRunId,
      activeTurn = turn,
      runStatus = status,
      errorMessage = run?.string("error") ?: state.errorMessage,
      needsSnapshot = false,
    )
  }

  private fun turnFromView(view: JsonObject): ChatTurn {
    val blocks = view.array("messages")?.mapNotNull { (it as? JsonObject)?.let(::blockFromMessage) }.orEmpty()
    return ChatTurn(
      id = view.string("turn_id") ?: view.string("run_id") ?: "live",
      role = ChatRole.Assistant,
      blocks = blocks,
      timestamp = view.string("started_at"),
      position = view.long("turn_position"),
      isPending = true,
    )
  }

  private fun blockFromMessage(message: JsonObject): ChatBlock? = when (message.string("type")) {
    "text" -> ChatBlock.Text(message.string("content").orEmpty(), message.int("id"))
    "reasoning" -> ChatBlock.Reasoning(
      message.string("content").orEmpty(),
      message.int("id"),
      message.obj("reasoning_timing")?.long("duration_ms"),
    )
    "tool", "tool_call" -> ChatBlock.Tool(
      id = message.int("id") ?: 0,
      name = message.string("name") ?: "tool",
      input = message["input"]?.takeUnless { it is JsonNull }?.toString(),
      output = message["output"]?.takeUnless { it is JsonNull }?.toString(),
      running = message.boolean("running") ?: false,
      failed = message["output"]?.toString()?.contains("error", true) == true,
      elapsedSeconds = message.double("elapsed_time_seconds"),
    )
    "error" -> ChatBlock.Notice(message.string("content").orEmpty(), true)
    "notice", "status", "command" -> ChatBlock.Notice(message.string("content").orEmpty())
    else -> null
  }

  private fun appendBlock(blocks: List<ChatBlock>, append: JsonObject): List<ChatBlock> {
    val id = append.int("id")
    val content = append.string("content").orEmpty()
    val index = blocks.indexOfFirst {
      (it is ChatBlock.Text && it.id == id) || (it is ChatBlock.Reasoning && it.id == id)
    }
    if (index < 0) {
      val block = if (append.string("type") == "reasoning") ChatBlock.Reasoning(content, id) else ChatBlock.Text(content, id)
      return blocks + block
    }
    return blocks.toMutableList().also { copy ->
      copy[index] = when (val current = copy[index]) {
        is ChatBlock.Text -> current.copy(content = current.content + content)
        is ChatBlock.Reasoning -> current.copy(content = current.content + content)
        else -> current
      }
    }
  }

  private fun upsertBlock(blocks: List<ChatBlock>, block: ChatBlock): List<ChatBlock> {
    val id = when (block) {
      is ChatBlock.Text -> block.id
      is ChatBlock.Reasoning -> block.id
      is ChatBlock.Tool -> block.id
      else -> null
    }
    if (id == null) return blocks + block
    val index = blocks.indexOfFirst {
      when (it) {
        is ChatBlock.Text -> it.id == id
        is ChatBlock.Reasoning -> it.id == id
        is ChatBlock.Tool -> it.id == id
        else -> false
      }
    }
    if (index < 0) return blocks + block
    return blocks.toMutableList().also { it[index] = block }
  }

  private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
  private fun JsonObject.long(key: String) = this[key]?.jsonPrimitive?.longOrNull
  private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull
  private fun JsonObject.double(key: String) = this[key]?.jsonPrimitive?.doubleOrNull
  private fun JsonObject.boolean(key: String) = this[key]?.jsonPrimitive?.booleanOrNull
  private fun JsonObject.obj(key: String) = this[key] as? JsonObject
  private fun JsonObject.array(key: String) = this[key] as? JsonArray
}
