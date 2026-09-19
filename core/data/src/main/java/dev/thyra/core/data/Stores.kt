package dev.thyra.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.thyra.core.model.ServerProfile
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.thyraDataStore by preferencesDataStore(name = "thyra_preferences")

@Serializable
data class PersistedState(
  val profiles: List<ServerProfile> = emptyList(),
  val selectedServerId: String? = null,
  val selectedAgentByServer: Map<String, String> = emptyMap(),
  val selectedSessionByAgent: Map<String, String> = emptyMap(),
) {
  fun sessionKey(serverId: String, agentId: String) = "$serverId:$agentId"
}

interface SelectionStore {
  val state: Flow<PersistedState>
  suspend fun update(transform: (PersistedState) -> PersistedState)
}

class DataStoreSelectionStore(
  context: Context,
  private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : SelectionStore {
  private val dataStore = context.applicationContext.thyraDataStore
  private val stateKey = stringPreferencesKey("state_v1")

  override val state: Flow<PersistedState> = dataStore.data.map { preferences ->
    preferences[stateKey]?.let { encoded ->
      runCatching { json.decodeFromString<PersistedState>(encoded) }.getOrNull()
    } ?: PersistedState()
  }

  override suspend fun update(transform: (PersistedState) -> PersistedState) {
    dataStore.edit { preferences ->
      val current = preferences[stateKey]?.let { runCatching { json.decodeFromString<PersistedState>(it) }.getOrNull() }
        ?: PersistedState()
      preferences[stateKey] = json.encodeToString(transform(current))
    }
  }
}

@Serializable
data class StoredCredential(val token: String, val expiresAt: String? = null)

interface CredentialStore {
  fun get(serverId: String): StoredCredential?
  fun put(serverId: String, credential: StoredCredential)
  fun remove(serverId: String)
}

class KeystoreCredentialStore(
  context: Context,
  private val json: Json = Json { ignoreUnknownKeys = true },
) : CredentialStore {
  private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

  override fun get(serverId: String): StoredCredential? {
    val encoded = preferences.getString(serverId, null) ?: return null
    return runCatching {
      val bytes = Base64.decode(encoded, Base64.NO_WRAP)
      require(bytes.size > IV_BYTES)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_BYTES)))
      val clear = cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)).toString(StandardCharsets.UTF_8)
      json.decodeFromString<StoredCredential>(clear)
    }.getOrElse {
      preferences.edit().remove(serverId).apply()
      null
    }
  }

  override fun put(serverId: String, credential: StoredCredential) {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val clear = json.encodeToString(credential).toByteArray(StandardCharsets.UTF_8)
    val encrypted = cipher.doFinal(clear)
    val payload = cipher.iv + encrypted
    preferences.edit().putString(serverId, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
  }

  override fun remove(serverId: String) {
    preferences.edit().remove(serverId).apply()
  }

  private fun secretKey(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
      init(
        KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setRandomizedEncryptionRequired(true)
          .build(),
      )
      generateKey()
    }
  }

  companion object {
    const val FILE_NAME = "thyra_secure_credentials"
    private const val KEY_ALIAS = "thyra_server_credentials_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
  }
}
