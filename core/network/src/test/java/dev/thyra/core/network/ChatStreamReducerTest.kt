package dev.thyra.core.network

import dev.thyra.core.model.ChatBlock
import dev.thyra.core.model.LiveChatState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamReducerTest {
  private val json = Json

  @Test
  fun snapshotThenDelta_assemblesStreamingText() {
    val snapshot = event(
      """{"type":"runtime_snapshot","epoch":"e1","seq":4,"snapshot":{"current_run_view":{"run_id":"r1","turn_id":"t1","status":"running","messages":[{"id":1,"type":"text","content":"Hel"}]}}}""",
    )
    val delta = event(
      """{"type":"runtime_delta","epoch":"e1","seq":5,"delta":{"message_appends":[{"id":1,"type":"text","content":"lo"}],"run":{"run_id":"r1","status":"running"}}}""",
    )

    val afterSnapshot = ChatStreamReducer.reduce(LiveChatState(), snapshot)
    val afterDelta = ChatStreamReducer.reduce(afterSnapshot, delta)

    assertEquals("Hello", (afterDelta.activeTurn!!.blocks.single() as ChatBlock.Text).content)
    assertEquals(5, afterDelta.cursor!!.sequence)
    assertFalse(afterDelta.needsSnapshot)
  }

  @Test
  fun sequenceGap_requestsAuthoritativeSnapshotAndKeepsVisibleContent() {
    val initial = ChatStreamReducer.reduce(
      LiveChatState(),
      event("""{"type":"runtime_snapshot","epoch":"e1","seq":4,"snapshot":{"current_run_view":{"run_id":"r1","turn_id":"t1","status":"running","messages":[{"id":1,"type":"text","content":"visible"}]}}}"""),
    )

    val result = ChatStreamReducer.reduce(
      initial,
      event("""{"type":"runtime_delta","epoch":"e1","seq":7,"delta":{"message_appends":[{"id":1,"type":"text","content":"bad"}]}}"""),
    )

    assertTrue(result.needsSnapshot)
    assertEquals("visible", (result.activeTurn!!.blocks.single() as ChatBlock.Text).content)
  }

  private fun event(value: String) = json.parseToJsonElement(value).jsonObject
}
