package dev.thyra.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UrlNormalizerTest {
  @Test
  fun normalize_addsHttpsAndRemovesTrailingSlash() {
    assertEquals("https://memoh.example.com/api", UrlNormalizer.normalize("memoh.example.com/api/"))
  }

  @Test
  fun discoveryCandidates_tryDirectThenApi() {
    assertEquals(
      listOf("https://memoh.example.com", "https://memoh.example.com/api"),
      UrlNormalizer.discoveryCandidates("https://memoh.example.com"),
    )
  }

  @Test
  fun normalize_rejectsEmbeddedCredentials() {
    assertThrows(IllegalArgumentException::class.java) {
      UrlNormalizer.normalize("https://user:secret@memoh.example.com")
    }
  }
}
