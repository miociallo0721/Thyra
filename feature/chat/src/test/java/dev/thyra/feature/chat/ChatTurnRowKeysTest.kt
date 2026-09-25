package dev.thyra.feature.chat

import dev.thyra.core.model.ChatRole
import dev.thyra.core.model.ChatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatTurnRowKeysTest {
  @Test
  fun repeatedTurnIdsKeepEveryRowWithDistinctStableKeys() {
    val turns = listOf(
      ChatTurn("shared", ChatRole.User, "first"),
      ChatTurn("other", ChatRole.Assistant, "middle"),
      ChatTurn("shared", ChatRole.Assistant, "last"),
    )

    val keys = chatTurnRowKeys("session-a", turns)

    assertEquals(turns.size, keys.size)
    assertEquals(turns.size, keys.toSet().size)
    assertEquals(keys, chatTurnRowKeys("session-a", turns.map { it.copy(text = "updated") }))
    assertEquals(keys[0], chatTurnRowKeys("session-a", turns.take(1))[0])
    assertNotEquals(keys, chatTurnRowKeys("session-b", turns))
  }

  @Test
  fun lengthPrefixesPreventDelimiterAndSpacerCollisions() {
    val turns = listOf(
      ChatTurn("a:0", ChatRole.User),
      ChatTurn("a", ChatRole.User),
      ChatTurn("a", ChatRole.User),
      ChatTurn("top-space", ChatRole.User),
      ChatTurn("", ChatRole.User),
    )

    val keys = chatTurnRowKeys("session", turns)

    assertEquals(turns.size, keys.toSet().size)
    assertNotEquals("top-space", keys[3])
    assertNotEquals("bottom-space", keys[4])
  }
}
