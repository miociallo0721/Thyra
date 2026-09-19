package dev.thyra.feature.agents

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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.thyra.core.designsystem.AgentAvatar
import dev.thyra.core.designsystem.EmptyState
import dev.thyra.core.designsystem.InlineError
import dev.thyra.core.designsystem.StatusLabel
import dev.thyra.core.model.Agent
import dev.thyra.core.model.ServerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
  profile: ServerProfile,
  agents: List<Agent>,
  busy: Boolean,
  errorMessage: String?,
  onAgentSelected: (Agent) -> Unit,
  onRefresh: () -> Unit,
  onSwitchServer: () -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Agents", fontWeight = FontWeight.SemiBold)
            Text(profile.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        actions = {
          IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Outlined.Refresh, "刷新") }
          TextButton(onClick = onSwitchServer) { Text("服务器") }
          TextButton(onClick = onLogout, enabled = !busy) { Text("退出") }
        },
      )
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      if (errorMessage != null) InlineError(errorMessage, onRetry = onRefresh)
      if (busy && agents.isEmpty()) {
        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
      } else if (agents.isEmpty()) {
        EmptyState(
          title = "还没有可用的 Agent",
          message = "请先在 Memoh 中创建 Bot，或确认当前账号具有聊天权限。",
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        LazyColumn(Modifier.fillMaxSize()) {
          items(agents, key = Agent::id) { agent ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = agent.enabled) { onAgentSelected(agent) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AgentAvatar(agent.displayName, agent.avatarUrl)
              Column(Modifier.weight(1f)) {
                Text(agent.displayName, style = MaterialTheme.typography.titleMedium)
                if (agent.name != agent.displayName) {
                  Text(agent.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              StatusLabel(
                label = if (!agent.enabled) "已停用" else agent.status.replaceFirstChar { it.uppercase() },
                healthy = agent.enabled && agent.status in setOf("ready", "active", "running"),
              )
            }
            HorizontalDivider(Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
      }
    }
  }
}
