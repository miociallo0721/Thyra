package dev.thyra.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.thyra.core.designsystem.ThyraTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val sage = android.graphics.Color.rgb(232, 239, 232)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(sage, sage),
      navigationBarStyle = SystemBarStyle.light(sage, sage),
    )
    setContent { ThyraTheme { ThyraApp(viewModel) } }
  }

  override fun onStart() {
    super.onStart()
    viewModel.onAppForegrounded()
  }

  override fun onStop() {
    viewModel.onAppBackgrounded()
    super.onStop()
  }
}
