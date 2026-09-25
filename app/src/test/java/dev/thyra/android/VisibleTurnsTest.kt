package dev.thyra.android

import dev.thyra.core.model.ChatRole
import dev.thyra.core.model.ChatTurn
import dev.thyra.core.model.LiveChatState
import dev.thyra.core.model.ThyraUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleTurnsTest {
  @Test
  fun liveTurnReplacesOnlyLatestMatchingHistoryTurn() {
    val earlier = ChatTurn("same", ChatRole.User, "earlier")
    val settled = ChatTurn("same", ChatRole.Assistant, "settled")
    val live = ChatTurn("same", ChatRole.Assistant, "streaming", isPending = true)
    val state = ThyraUiState(
      history = listOf(earlier, settled),
      live = LiveChatState(activeTurn = live),
    )

    assertEquals(listOf(earlier, live), state.visibleTurns)
    assertEquals(listOf(earlier, settled), state.copy(live = LiveChatState()).visibleTurns)
  }
}
