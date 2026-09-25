package dev.thyra.core.network

import dev.thyra.core.model.SocketStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class MemohChatSocketTest {
  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun reconnect_usesTokenProvidedAfterPreviousHandshake() {
    var token = "old-token"
    server.enqueue(
      MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          token = "new-token"
          webSocket.close(1000, "reconnect")
        }
      }),
    )
    server.enqueue(
      MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          webSocket.close(1000, "done")
        }
      }),
    )
    val connection = MemohChatSocket(
      http = OkHttpClient(),
      json = Json,
      baseUrl = server.url("/").toString().trimEnd('/'),
      credentialProvider = { RequestCredential.Bearer(token) },
      botId = "bot",
      sessionId = "session",
    )

    val first = server.takeRequest(2, TimeUnit.SECONDS)
    val second = server.takeRequest(3, TimeUnit.SECONDS)
    connection.close()

    assertNotNull(first)
    assertNotNull(second)
    assertEquals("Bearer old-token", first!!.getHeader("Authorization"))
    assertEquals("Bearer new-token", second!!.getHeader("Authorization"))
  }

  @Test
  fun cloudReconnect_requestsFreshTicketForEveryHandshake() {
    server.enqueue(jsonResponse("""{"ticket":"ticket-1"}"""))
    server.enqueue(
      MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          webSocket.close(1000, "reconnect")
        }
      }),
    )
    server.enqueue(jsonResponse("""{"ticket":"ticket-2"}"""))
    server.enqueue(
      MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          webSocket.close(1000, "done")
        }
      }),
    )
    val root = server.url("/").toString().trimEnd('/')
    val connection = MemohChatSocket(
      http = OkHttpClient(),
      json = Json,
      baseUrl = "$root/api/memoh",
      credentialProvider = { RequestCredential.Cloud("session=cookie", "team-1", "$root/api/v1") },
      botId = "bot",
      sessionId = "session",
    )

    val ticket1 = server.takeRequest(2, TimeUnit.SECONDS)
    val socket1 = server.takeRequest(2, TimeUnit.SECONDS)
    val ticket2 = server.takeRequest(4, TimeUnit.SECONDS)
    val socket2 = server.takeRequest(2, TimeUnit.SECONDS)
    connection.close()

    assertEquals("/api/v1/ws-tickets", ticket1?.path)
    assertEquals("session=cookie", ticket1?.getHeader("Cookie"))
    assertEquals("team-1", ticket1?.getHeader("X-Team-Id"))
    assertEquals(root, ticket1?.getHeader("Origin"))
    assertEquals(null, ticket1?.getHeader("Content-Type"))
    assertEquals("/api/memoh/bots/bot/web/ws?team_id=team-1&ticket=ticket-1", socket1?.path)
    assertEquals("/api/v1/ws-tickets", ticket2?.path)
    assertEquals("/api/memoh/bots/bot/web/ws?team_id=team-1&ticket=ticket-2", socket2?.path)
  }

  @Test
  fun cloudTicket403StopsReconnectAndExposesForbiddenStatus() {
    server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"forbidden"}"""))
    val root = server.url("/").toString().trimEnd('/')
    val connection = MemohChatSocket(
      http = OkHttpClient(),
      json = Json,
      baseUrl = "$root/api/memoh",
      credentialProvider = { RequestCredential.Cloud("session=cookie", "team-1", "$root/api/v1") },
      botId = "bot",
      sessionId = "session",
    )

    try {
      val request = server.takeRequest(2, TimeUnit.SECONDS)
      runBlocking { withTimeout(2_000) { connection.status.first { it == SocketStatus.Forbidden } } }

      assertEquals("/api/v1/ws-tickets", request?.path)
      assertEquals(SocketStatus.Forbidden, connection.status.value)
      assertEquals(null, server.takeRequest(2, TimeUnit.SECONDS))
    } finally {
      connection.close()
    }
  }

  private fun jsonResponse(body: String) = MockResponse()
    .setResponseCode(200)
    .setHeader("Content-Type", "application/json")
    .setBody(body)
}
