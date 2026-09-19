package dev.thyra.core.network

import java.net.URI

object UrlNormalizer {
  fun normalize(input: String): String {
    val raw = input.trim()
    require(raw.isNotEmpty()) { "请输入服务器地址" }
    val withScheme = if ("://" in raw) raw else "https://$raw"
    val uri = runCatching { URI(withScheme) }.getOrElse { throw IllegalArgumentException("服务器地址格式无效") }
    require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) { "仅支持 HTTP 或 HTTPS 地址" }
    require(!uri.host.isNullOrBlank()) { "服务器地址缺少有效主机名" }
    require(uri.userInfo == null) { "服务器地址不能包含用户名或密码" }
    require(uri.query == null && uri.fragment == null) { "服务器地址不能包含查询参数或片段" }

    val scheme = uri.scheme.lowercase()
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val path = (uri.rawPath ?: "").trimEnd('/').let { if (it == "/") "" else it }
    return "$scheme://${uri.host.lowercase()}$port$path"
  }

  fun discoveryCandidates(input: String): List<String> {
    val normalized = normalize(input)
    return if (normalized.endsWith("/api")) listOf(normalized) else listOf(normalized, "$normalized/api")
  }
}
