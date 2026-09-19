package dev.thyra.feature.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.thyra.core.designsystem.AgentAvatar
import dev.thyra.core.designsystem.EmptyState
import dev.thyra.core.designsystem.InlineError
import dev.thyra.core.model.Agent
import dev.thyra.core.model.ChatSession

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
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AgentAvatar(agent.displayName, agent.avatarUrl, size = 34.dp)
            Column {
              Text(agent.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text("对话", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
      FloatingActionButton(onClick = onNewSession) { Icon(Icons.Outlined.Add, "新建对话") }
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      if (errorMessage != null) InlineError(errorMessage, onRetry = onRefresh)
      if (busy && sessions.isEmpty()) {
        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
      } else if (sessions.isEmpty()) {
        EmptyState(
          title = "开始第一段对话",
          message = "消息会在这里按最近使用时间排列。",
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        LazyColumn(Modifier.fillMaxSize()) {
          items(sessions, key = ChatSession::id) { session ->
            val selected = session.id == selectedSessionId
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onSessionSelected(session) }
                .then(if (selected) Modifier.padding(start = 3.dp) else Modifier)
                .padding(horizontal = 20.dp, vertical = 15.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
              )
              Column(Modifier.weight(1f)) {
                Text(session.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(
                  session.type.replaceFirstChar { it.uppercase() },
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
      }
    }
  }
}
