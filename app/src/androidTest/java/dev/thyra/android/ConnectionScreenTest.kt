package dev.thyra.android

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.thyra.core.designsystem.ThyraTheme
import dev.thyra.feature.connection.ConnectionScreen
import org.junit.Rule
import org.junit.Test

class ConnectionScreenTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun validationButtonRequiresServerAddress() {
    composeRule.setContent {
      ThyraTheme {
        ConnectionScreen(
          profiles = emptyList(),
          busy = false,
          errorMessage = null,
          onOfficialCloud = {},
          onAddServer = { _, _ -> },
          onSelectProfile = {},
          onExploreDemo = {},
        )
      }
    }

    composeRule.onNodeWithText("新增自托管服务器").performClick()
    composeRule.onNodeWithText("验证并继续").assertIsNotEnabled()
    composeRule.onNodeWithText("Memoh API 地址").performTextInput("https://memoh.example.com")
    composeRule.onNodeWithText("验证并继续").assertIsEnabled()
  }
}
