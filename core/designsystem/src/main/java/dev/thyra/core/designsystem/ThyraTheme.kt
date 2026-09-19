package dev.thyra.core.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val ThyraLightColors = lightColorScheme(
  primary = Color(0xFF27605C),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFCCE8E4),
  onPrimaryContainer = Color(0xFF0D3B38),
  secondary = Color(0xFF53635F),
  background = Color(0xFFF9FAF8),
  surface = Color(0xFFF9FAF8),
  surfaceVariant = Color(0xFFEEF1EF),
  outline = Color(0xFF737A77),
  outlineVariant = Color(0xFFD8DDDA),
  error = Color(0xFFB3261E),
)

private val ThyraDarkColors = darkColorScheme(
  primary = Color(0xFF93D2CC),
  onPrimary = Color(0xFF003734),
  primaryContainer = Color(0xFF174E4A),
  onPrimaryContainer = Color(0xFFB0EFEB),
  secondary = Color(0xFFBACAC5),
  background = Color(0xFF101413),
  surface = Color(0xFF101413),
  surfaceVariant = Color(0xFF1D2422),
  outline = Color(0xFF89918E),
  outlineVariant = Color(0xFF39413F),
  error = Color(0xFFFFB4AB),
)

@Immutable
data class ThyraSpacing(
  val xxs: Dp = 4.dp,
  val xs: Dp = 8.dp,
  val sm: Dp = 12.dp,
  val md: Dp = 16.dp,
  val lg: Dp = 24.dp,
  val xl: Dp = 32.dp,
  val xxl: Dp = 48.dp,
)

private val LocalThyraSpacing = staticCompositionLocalOf { ThyraSpacing() }

object ThyraThemeTokens {
  val spacing: ThyraSpacing
    @Composable @ReadOnlyComposable get() = LocalThyraSpacing.current
}

@Composable
fun ThyraTheme(content: @Composable () -> Unit) {
  val isDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
  MaterialTheme(
    colorScheme = if (isDark) ThyraDarkColors else ThyraLightColors,
    typography = MaterialTheme.typography,
    content = content,
  )
}

@Composable
fun AgentAvatar(
  name: String,
  imageUrl: String?,
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    if (imageUrl.isNullOrBlank()) {
      Text(
        text = name.trim().take(1).uppercase().ifBlank { "Θ" },
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
    } else {
      AsyncImage(
        model = imageUrl,
        contentDescription = "$name 的头像",
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.Crop,
      )
    }
  }
}

@Composable
fun StatusLabel(label: String, healthy: Boolean, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.semantics { stateDescription = label },
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(7.dp)
        .clip(CircleShape)
        .background(if (healthy) Color(0xFF2E7D66) else MaterialTheme.colorScheme.outline),
    )
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun InlineError(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.errorContainer)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
    Text(
      text = message,
      modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.onErrorContainer,
      style = MaterialTheme.typography.bodyMedium,
    )
    if (onRetry != null) Button(onClick = onRetry) { Text("重试") }
  }
}

@Composable
fun EmptyState(
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  Column(
    modifier = modifier.padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      Icons.Outlined.Inbox,
      contentDescription = null,
      modifier = Modifier.size(36.dp),
      tint = MaterialTheme.colorScheme.outline,
    )
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(
      message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (action != null) {
      Spacer(Modifier.height(20.dp))
      action()
    }
  }
}
