package dev.thyra.android

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import dev.thyra.core.designsystem.ThyraTheme
import dev.thyra.core.model.AuthMode
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
          // A server that was last accessed with a token must still default to
          // the preferred password login flow when authentication is shown.
          profile = ServerProfile(
            id = "server",
            displayName = "Memoh",
            baseUrl = "https://memoh.example.com",
            authMode = AuthMode.AccessToken,
          ),
          busy = false,
          errorMessage = null,
          cloudEmailCodeSent = false,
          onPasswordLogin = { _, _ -> },
          onTokenLogin = {},
          onSendCloudEmailCode = {},
          onCloudLogin = { _, _ -> },
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

  @Test
  fun cloudLoginUsesNativeEmailCodeFields() {
    composeRule.setContent {
      ThyraTheme {
        AuthenticationScreen(
          profile = ServerProfile(
            id = "memoh-cloud",
            displayName = "Memoh Cloud",
            baseUrl = "https://app.memoh.net/api/memoh",
            authMode = AuthMode.Cloud,
          ),
          busy = false,
          errorMessage = null,
          cloudEmailCodeSent = true,
          onPasswordLogin = { _, _ -> },
          onTokenLogin = {},
          onSendCloudEmailCode = {},
          onCloudLogin = { _, _ -> },
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithText("验证码").assertIsDisplayed()
    composeRule.onNodeWithText("重新发送验证码").assertIsDisplayed()
    composeRule.onNodeWithText("登录").assertIsNotEnabled()
  }
}
