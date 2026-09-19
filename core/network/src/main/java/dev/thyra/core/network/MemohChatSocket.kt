package dev.thyra.core.network

import dev.thyra.core.model.RuntimeCursor
import dev.thyra.core.model.SocketStatus
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

interface ChatConnection : AutoCloseable {
  val events: Flow<JsonObject>
  val status: StateFlow<SocketStatus>
  fun updateCursor(value: RuntimeCursor?)
  fun requestSnapshot()
  fun sendMessage(text: String, invocationId: String = UUID.randomUUID().toString()): String
  fun abort(runId: String, controlId: String = UUID.randomUUID().toString())
}

class MemohChatSocket internal constructor(
  private val http: OkHttpClient,
  private val json: Json,
  private val baseUrl: String,
  private val tokenProvider: () -> String?,
  private val botId: String,
  private val sessionId: String,
) : ChatConnection {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val closed = AtomicBoolean(false)
  private val lock = Any()
  private val reliable = linkedMapOf<String, String>()
  private var socket: WebSocket? = null
  private var reconnectJob: Job? = null
  private var reconnectDelayMillis = 1_000L
  private var cursor: RuntimeCursor? = null

  // WebSocket callbacks are ordered. Keep that order with a bounded channel and
  // apply backpressure to the callback thread instead of dropping a frame. This
  // avoids an unbounded coroutine backlog while making a slow UI consumer slow
  // its own socket rather than silently losing a terminal runtime delta.
  private val eventQueue = OrderedSocketEvents()
  override val events: Flow<JsonObject> = eventQueue.events

  private val _status = MutableStateFlow(SocketStatus.Connecting)
  override val status: StateFlow<SocketStatus> = _status

  init { connect() }

  override fun updateCursor(value: RuntimeCursor?) {
    cursor = value
  }

  override fun requestSnapshot() {
    cursor = null
    sendSubscription()
  }

  override fun sendMessage(text: String, invocationId: String): String {
    val payload = buildJsonObject {
      put("type", "message")
      put("invocation_id", invocationId)
      put("session_id", sessionId)
      put("text", text)
    }.toString()
    synchronized(lock) {
      reliable["invocation:$invocationId"] = payload
      socket?.send(payload)
    }
    return invocationId
  }

  override fun abort(runId: String, controlId: String) {
    val payload = buildJsonObject {
      put("type", "abort")
      put("run_id", runId)
      put("session_id", sessionId)
      put("control_id", controlId)
    }.toString()
    synchronized(lock) {
      reliable["control:$controlId"] = payload
      socket?.send(payload)
    }
  }

  private fun connect() {
    if (closed.get()) return
    val token = tokenProvider()?.trim().orEmpty()
    if (token.isEmpty()) {
      _status.value = SocketStatus.Expired
      return
    }
    _status.value = if (socket == null) SocketStatus.Connecting else SocketStatus.Reconnecting
    val request = Request.Builder()
      .url(baseUrl.trimEnd('/') + "/bots/$botId/web/ws")
      .header("Authorization", "Bearer $token")
      .build()
    socket = http.newWebSocket(request, Listener(token))
  }

  private fun sendSubscription() {
    val value = cursor
    val payload = buildJsonObject {
      put("type", "runtime_subscribe")
      put("session_id", sessionId)
      if (value != null) {
        put("cursor", buildJsonObject {
          put("epoch", value.epoch)
          put("seq", value.sequence)
        })
      }
    }.toString()
    synchronized(lock) { socket?.send(payload) }
  }

  private fun scheduleReconnect(webSocket: WebSocket) {
    synchronized(lock) {
      if (socket !== webSocket) return
      socket = null
    }
    if (closed.get() || _status.value == SocketStatus.Expired) return
    _status.value = SocketStatus.Reconnecting
    reconnectJob?.cancel()
    reconnectJob = scope.launch {
      delay(reconnectDelayMillis)
      reconnectDelayMillis = min((reconnectDelayMillis * 1.5).toLong(), 10_000L)
      connect()
    }
  }

  private inner class Listener(private val handshakeToken: String) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      if (!isActive(webSocket)) return
      reconnectDelayMillis = 1_000L
      _status.value = SocketStatus.Connected
      synchronized(lock) { reliable.values.forEach(webSocket::send) }
      sendSubscription()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      if (!isActive(webSocket)) return
      val event = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
      acknowledge(event)
      try {
        eventQueue.send(event)
      } catch (_: ClosedSendChannelException) {
        // close() ended the consumer; no event is silently discarded while a
        // connection remains active.
      }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      webSocket.close(code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      scheduleReconnect(webSocket)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      if (!isActive(webSocket)) return
      if (response?.code == 401) {
        // A REST refresh can finish between this handshake and its response.
        // Retry with that newer credential before declaring the session expired.
        if (tokenProvider()?.trim() != handshakeToken) {
          scheduleReconnect(webSocket)
        } else {
          synchronized(lock) {
            if (socket === webSocket) socket = null
          }
          _status.value = SocketStatus.Expired
        }
      } else {
        scheduleReconnect(webSocket)
      }
    }
  }

  private fun acknowledge(event: JsonObject) {
    val type = event["type"]?.jsonPrimitive?.content ?: return
    val key = when (type) {
      "run_accepted", "run_rejected", "command_result", "command_error", "error" ->
        event["invocation_id"]?.jsonPrimitive?.contentOrNull?.let { "invocation:$it" }
      "control_ack" -> event["control_id"]?.jsonPrimitive?.contentOrNull?.let { "control:$it" }
      else -> null
    }
    if (key != null) synchronized(lock) { reliable.remove(key) }
  }

  private fun isActive(webSocket: WebSocket): Boolean = synchronized(lock) { socket === webSocket && !closed.get() }

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    reconnectJob?.cancel()
    synchronized(lock) {
      reliable.clear()
      socket?.close(1000, "screen closed")
      socket = null
    }
    eventQueue.close()
    _status.value = SocketStatus.Disconnected
  }
}

/** A bounded, ordered handoff from OkHttp callbacks to the sole UI collector. */
internal class OrderedSocketEvents(capacity: Int = 64) {
  private val channel = Channel<JsonObject>(capacity)
  val events: Flow<JsonObject> = channel.receiveAsFlow()

  fun send(event: JsonObject) = runBlocking { channel.send(event) }

  fun close() = channel.close()
}
