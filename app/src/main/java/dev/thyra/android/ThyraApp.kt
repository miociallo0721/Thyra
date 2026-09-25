package dev.thyra.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.thyra.core.model.AppDestination
import dev.thyra.core.model.ThyraUiState
import dev.thyra.core.designsystem.AgentAvatar
import dev.thyra.core.designsystem.EmptyState
import dev.thyra.core.designsystem.ThyraThemeTokens
import dev.thyra.feature.agents.AgentsScreen
import dev.thyra.feature.chat.ChatScreen
import dev.thyra.feature.connection.AuthenticationScreen
import dev.thyra.feature.connection.ConnectionScreen
import dev.thyra.feature.sessions.SessionsScreen

@Composable
fun ThyraApp(viewModel: MainViewModel) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  if (state.restoring) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    return
  }

  val backStack = rememberNavBackStack(ConnectionRoute)
  val expectedPath = state.destination.path()
  LaunchedEffect(expectedPath) {
    if (backStack.toList() != expectedPath) {
      val sharedCount = backStack.zip(expectedPath).takeWhile { (current, expected) -> current == expected }.count()
      while (backStack.size > sharedCount) backStack.removeAt(backStack.lastIndex)
      backStack.addAll(expectedPath.drop(sharedCount))
    }
  }
  BackHandler(enabled = state.destination != AppDestination.Connection) { viewModel.back() }
  val motion = ThyraThemeTokens.motion
  val isWide = LocalConfiguration.current.screenWidthDp >= 840 * LocalDensity.current.fontScale

  NavDisplay(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    backStack = backStack,
    onBack = { viewModel.back() },
    transitionSpec = {
      if (isWide) EnterTransition.None togetherWith ExitTransition.None
      else (fadeIn(tween(motion.navigationMs)) + slideInHorizontally(tween(motion.navigationMs), { it / 12 })) togetherWith fadeOut(tween(motion.feedbackMs))
    },
    popTransitionSpec = {
      if (isWide) EnterTransition.None togetherWith ExitTransition.None
      else fadeIn(tween(motion.navigationMs)) togetherWith (fadeOut(tween(motion.navigationMs)) + slideOutHorizontally(tween(motion.navigationMs), { it / 12 }))
    },
    predictivePopTransitionSpec = {
      if (isWide) EnterTransition.None togetherWith ExitTransition.None
      else fadeIn(tween(motion.navigationMs)) togetherWith (fadeOut(tween(motion.navigationMs)) + slideOutHorizontally(tween(motion.navigationMs), { it / 12 }))
    },
    entryProvider = entryProvider {
      entry<ConnectionRoute> {
        ConnectionScreen(
          profiles = state.profiles,
          busy = state.busy,
          errorMessage = state.errorMessage,
          onOfficialCloud = viewModel::useOfficialCloud,
          onAddServer = viewModel::addServer,
          onSelectProfile = viewModel::selectProfile,
          onExploreDemo = viewModel::exploreDemo,
        )
      }
      entry<AuthenticationRoute> {
        val profile = state.selectedProfile
        if (profile != null) {
          AuthenticationScreen(
            profile = profile,
            busy = state.busy,
            errorMessage = state.errorMessage,
            cloudEmailCodeSent = state.cloudEmailCodeSent,
            onPasswordLogin = viewModel::loginWithPassword,
            onTokenLogin = viewModel::loginWithToken,
            onSendCloudEmailCode = viewModel::sendCloudEmailCode,
            onCloudLogin = viewModel::loginWithCloudEmailCode,
            onBack = viewModel::back,
          )
        }
      }
      entry<AgentsRoute> {
        val profile = state.selectedProfile
        if (profile != null) {
          AgentsScreen(
            profile = profile,
            agents = state.agents,
            busy = state.busy,
            errorMessage = state.errorMessage,
            onAgentSelected = viewModel::selectAgent,
            onRefresh = viewModel::refreshAgents,
            onSwitchServer = viewModel::switchServer,
            onLogout = viewModel::logout,
          )
        }
      }
      entry<SessionsRoute> { WorkspaceScreen(state, viewModel, showChat = false) }
      entry<ChatRoute> { WorkspaceScreen(state, viewModel, showChat = true) }
    },
  )
}

@Composable
private fun WorkspaceScreen(state: ThyraUiState, viewModel: MainViewModel, showChat: Boolean) {
  val agent = state.selectedAgent ?: return
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val expanded = maxWidth >= 840.dp * LocalDensity.current.fontScale
    val sidebarWidth = (maxWidth * 0.34f).coerceIn(280.dp, 380.dp)
    if (expanded) {
      Row(Modifier.fillMaxSize()) {
        SessionsScreen(
          agent = agent,
          sessions = state.sessions,
          selectedSessionId = state.selectedSession?.id,
          busy = state.busy,
          errorMessage = if (showChat) null else state.errorMessage,
          onSessionSelected = viewModel::selectSession,
          onNewSession = viewModel::createSession,
          onRefresh = viewModel::refreshSessions,
          onBack = viewModel::back,
          serverName = state.selectedProfile?.displayName,
          showBack = true,
          modifier = Modifier.width(sidebarWidth),
        )
        val session = state.selectedSession
        if (showChat && session != null) {
          ChatScreen(
            agent = agent,
            session = session,
            turns = state.visibleTurns,
            socketStatus = state.socketStatus,
            serverName = state.selectedProfile?.displayName.orEmpty(),
            historyLoading = state.historyLoading,
            draft = viewModel.draftFor(state.selectedProfile?.id.orEmpty(), agent.id, session.id),
            onDraftChange = { viewModel.updateDraft(state.selectedProfile?.id.orEmpty(), agent.id, session.id, it) },
            isGenerating = state.live.isGenerating,
            errorMessage = state.errorMessage,
            onRetry = viewModel::retryChat,
            onSend = viewModel::sendMessage,
            onStop = viewModel::stopGeneration,
            onBack = viewModel::back,
            showBack = false,
            modifier = Modifier.weight(1f),
          )
        } else {
          Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
              AgentAvatar(agent.displayName, agent.avatarUrl, size = 56.dp)
              Text(agent.displayName, style = MaterialTheme.typography.titleLarge)
              EmptyState("选择或新建会话", "从左侧选择对话，或开始与 ${agent.displayName} 的新对话。") {
                Button(onClick = viewModel::createSession, enabled = !state.busy) { Text("新建会话") }
              }
            }
          }
        }
      }
    } else if (showChat && state.selectedSession != null) {
      val compactSession = requireNotNull(state.selectedSession)
      ChatScreen(
        agent = agent,
        session = compactSession,
        turns = state.visibleTurns,
        socketStatus = state.socketStatus,
        serverName = state.selectedProfile?.displayName.orEmpty(),
        historyLoading = state.historyLoading,
        draft = viewModel.draftFor(state.selectedProfile?.id.orEmpty(), agent.id, compactSession.id),
        onDraftChange = { viewModel.updateDraft(state.selectedProfile?.id.orEmpty(), agent.id, compactSession.id, it) },
        isGenerating = state.live.isGenerating,
        errorMessage = state.errorMessage,
        onRetry = viewModel::retryChat,
        onSend = viewModel::sendMessage,
        onStop = viewModel::stopGeneration,
        onBack = viewModel::back,
      )
    } else {
      SessionsScreen(
        agent = agent,
        sessions = state.sessions,
        busy = state.busy,
        errorMessage = state.errorMessage,
        onSessionSelected = viewModel::selectSession,
        onNewSession = viewModel::createSession,
        onRefresh = viewModel::refreshSessions,
        onBack = viewModel::back,
        serverName = state.selectedProfile?.displayName,
      )
    }
  }
}

private fun AppDestination.path(): List<NavKey> = when (this) {
  AppDestination.Connection -> listOf(ConnectionRoute)
  AppDestination.Authentication -> listOf(ConnectionRoute, AuthenticationRoute)
  AppDestination.Agents -> listOf(ConnectionRoute, AgentsRoute)
  AppDestination.Sessions -> listOf(ConnectionRoute, AgentsRoute, SessionsRoute)
  AppDestination.Chat -> listOf(ConnectionRoute, AgentsRoute, SessionsRoute, ChatRoute)
}
