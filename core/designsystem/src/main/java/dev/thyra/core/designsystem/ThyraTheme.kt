package dev.thyra.core.designsystem

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val ThyraLightColors = lightColorScheme(
  primary = Color(0xFF456D5A),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDDEBDF),
  onPrimaryContainer = Color(0xFF202B23),
  secondary = Color(0xFF526157),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE5EDE4),
  onSecondaryContainer = Color(0xFF202B23),
  background = Color(0xFFE8EFE8),
  onBackground = Color(0xFF202B23),
  surface = Color(0xFFFBFCF9),
  onSurface = Color(0xFF202B23),
  surfaceVariant = Color(0xFFE5EDE4),
  onSurfaceVariant = Color(0xFF526157),
  outline = Color(0xFF75877A),
  outlineVariant = Color(0xFFD1DBD1),
  error = Color(0xFFA35651),
  onError = Color.White,
  errorContainer = Color(0xFFF7E7E3),
  onErrorContainer = Color(0xFF633B37),
)

private val ThyraTypography = Typography(
  headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
  titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
  titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
  bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
  bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
  bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
  labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
  labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
  labelSmall = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
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
@Immutable
data class ThyraMotion(val feedbackMs: Int = 120, val disclosureMs: Int = 200, val navigationMs: Int = 240)

private val LocalThyraMotion = staticCompositionLocalOf { ThyraMotion() }

object ThyraThemeTokens {
  val spacing: ThyraSpacing
    @Composable @ReadOnlyComposable get() = LocalThyraSpacing.current
  val motion: ThyraMotion
    @Composable @ReadOnlyComposable get() = LocalThyraMotion.current
}

@Composable
fun ThyraTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = ThyraLightColors,
    typography = ThyraTypography,
    content = content,
  )
}

/** Static tinted glass; the translucent layer remains readable on API 26. */
@Composable
fun GlassSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
    shadowElevation = 2.dp,
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
