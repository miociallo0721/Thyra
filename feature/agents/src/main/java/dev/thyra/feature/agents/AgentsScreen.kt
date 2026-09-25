package dev.thyra.feature.agents

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Logout
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.thyra.core.designsystem.AgentAvatar
import dev.thyra.core.designsystem.EmptyState
import dev.thyra.core.designsystem.InlineError
import dev.thyra.core.designsystem.ThyraThemeTokens
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
  val listState = rememberLazyListState()

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
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
          IconButton(onClick = onSwitchServer) { Icon(Icons.Outlined.Storage, "切换服务器") }
          IconButton(onClick = onLogout, enabled = !busy) { Icon(Icons.Outlined.Logout, "退出登录") }
        },
      )
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      when {
        busy && agents.isEmpty() -> LoadingAgents()
        agents.isEmpty() && errorMessage != null -> Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            title = "暂时无法加载 Agent",
            message = errorMessage,
            action = { TextButton(onClick = onRefresh, enabled = !busy) { Text("重试") } },
          )
        }
        agents.isEmpty() -> Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            title = "还没有可用的 Agent",
            message = "请先在 Memoh 中创建 Bot，或确认当前账号具有聊天权限。",
            action = { TextButton(onClick = onSwitchServer) { Text("切换服务器") } },
          )
        }
        else -> {
          if (errorMessage != null) InlineError(errorMessage, onRetry = onRefresh)
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
          ) {
            items(agents, key = Agent::id) { agent ->
              AgentRow(agent = agent, onClick = { onAgentSelected(agent) })
              HorizontalDivider(Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LoadingAgents() {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      CircularProgressIndicator()
      Text("正在加载 Agent…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun AgentRow(agent: Agent, onClick: () -> Unit) {
  val available = agent.enabled
  val statusLabel = if (available) "可用" else "不可用"
  val unavailableReason = if (available) null else "此 Agent 已停用，请在 Memoh 中启用后继续。"
  val backgroundColor by animateColorAsState(
    targetValue = if (available) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
    animationSpec = tween(ThyraThemeTokens.motion.feedbackMs),
    label = "agentAvailability",
  )
  val statusColor = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(backgroundColor)
      .clickable(
        enabled = available,
        role = Role.Button,
        onClickLabel = if (available) "打开 ${agent.displayName}" else null,
        onClick = onClick,
      )
      .semantics(mergeDescendants = true) {
        contentDescription = buildString {
          append(agent.displayName)
          if (agent.name != agent.displayName) append("，${agent.name}")
        }
        stateDescription = unavailableReason ?: statusLabel
      }
      .padding(horizontal = 20.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AgentAvatar(agent.displayName, agent.avatarUrl)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(agent.displayName, style = MaterialTheme.typography.titleMedium)
      if (agent.name != agent.displayName) {
        Text(
          agent.name,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (unavailableReason != null) {
        Text(
          unavailableReason,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor)
  }
}
