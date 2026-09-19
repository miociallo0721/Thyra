package dev.thyra.feature.connection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.thyra.core.designsystem.InlineError
import dev.thyra.core.model.ServerProfile

@Composable
fun ConnectionScreen(
  profiles: List<ServerProfile>,
  busy: Boolean,
  errorMessage: String?,
  onAddServer: (displayName: String, address: String) -> Unit,
  onSelectProfile: (ServerProfile) -> Unit,
  onExploreDemo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var displayName by rememberSaveable { mutableStateOf("") }
  var address by rememberSaveable { mutableStateOf("") }
  Scaffold(modifier = modifier.imePadding()) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
      Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(20.dp))
      Text("打开 Thyra", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
      Text(
        "连接你的 Memoh，进入 Agent 与工作区。",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(36.dp))

      if (profiles.isNotEmpty()) {
        Text("已保存的服务器", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        profiles.sortedByDescending { it.lastUsedAt }.forEach { profile ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable(enabled = !busy) { onSelectProfile(profile) }
              .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
              Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = "连接 ${profile.displayName}")
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(Modifier.height(32.dp))
      }

      Text("添加自托管服务器", style = MaterialTheme.typography.titleSmall)
      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("名称（可选）") },
        placeholder = { Text("家庭服务器") },
      )
      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = address,
        onValueChange = { address = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Memoh API 地址") },
        placeholder = { Text("https://memoh.example.com/api") },
        supportingText = {
          Text(if (address.trim().startsWith("http://")) "HTTP 仅适合本地调试，正式使用请启用 HTTPS。" else "可填写后端直连地址；Thyra 会检测 /api。")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
      )
      if (errorMessage != null) {
        Spacer(Modifier.height(12.dp))
        InlineError(errorMessage)
      }
      Spacer(Modifier.height(20.dp))
      Button(
        onClick = { onAddServer(displayName, address) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy && address.isNotBlank(),
      ) {
        if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
        else Text("验证并继续")
      }
      Spacer(Modifier.height(12.dp))
      OutlinedButton(onClick = onExploreDemo, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
        Text("浏览演示")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationScreen(
  profile: ServerProfile,
  busy: Boolean,
  errorMessage: String?,
  onPasswordLogin: (identity: String, password: String) -> Unit,
  onTokenLogin: (token: String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var useToken by rememberSaveable { mutableStateOf(profile.authMode.name == "AccessToken") }
  var identity by rememberSaveable { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var token by remember { mutableStateOf("") }
  Scaffold(
    modifier = modifier.imePadding(),
    topBar = {
      TopAppBar(
        title = { Text(profile.displayName) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .padding(padding)
        .padding(horizontal = 24.dp, vertical = 28.dp)
        .verticalScroll(rememberScrollState()),
    ) {
      Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(16.dp))
      Text("登录 Memoh", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
      Text(profile.baseUrl, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(28.dp))
      if (useToken) {
        OutlinedTextField(
          value = token,
          onValueChange = { token = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("访问令牌") },
          visualTransformation = PasswordVisualTransformation(),
          minLines = 1,
          maxLines = 3,
        )
      } else {
        OutlinedTextField(
          value = identity,
          onValueChange = { identity = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("邮箱或用户名") },
          supportingText = { Text("推荐使用邮箱登录") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("密码") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
      }
      if (errorMessage != null) {
        Spacer(Modifier.height(12.dp))
        InlineError(errorMessage)
      }
      Spacer(Modifier.height(20.dp))
      Button(
        onClick = { if (useToken) onTokenLogin(token) else onPasswordLogin(identity, password) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy && if (useToken) token.isNotBlank() else identity.isNotBlank() && password.isNotBlank(),
      ) {
        if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
        else Text("登录")
      }
      TextButton(onClick = { useToken = !useToken }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(if (useToken) "改用邮箱和密码" else "改用访问令牌")
      }
    }
  }
}
