package dev.thyra.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderedSocketEventsTest {
  private val json = Json

  @Test
  fun burstEvents_preserveArrivalOrder() = runTest {
    val queue = OrderedSocketEvents(capacity = 4)
    queue.send(event("first"))
    queue.send(event("second"))
    queue.send(event("terminal"))

    assertEquals(listOf("first", "second", "terminal"), queue.events.take(3).toList().map { it["type"]!!.toString().trim('"') })
  }

  @Test
  fun terminalEvent_waitsForCapacityInsteadOfBeingDropped() = runTest {
    val queue = OrderedSocketEvents(capacity = 1)
    queue.send(event("running"))
    val sender = launch(Dispatchers.Default) { queue.send(event("completed")) }

    val received = queue.events.take(2).toList()
    sender.join()

    assertEquals(listOf("running", "completed"), received.map { it["type"]!!.toString().trim('"') })
  }

  @Test
  fun close_endsEventFlow() = runTest {
    val queue = OrderedSocketEvents()
    queue.close()

    assertEquals(emptyList<Any>(), queue.events.toList())
  }

  private fun event(type: String) = json.parseToJsonElement("""{"type":"$type"}""").jsonObject
}
