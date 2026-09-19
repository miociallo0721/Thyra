package dev.thyra.core.network

import java.util.concurrent.TimeUnit
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
      tokenProvider = { token },
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
}
