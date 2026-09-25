package dev.thyra.feature.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.thyra.core.designsystem.InlineError
import dev.thyra.core.model.AuthMode
import dev.thyra.core.model.ServerProfile

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConnectionScreen(
  profiles: List<ServerProfile>,
  busy: Boolean,
  errorMessage: String?,
  onOfficialCloud: () -> Unit,
  onAddServer: (displayName: String, address: String) -> Unit,
  onSelectProfile: (ServerProfile) -> Unit,
  onExploreDemo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showSelfHostedFlow by rememberSaveable { mutableStateOf(false) }
  var serverAddress by rememberSaveable { mutableStateOf("") }
  var displayName by rememberSaveable { mutableStateOf("") }
  var pendingAction by rememberSaveable { mutableStateOf("") }
  var failedAction by rememberSaveable { mutableStateOf("") }

  LaunchedEffect(busy, errorMessage) {
    if (!busy && pendingAction.isNotBlank()) {
      if (errorMessage != null) failedAction = pendingAction
      if (pendingAction == "add" && errorMessage == null) showSelfHostedFlow = false
      pendingAction = ""
    }
  }

  val servers = profiles
    .filter { it.authMode != AuthMode.Cloud }
    .sortedByDescending { it.lastUsedAt }
  val recentServer = servers.firstOrNull()

  Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxSize()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("连接到 Thyra", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
          Text(
            "继续使用最近的服务器，或连接 Memoh Cloud。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        FilledTonalButton(
          onClick = {
            pendingAction = "cloud"
            failedAction = ""
            onOfficialCloud()
          },
          modifier = Modifier.fillMaxWidth(),
          enabled = !busy,
        ) {
          if (busy && pendingAction == "cloud") {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Outlined.Cloud, contentDescription = null)
          }
          Spacer(Modifier.width(10.dp))
          Text(if (busy && pendingAction == "cloud") "正在连接 Memoh Cloud…" else "使用 Memoh Cloud")
        }
        if (failedAction == "cloud" && errorMessage != null) {
          InlineError(errorMessage)
        }

        if (recentServer != null) {
          Spacer(Modifier.height(4.dp))
          Text("最近使用", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          key(recentServer.id) {
            ServerProfileButton(
              profile = recentServer,
              busy = busy && pendingAction == "server:${recentServer.id}",
              enabled = !busy,
              onClick = {
                pendingAction = "server:${recentServer.id}"
                failedAction = ""
                onSelectProfile(recentServer)
              },
            )
          }
          if (failedAction == "server:${recentServer.id}" && errorMessage != null) {
            InlineError(errorMessage)
          }

          val otherServers = servers.drop(1)
          if (otherServers.isNotEmpty()) {
            Text("其他服务器", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
              otherServers.forEach { profile ->
                key(profile.id) {
                  SavedServerRow(
                    profile = profile,
                    busy = busy && pendingAction == "server:${profile.id}",
                    enabled = !busy,
                    onClick = {
                      pendingAction = "server:${profile.id}"
                      failedAction = ""
                      onSelectProfile(profile)
                    },
                  )
                  if (failedAction == "server:${profile.id}" && errorMessage != null) {
                    InlineError(errorMessage)
                  }
                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
              }
            }
          }
        }

        OutlinedButton(
          onClick = {
            failedAction = ""
            showSelfHostedFlow = true
          },
          modifier = Modifier.fillMaxWidth(),
          enabled = !busy,
        ) {
          if (busy && pendingAction == "add") {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Outlined.Add, contentDescription = null)
          }
          Spacer(Modifier.width(8.dp))
          Text(if (busy && pendingAction == "add") "正在验证服务器…" else "新增自托管服务器")
        }
        if (failedAction == "add" && errorMessage != null) InlineError(errorMessage)
        TextButton(
          onClick = onExploreDemo,
          modifier = Modifier.align(Alignment.CenterHorizontally),
          enabled = !busy,
        ) {
          Text("先浏览演示")
        }
      }
    }
  }

  if (showSelfHostedFlow) {
    Dialog(
      onDismissRequest = { showSelfHostedFlow = false },
      properties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = false,
      ),
    ) {
      Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
          TopAppBar(
            title = { Text("新增自托管服务器") },
            navigationIcon = {
              IconButton(onClick = { showSelfHostedFlow = false }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回连接方式")
              }
            },
          )
        },
      ) { flowPadding ->
        Box(
          modifier = Modifier.fillMaxSize().padding(flowPadding),
          contentAlignment = Alignment.TopCenter,
        ) {
          AnimatedVisibility(
            visible = showSelfHostedFlow,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 18 }),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxSize(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Text("连接你的 Memoh 部署", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
              Text(
                "输入服务器地址。Thyra 会按现有连接流程验证服务器并继续登录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              OutlinedTextField(
                value = serverAddress,
                onValueChange = { serverAddress = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Memoh API 地址") },
                placeholder = { Text("https://memoh.example.com/api") },
                supportingText = { Text("请使用 HTTPS 地址。") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
              )
              OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("服务器名称（可选）") },
                placeholder = { Text("家庭服务器") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              )
              if (errorMessage != null && (failedAction == "add" || pendingAction == "add")) {
                InlineError(errorMessage)
              }
              Button(
                onClick = {
                  pendingAction = "add"
                  failedAction = ""
                  onAddServer(displayName, serverAddress)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && serverAddress.isNotBlank(),
              ) {
                if (busy && pendingAction == "add") {
                  CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                  Spacer(Modifier.width(10.dp))
                  Text("正在验证服务器…")
                } else {
                  Text("验证并继续")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ServerProfileButton(
  profile: ServerProfile,
  busy: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
        Text(
          profile.baseUrl,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
        )
      }
      if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
      else Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
  }
}

@Composable
private fun SavedServerRow(
  profile: ServerProfile,
  busy: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        enabled = enabled,
        role = Role.Button,
        onClickLabel = "连接到 ${profile.displayName}",
        onClick = onClick,
      )
      .semantics(mergeDescendants = true) {
        contentDescription = "${profile.displayName}，${profile.baseUrl}"
      }
      .padding(vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
      Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    else Icon(Icons.Outlined.ChevronRight, contentDescription = null)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationScreen(
  profile: ServerProfile,
  busy: Boolean,
  errorMessage: String?,
  cloudEmailCodeSent: Boolean,
  onPasswordLogin: (identity: String, password: String) -> Unit,
  onTokenLogin: (token: String) -> Unit,
  onSendCloudEmailCode: (email: String) -> Unit,
  onCloudLogin: (email: String, code: String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Keep credentials and tokens out of saved state. Email and the selected
  // presentation mode are safe to restore after a configuration change.
  var useToken by rememberSaveable { mutableStateOf(false) }
  var identity by rememberSaveable { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var token by remember { mutableStateOf("") }
  var cloudEmail by rememberSaveable { mutableStateOf("") }
  var cloudCode by remember { mutableStateOf("") }

  Scaffold(
    modifier = modifier.imePadding(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("登录方式")
            Text(profile.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
        },
      )
    },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Icon(
          if (profile.authMode == AuthMode.Cloud) Icons.Outlined.Cloud else if (useToken) Icons.Outlined.Key else Icons.Outlined.Lock,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          Text("登录 Memoh", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
          Text(
            profile.baseUrl,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (profile.authMode == AuthMode.Cloud) {
          Text("Memoh Cloud · 邮箱验证码", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
          OutlinedTextField(
            value = cloudEmail,
            onValueChange = { cloudEmail = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("邮箱") },
            supportingText = { Text(if (cloudEmailCodeSent) "验证码已发送，请查看你的邮箱。" else "验证码将发送到你的 Cloud 账号邮箱。") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (cloudEmailCodeSent) ImeAction.Next else ImeAction.Done),
          )
          if (cloudEmailCodeSent) {
            OutlinedTextField(
              value = cloudCode,
              onValueChange = { cloudCode = it },
              modifier = Modifier.fillMaxWidth(),
              label = { Text("验证码") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            )
          }
        } else {
          Text("选择登录方式", style = MaterialTheme.typography.titleSmall)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = !useToken,
              onClick = { useToken = false },
              modifier = Modifier.weight(1f),
              enabled = !busy,
              label = { Text("邮箱和密码") },
              leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            )
            FilterChip(
              selected = useToken,
              onClick = { useToken = true },
              modifier = Modifier.weight(1f),
              enabled = !busy,
              label = { Text("访问令牌") },
              leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
            )
          }
          if (useToken) {
            OutlinedTextField(
              value = token,
              onValueChange = { token = it },
              modifier = Modifier.fillMaxWidth(),
              label = { Text("访问令牌") },
              supportingText = { Text("令牌仅用于本次登录，不会显示在地址或日志中。") },
              visualTransformation = PasswordVisualTransformation(),
              minLines = 1,
              maxLines = 3,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
          } else {
            OutlinedTextField(
              value = identity,
              onValueChange = { identity = it },
              modifier = Modifier.fillMaxWidth(),
              label = { Text("邮箱或用户名") },
              supportingText = { Text("推荐使用邮箱登录") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
              value = password,
              onValueChange = { password = it },
              modifier = Modifier.fillMaxWidth(),
              label = { Text("密码") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
          }
        }

        if (errorMessage != null) InlineError(errorMessage)
        Button(
          onClick = {
            when {
              profile.authMode == AuthMode.Cloud && cloudEmailCodeSent -> onCloudLogin(cloudEmail, cloudCode)
              profile.authMode == AuthMode.Cloud -> onSendCloudEmailCode(cloudEmail)
              useToken -> onTokenLogin(token)
              else -> onPasswordLogin(identity, password)
            }
          },
          modifier = Modifier.fillMaxWidth(),
          enabled = !busy && when {
            profile.authMode == AuthMode.Cloud && cloudEmailCodeSent -> cloudEmail.isNotBlank() && cloudCode.isNotBlank()
            profile.authMode == AuthMode.Cloud -> cloudEmail.isNotBlank()
            useToken -> token.isNotBlank()
            else -> identity.isNotBlank() && password.isNotBlank()
          },
        ) {
          if (busy) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(if (profile.authMode == AuthMode.Cloud && !cloudEmailCodeSent) "正在发送验证码…" else "正在登录…")
          } else {
            Text(if (profile.authMode == AuthMode.Cloud && !cloudEmailCodeSent) "发送验证码" else "登录")
          }
        }
        if (profile.authMode == AuthMode.Cloud && cloudEmailCodeSent) {
          TextButton(
            onClick = { onSendCloudEmailCode(cloudEmail) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enabled = !busy && cloudEmail.isNotBlank(),
          ) { Text("重新发送验证码") }
        }
      }
    }
  }
}
