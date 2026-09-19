package dev.thyra.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.thyra.core.model.AppDestination
import dev.thyra.core.model.ThyraUiState
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
      backStack.clear()
      backStack.addAll(expectedPath)
    }
  }
  BackHandler(enabled = state.destination != AppDestination.Connection) { viewModel.back() }

  NavDisplay(
    backStack = backStack,
    onBack = { viewModel.back() },
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
    val expanded = maxWidth >= 840.dp
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
          showBack = false,
          modifier = Modifier.width(360.dp),
        )
        val session = state.selectedSession
        if (showChat && session != null) {
          ChatScreen(
            agent = agent,
            session = session,
            turns = state.visibleTurns,
            socketStatus = state.socketStatus,
            isGenerating = state.live.isGenerating,
            errorMessage = state.errorMessage,
            onSend = viewModel::sendMessage,
            onStop = viewModel::stopGeneration,
            onBack = viewModel::back,
            showBack = false,
            modifier = Modifier.weight(1f),
          )
        } else {
          Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { }
        }
      }
    } else if (showChat && state.selectedSession != null) {
      val compactSession = requireNotNull(state.selectedSession)
      ChatScreen(
        agent = agent,
        session = compactSession,
        turns = state.visibleTurns,
        socketStatus = state.socketStatus,
        isGenerating = state.live.isGenerating,
        errorMessage = state.errorMessage,
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
