package dev.thyra.feature.sessions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.thyra.core.designsystem.AgentAvatar
import dev.thyra.core.designsystem.EmptyState
import dev.thyra.core.designsystem.InlineError
import dev.thyra.core.designsystem.ThyraThemeTokens
import dev.thyra.core.model.Agent
import dev.thyra.core.model.ChatSession
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
  agent: Agent,
  sessions: List<ChatSession>,
  selectedSessionId: String? = null,
  busy: Boolean,
  errorMessage: String?,
  onSessionSelected: (ChatSession) -> Unit,
  onNewSession: () -> Unit,
  onRefresh: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  showBack: Boolean = true,
  serverName: String? = null,
) {
  val listState = rememberSaveable(agent.id, saver = LazyListState.Saver) { LazyListState() }

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AgentAvatar(agent.displayName, agent.avatarUrl, size = 34.dp)
            Column {
              Text(agent.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text(
                if (serverName.isNullOrBlank()) "对话" else "对话 · $serverName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        navigationIcon = {
          if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
        },
        actions = { IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Outlined.Refresh, "刷新") } },
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = onNewSession, content = { Icon(Icons.Outlined.Add, "新建对话") })
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      when {
        busy && sessions.isEmpty() -> LoadingSessions()
        sessions.isEmpty() && errorMessage != null -> Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            title = "暂时无法加载会话",
            message = errorMessage,
            action = { TextButton(onClick = onRefresh, enabled = !busy) { Text("重试") } },
          )
        }
        sessions.isEmpty() -> Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            title = "开始第一段对话",
            message = "消息会在这里按最近更新时间排列。",
            action = { TextButton(onClick = onNewSession) { Text("新建对话") } },
          )
        }
        else -> {
          if (errorMessage != null) InlineError(errorMessage, onRetry = onRefresh)
          LazyColumn(
            modifier = Modifier.fillMaxSize().selectableGroup(),
            state = listState,
          ) {
            items(sessions, key = ChatSession::id) { session ->
              SessionRow(
                session = session,
                selected = session.id == selectedSessionId,
                onClick = { onSessionSelected(session) },
              )
              HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LoadingSessions() {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      CircularProgressIndicator()
      Text("正在加载会话…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun SessionRow(session: ChatSession, selected: Boolean, onClick: () -> Unit) {
  val updatedAt = formatSessionUpdatedAt(session.updatedAt?.takeIf(String::isNotBlank) ?: session.createdAt)
  val backgroundColor by animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    animationSpec = tween(ThyraThemeTokens.motion.feedbackMs),
    label = "sessionSelection",
  )
  val foregroundColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
  val detailColor = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant
  val selectionDescription = if (selected) "当前会话" else "未选中"
  val accessibilityLabel = "${session.title}，${session.type}，更新时间：$updatedAt"

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 2.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(backgroundColor)
      .selectable(
        selected = selected,
        role = Role.RadioButton,
        onClick = onClick,
      )
      .semantics(mergeDescendants = true) {
        contentDescription = accessibilityLabel
        stateDescription = selectionDescription
      }
      .padding(horizontal = 16.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Outlined.ChatBubbleOutline,
      contentDescription = null,
      tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        session.title,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = foregroundColor,
      )
      Text(
        "${session.type.replaceFirstChar { it.uppercase() }} · $updatedAt",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium,
        color = detailColor,
      )
    }
  }
}

private fun formatSessionUpdatedAt(value: String?): String {
  if (value.isNullOrBlank()) return "更新时间未知"
  val instant = runCatching { Instant.parse(value) }.getOrNull()
    ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    ?: return value.substringBefore('T').ifBlank { "更新时间未知" }

  val minutes = Duration.between(instant, Instant.now()).toMinutes().coerceAtLeast(0)
  return when {
    minutes < 1 -> "刚刚"
    minutes < 60 -> "${minutes}分钟前"
    minutes < 24 * 60 -> "${minutes / 60}小时前"
    minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}天前"
    else -> DateTimeFormatter.ofPattern("M月d日", Locale.getDefault())
      .withZone(ZoneId.systemDefault())
      .format(instant)
  }
}
