package dev.thyra.android

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import dev.thyra.core.designsystem.ThyraTheme
import dev.thyra.core.model.ServerProfile
import dev.thyra.feature.connection.AuthenticationScreen
import org.junit.Rule
import org.junit.Test

class AuthenticationScreenTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun emailAndPasswordAreThePrimaryLoginFields() {
    composeRule.setContent {
      ThyraTheme {
        AuthenticationScreen(
          profile = ServerProfile("server", "Memoh", "https://memoh.example.com"),
          busy = false,
          errorMessage = null,
          onPasswordLogin = { _, _ -> },
          onTokenLogin = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithText("推荐使用邮箱登录").assertIsDisplayed()
    composeRule.onNodeWithText("登录").assertIsNotEnabled()
    composeRule.onNodeWithText("邮箱或用户名").performTextInput("alice@example.com")
    composeRule.onNodeWithText("密码").performTextInput("secret")
    composeRule.onNodeWithText("登录").assertIsEnabled()
  }
}
