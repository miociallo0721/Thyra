package dev.thyra.feature.chat

import android.graphics.Color as AndroidColor
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.thyra.core.designsystem.AgentAvatar
import dev.thyra.core.designsystem.EmptyState
import dev.thyra.core.designsystem.InlineError
import dev.thyra.core.designsystem.StatusLabel
import dev.thyra.core.designsystem.GlassSurface
import dev.thyra.core.designsystem.ThyraThemeTokens
import dev.thyra.core.model.Agent
import dev.thyra.core.model.ChatBlock
import dev.thyra.core.model.ChatRole
import dev.thyra.core.model.ChatSession
import dev.thyra.core.model.ChatTurn
import dev.thyra.core.model.SocketStatus
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
  agent: Agent,
  session: ChatSession,
  turns: List<ChatTurn>,
  socketStatus: SocketStatus,
  serverName: String,
  historyLoading: Boolean,
  draft: String,
  onDraftChange: (String) -> Unit,
  isGenerating: Boolean,
  errorMessage: String?,
  onRetry: () -> Unit,
  onSend: (String) -> Unit,
  onStop: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  showBack: Boolean = true,
) {
  val listState = rememberSaveable(session.id, saver = LazyListState.Saver) { LazyListState() }
  var pinnedToBottom by rememberSaveable(session.id) { mutableStateOf(true) }
  var initialJumpDone by rememberSaveable(session.id) { mutableStateOf(false) }
  var previousTurnCount by rememberSaveable(session.id) { mutableStateOf(0) }
  val contentVersion = turns.lastOrNull()?.let { it.id to it.hashCode() }
  val rowKeys = remember(session.id, turns) { chatTurnRowKeys(session.id, turns) }

  LaunchedEffect(listState) {
    snapshotFlow { listState.isNearBottom() }
      .distinctUntilChanged()
      .collect { pinnedToBottom = it }
  }
  LaunchedEffect(session.id, historyLoading, turns.size, contentVersion) {
    if (historyLoading || turns.isEmpty()) return@LaunchedEffect
    val bottomIndex = turns.size + 1
    if (!initialJumpDone) {
      listState.scrollToItem(bottomIndex)
      initialJumpDone = true
    } else if (pinnedToBottom) {
      if (turns.size > previousTurnCount) listState.animateScrollToItem(bottomIndex)
      else listState.scrollToItem(bottomIndex)
    }
    previousTurnCount = turns.size
  }

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        title = {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AgentAvatar(agent.displayName, agent.avatarUrl, size = 34.dp)
            Column {
              Text(session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
              Text("$serverName · ${agent.displayName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
              StatusLabel(socketLabel(socketStatus, isGenerating), socketStatus == SocketStatus.Connected)
            }
          }
        },
        navigationIcon = {
          if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回对话列表") }
        },
      )
    },
    bottomBar = {
      ChatComposer(
        isGenerating = isGenerating,
        connected = socketStatus == SocketStatus.Connected,
        text = draft,
        onTextChange = onDraftChange,
        onSend = onSend,
        onStop = onStop,
      )
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      if (errorMessage != null) InlineError(errorMessage, onRetry = onRetry)
      else if (socketStatus in setOf(SocketStatus.Disconnected, SocketStatus.Forbidden, SocketStatus.Expired) && !historyLoading) {
        InlineError("连接已中断，消息仍保留。", onRetry = onRetry)
      }
      if (historyLoading && turns.isEmpty()) {
        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
      } else if (turns.isEmpty() && errorMessage == null && socketStatus !in setOf(SocketStatus.Disconnected, SocketStatus.Forbidden, SocketStatus.Expired)) {
        EmptyState(
          title = session.title,
          message = "发送消息，开始与 ${agent.displayName} 协作。",
          modifier = Modifier.fillMaxSize(),
        )
      } else if (turns.isNotEmpty()) {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
          verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          item(key = "top-space") { Spacer(Modifier.height(4.dp)) }
          itemsIndexed(turns, key = { index, _ -> rowKeys[index] }) { _, turn ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              ChatTurnView(turn, agent, Modifier.widthIn(max = 800.dp).fillMaxWidth().padding(horizontal = 18.dp))
            }
          }
          item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
        }
      }
    }
  }
}

// A server turn_id can occur more than once in REST history. Keep every turn and
// distinguish repeated IDs by their occurrence within this session.
internal fun chatTurnRowKeys(sessionId: String, turns: List<ChatTurn>): List<String> {
  val occurrences = mutableMapOf<String, Int>()
  return turns.map { turn ->
    val occurrence = occurrences.getOrDefault(turn.id, 0)
    occurrences[turn.id] = occurrence + 1
    "turn:${sessionId.length}:$sessionId:${turn.id.length}:${turn.id}:$occurrence"
  }
}

@Composable
private fun ChatTurnView(turn: ChatTurn, agent: Agent, modifier: Modifier = Modifier) {
  when (turn.role) {
    ChatRole.User -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      SelectionContainer {
        Text(
          text = turn.text,
          modifier = Modifier
            .widthIn(max = 560.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp))
            .padding(horizontal = 15.dp, vertical = 11.dp),
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
    ChatRole.Assistant -> Column(modifier.fillMaxWidth()) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AgentAvatar(agent.displayName, agent.avatarUrl, size = 24.dp)
        Text(agent.displayName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (turn.isPending) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
      }
      Spacer(Modifier.height(8.dp))
      AssistantBlocks(turn.blocks)
    }
    ChatRole.System -> SelectionContainer {
      Text(
        turn.text,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun AssistantBlocks(blocks: List<ChatBlock>) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    var index = 0
    while (index < blocks.size) {
      val block = blocks[index]
      if (block is ChatBlock.Tool) {
        val tools = mutableListOf<ChatBlock.Tool>()
        while (index < blocks.size && blocks[index] is ChatBlock.Tool) {
          tools += blocks[index] as ChatBlock.Tool
          index++
        }
        ActivityDisclosure(tools)
        continue
      }
      when (block) {
        is ChatBlock.Text -> MarkdownText(block.content)
        is ChatBlock.Reasoning -> ReasoningDisclosure(block)
        is ChatBlock.Notice -> Text(
          block.content,
          color = if (block.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
        )
        else -> Unit
      }
      index++
    }
  }
}

@Composable
private fun ReasoningDisclosure(reasoning: ChatBlock.Reasoning) {
  var expanded by rememberSaveable(reasoning.id) { mutableStateOf(false) }
  val durationMs = reasoning.durationMs
  val motion = ThyraThemeTokens.motion
  Column {
    Row(
      modifier = Modifier
        .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起思考过程" else "展开思考过程") { expanded = !expanded }
        .heightIn(min = 48.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.outline,
      )
      Text(
        if (durationMs != null) "思考 ${(durationMs / 1000.0).formatOne()} 秒" else "思考过程",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    AnimatedVisibility(expanded, enter = fadeIn(tween(motion.disclosureMs)), exit = fadeOut(tween(motion.disclosureMs))) {
      SelectionContainer {
        Text(
          reasoning.content,
          modifier = Modifier.padding(start = 24.dp, bottom = 6.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ActivityDisclosure(tools: List<ChatBlock.Tool>) {
  var expanded by rememberSaveable(tools.firstOrNull()?.id) { mutableStateOf(false) }
  val running = tools.any(ChatBlock.Tool::running)
  val failed = tools.any(ChatBlock.Tool::failed)
  val motion = ThyraThemeTokens.motion
  GlassSurface(Modifier.fillMaxWidth()) {
  Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起工具活动" else "展开工具活动") { expanded = !expanded }
        .heightIn(min = 48.dp),
      horizontalArrangement = Arrangement.spacedBy(9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      when {
        running -> Icon(Icons.Outlined.HourglassTop, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        failed -> Icon(Icons.Outlined.Terminal, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        else -> Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
      }
      Text(
        when {
          tools.size == 1 -> humanToolName(tools.first().name)
          running -> "正在工作 · ${tools.size} 项活动"
          else -> "${tools.size} 项活动"
        },
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
    }
    AnimatedVisibility(expanded, enter = fadeIn(tween(motion.disclosureMs)), exit = fadeOut(tween(motion.disclosureMs))) {
      Column(Modifier.padding(start = 27.dp, bottom = 8.dp)) {
        tools.forEachIndexed { index, tool ->
          val input = tool.input
          val output = tool.output
          Text(humanToolName(tool.name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
          if (!input.isNullOrBlank()) DetailText("输入", input)
          if (!output.isNullOrBlank()) DetailText("输出", output)
          if (index != tools.lastIndex) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
      }
    }
  }
  }
}

@Composable
private fun DetailText(label: String, value: String) {
  var showAll by rememberSaveable(label, value) { mutableStateOf(false) }
  val clipboard = LocalClipboardManager.current
  Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
  SelectionContainer {
    Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (showAll) Int.MAX_VALUE else 12)
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (!showAll && (value.lines().size > 12 || value.length > 600)) {
      Button(onClick = { showAll = true }) { Text("查看全部") }
    }
    Button(onClick = { clipboard.setText(AnnotatedString(value)) }) { Text("复制$label") }
  }
}

@Composable
private fun MarkdownText(markdown: String) {
  if (markdown.isBlank()) return
  val context = LocalContext.current
  val markwon = remember(context) { Markwon.create(context) }
  val color = MaterialTheme.colorScheme.onSurface.toArgb()
  val linkColor = MaterialTheme.colorScheme.primary.toArgb()
  val textSize = MaterialTheme.typography.bodyLarge.fontSize.value
  AndroidView(
    factory = {
      TextView(it).apply {
        setTextIsSelectable(true)
        movementMethod = LinkMovementMethod.getInstance()
        includeFontPadding = false
        setBackgroundColor(AndroidColor.TRANSPARENT)
      }
    },
    update = { view ->
      view.setTextColor(color)
      view.setLinkTextColor(linkColor)
      view.textSize = textSize
      if (view.tag != markdown) {
        view.tag = markdown
        markwon.setMarkdown(view, markdown)
      }
    },
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun ChatComposer(
  isGenerating: Boolean,
  connected: Boolean,
  text: String,
  onTextChange: (String) -> Unit,
  onSend: (String) -> Unit,
  onStop: () -> Unit,
) {
  fun submit() {
    val value = text.trim()
    if (value.isNotEmpty() && connected && !isGenerating) {
      onTextChange("")
      onSend(value)
    }
  }
  Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 2.dp) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.weight(1f),
        placeholder = { Text(if (connected) "输入消息…" else "连接恢复后可发送") },
        minLines = 1,
        maxLines = 7,
        enabled = !isGenerating,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { submit() }),
        shape = RoundedCornerShape(22.dp),
      )
      IconButton(
        onClick = { if (isGenerating) onStop() else submit() },
        enabled = isGenerating || (connected && text.isNotBlank()),
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          if (isGenerating) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
          contentDescription = if (isGenerating) "停止生成" else "发送",
        )
      }
    }
  }
}

private fun LazyListState.isNearBottom(): Boolean {
  val total = layoutInfo.totalItemsCount
  if (total == 0) return true
  return (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 2
}

private fun socketLabel(status: SocketStatus, generating: Boolean) = when {
  generating -> "正在生成"
  status == SocketStatus.Connected -> "已连接"
  status == SocketStatus.Expired -> "登录已过期"
  status == SocketStatus.Forbidden -> "连接被拒绝"
  status == SocketStatus.Reconnecting -> "正在重连"
  status == SocketStatus.Connecting -> "正在连接"
  else -> "已断开"
}

private fun humanToolName(name: String): String = when (name.lowercase()) {
  "read", "read_file", "read_files" -> "读取文件"
  "search", "grep", "search_files" -> "搜索工作区"
  "shell", "exec", "run_command" -> "运行命令"
  "write", "edit", "apply_patch" -> "更新文件"
  else -> name.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun Double.formatOne() = String.format(java.util.Locale.getDefault(), "%.1f", this)
