package dev.thyra.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemohApiClientTest {
  private lateinit var server: MockWebServer
  private lateinit var client: MemohApiClient

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    client = MemohApiClient()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun loginAndMe_useCurrentMemohShapesAndBearerHeader() = runTest {
    server.enqueue(jsonResponse("""{"access_token":"jwt-value","expires_at":"2026-09-19T12:00:00Z"}"""))
    server.enqueue(jsonResponse("""{"id":"u1","username":"alice","display_name":"Alice"}"""))
    val baseUrl = server.url("/").toString().trimEnd('/')

    val auth = client.login(baseUrl, "alice", "secret")
    val account = client.me(baseUrl, auth.accessToken)

    assertEquals("jwt-value", auth.accessToken)
    assertEquals("Alice", account.displayName)
    val loginRequest = server.takeRequest()
    assertEquals("/auth/login", loginRequest.path)
    assertTrue(loginRequest.body.readUtf8().contains("\"username\":\"alice\""))
    val meRequest = server.takeRequest()
    assertEquals("Bearer jwt-value", meRequest.getHeader("Authorization"))
  }

  @Test
  fun agentsAndHistory_ignoreNewOptionalFields() = runTest {
    server.enqueue(jsonResponse("""{"items":[{"id":"b1","name":"shio","display_name":"Shio","status":"ready","is_active":true,"future_field":7}]}"""))
    server.enqueue(jsonResponse("""{"items":[{"turn_id":"t1","turn_position":1,"role":"assistant","timestamp":"2026-09-19T00:00:00Z","messages":[{"id":1,"type":"text","content":"hello"},{"id":2,"type":"tool","name":"search","running":false,"input":{"q":"repo"},"output":{"count":2}}]}]}"""))
    val baseUrl = server.url("/").toString().trimEnd('/')

    val agents = client.agents(baseUrl, "jwt")
    val turns = client.messages(baseUrl, "jwt", "b1", "s1")

    assertEquals("Shio", agents.single().displayName)
    assertEquals(2, turns.single().blocks.size)
  }

  @Test(expected = ApiException::class)
  fun unauthorized_isReportedAsApiException() = runTest {
    server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"auth.expired","message":"expired"}"""))
    client.me(server.url("/").toString().trimEnd('/'), "expired")
  }

  private fun jsonResponse(body: String) = MockResponse()
    .setResponseCode(200)
    .setHeader("Content-Type", "application/json")
    .setBody(body)
}
