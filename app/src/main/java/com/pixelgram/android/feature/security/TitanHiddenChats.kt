package com.pixelgram.android.feature.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Mac
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class HiddenChatMode { ExcludeSelected, IncludeOnlySelected }
data class TitanHiddenChatState(
    val unlocked: Boolean = false,
    val mode: HiddenChatMode = HiddenChatMode.ExcludeSelected,
    val selectedChatIds: Set<Long> = emptySet(),
    val strongBoxBacked: Boolean = false,
)

class TitanHiddenChats(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("titan_hidden_chats", Context.MODE_PRIVATE)
    private val stateFlow = MutableStateFlow(TitanHiddenChatState(
        mode = runCatching { HiddenChatMode.valueOf(prefs.getString(KEY_MODE, null) ?: "ExcludeSelected") }.getOrDefault(HiddenChatMode.ExcludeSelected),
    ))
    val state: StateFlow<TitanHiddenChatState> = stateFlow.asStateFlow()

    fun isVisible(chatId: Long): Boolean {
        if (chatId == 0L || stateFlow.value.unlocked) return true
        val hashes = prefs.getStringSet(KEY_HASHES, emptySet()).orEmpty()
        val mode = runCatching { HiddenChatMode.valueOf(prefs.getString(KEY_MODE, null) ?: "ExcludeSelected") }
            .getOrDefault(HiddenChatMode.ExcludeSelected)
        if (hashes.isEmpty()) return mode == HiddenChatMode.ExcludeSelected
        val selected = hashes.contains(hashId(chatId))
        return when (mode) {
            HiddenChatMode.ExcludeSelected -> !selected
            HiddenChatMode.IncludeOnlySelected -> selected
        }
    }

    fun lock() { stateFlow.value = stateFlow.value.copy(unlocked = false, selectedChatIds = emptySet()) }

    fun authenticate(activity: FragmentActivity, onComplete: (Boolean) -> Unit = {}) {
        // A CryptoObject-backed prompt must use BIOMETRIC_STRONG alone.
        // DEVICE_CREDENTIAL cannot be combined with a CryptoObject on Android 11+.
        // Pixel fingerprint hardware reports BIOMETRIC_STRONG, so this keeps the
        // Keystore authentication bound to the same biometric operation.
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onComplete(false)
            return
        }

        fun prepareCipher(): Cipher {
            val iv = prefs.getString(KEY_IV, null)
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                if (iv == null) {
                    init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                } else {
                    init(
                        Cipher.DECRYPT_MODE,
                        getOrCreateKey(),
                        GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
                    )
                }
            }
        }

        fun recreateKeyAndCipher(): Cipher? {
            val cleared = runCatching {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
                prefs.edit()
                    .remove(KEY_IV)
                    .remove(KEY_CIPHERTEXT)
                    .remove(KEY_HASHES)
                    .putString(KEY_MODE, HiddenChatMode.ExcludeSelected.name)
                    .apply()
                stateFlow.value = TitanHiddenChatState()
            }.isSuccess
            return if (cleared) runCatching { prepareCipher() }.getOrNull() else null
        }

        val cipher = try {
            prepareCipher()
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Enrollment changes invalidate per-use keys; their previous ciphertext is
            // unrecoverable, so clear this feature's state and generate a fresh key.
            recreateKeyAndCipher() ?: run { onComplete(false); return }
        } catch (_: StrongBoxUnavailableException) {
            // Do not immediately select StrongBox again after it has become unavailable.
            prefs.edit().putBoolean(KEY_FORCE_TEE, true).apply()
            recreateKeyAndCipher() ?: run { onComplete(false); return }
        } catch (_: ProviderException) {
            prefs.edit().putBoolean(KEY_FORCE_TEE, true).apply()
            recreateKeyAndCipher() ?: run { onComplete(false); return }
        } catch (_: java.security.UnrecoverableKeyException) {
            prefs.edit().putBoolean(KEY_FORCE_TEE, true).apply()
            recreateKeyAndCipher() ?: run { onComplete(false); return }
        } catch (_: Throwable) {
            onComplete(false)
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticated = result.cryptoObject?.cipher ?: run {
                        onComplete(false)
                        return
                    }
                    val config = runCatching {
                        if (prefs.getString(KEY_CIPHERTEXT, null) == null) {
                            HiddenConfig(HiddenChatMode.ExcludeSelected, emptySet()).also {
                                check(persist(it, authenticated))
                            }
                        } else {
                            decode(
                                authenticated.doFinal(
                                    Base64.decode(prefs.getString(KEY_CIPHERTEXT, ""), Base64.NO_WRAP),
                                ).toString(Charsets.UTF_8),
                            )
                        }
                    }.getOrElse {
                        onComplete(false)
                        return
                    }
                    updateIndex(config)
                    stateFlow.value = TitanHiddenChatState(true, config.mode, config.ids)
                    onComplete(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onComplete(false)
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock hidden chats")
            .setSubtitle("Verify with biometrics or your device screen lock")
            .setAllowedAuthenticators(authenticators)
            .apply {
                if ((authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) == 0) {
                    setNegativeButtonText("Cancel")
                }
            }
            .build()
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    fun setMode(mode: HiddenChatMode): Boolean {
        val current = stateFlow.value
        if (!current.unlocked || !persist(HiddenConfig(mode, current.selectedChatIds))) return false
        stateFlow.value = current.copy(mode = mode)
        return true
    }

    fun setSelected(chatId: Long, selected: Boolean): Boolean {
        val current = stateFlow.value
        if (!current.unlocked || chatId == 0L) return false
        val ids = current.selectedChatIds.toMutableSet().apply { if (selected) add(chatId) else remove(chatId) }
        if (!persist(HiddenConfig(current.mode, ids))) return false
        stateFlow.value = current.copy(selectedChatIds = ids)
        return true
    }

    fun toggleChat(chatId: Long): Boolean {
        val current = stateFlow.value
        return setSelected(chatId, chatId !in current.selectedChatIds)
    }

    fun prepareUnlockCipher(): Cipher {
        val iv = prefs.getString(KEY_IV, null)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                if (iv == null) {
                    init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                } else {
                    init(
                        Cipher.DECRYPT_MODE,
                        getOrCreateKey(),
                        GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
                    )
                }
            }
        } catch (_: Throwable) {
            prefs.edit().putBoolean(KEY_FORCE_TEE, true).apply()
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
            prefs.edit().remove(KEY_IV).remove(KEY_CIPHERTEXT).remove(KEY_HASHES).apply()
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        }
    }

    fun markUnlocked(cipher: Cipher?) {
        val strongBox = Build.VERSION.SDK_INT >= 28 && !prefs.getBoolean(KEY_FORCE_TEE, false)
        val config = if (cipher != null) {
            runCatching {
                if (prefs.getString(KEY_CIPHERTEXT, null) == null) {
                    HiddenConfig(stateFlow.value.mode, emptySet()).also { persist(it, cipher) }
                } else {
                    decode(cipher.doFinal(Base64.decode(prefs.getString(KEY_CIPHERTEXT, ""), Base64.NO_WRAP)).toString(Charsets.UTF_8))
                }
            }.getOrElse { HiddenConfig(stateFlow.value.mode, stateFlow.value.selectedChatIds) }
        } else {
            val mode = runCatching { HiddenChatMode.valueOf(prefs.getString(KEY_MODE, null) ?: "ExcludeSelected") }
                .getOrDefault(HiddenChatMode.ExcludeSelected)
            HiddenConfig(mode, stateFlow.value.selectedChatIds)
        }
        updateIndex(config)
        stateFlow.value = TitanHiddenChatState(
            unlocked = true,
            mode = config.mode,
            selectedChatIds = config.ids,
            strongBoxBacked = strongBox,
        )
    }

    private fun persist(config: HiddenConfig, authenticated: Cipher? = null): Boolean = runCatching {
        val cipher = authenticated ?: Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val encrypted = cipher.doFinal(encode(config).toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_MODE, config.mode.name)
            .putStringSet(KEY_HASHES, config.ids.map(::hashId).toSet())
            .apply()
        true
    }.getOrDefault(false)

    private fun updateIndex(config: HiddenConfig) {
        prefs.edit().putString(KEY_MODE, config.mode.name)
            .putStringSet(KEY_HASHES, config.ids.map(::hashId).toSet()).apply()
    }

    private fun encode(config: HiddenConfig) = JSONObject().apply {
        put("mode", config.mode.name)
        put("ids", JSONArray().apply { config.ids.sorted().forEach { put(it) } })
    }.toString()

    private fun decode(value: String): HiddenConfig {
        val json = JSONObject(value)
        val mode = runCatching { HiddenChatMode.valueOf(json.optString("mode")) }.getOrDefault(HiddenChatMode.ExcludeSelected)
        val values = json.optJSONArray("ids") ?: JSONArray()
        return HiddenConfig(mode, buildSet { for (i in 0 until values.length()) add(values.optLong(i)) }.filterTo(mutableSetOf()) { it != 0L })
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        fun generate(strongBox: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= 30) {
                spec.setUserAuthenticationParameters(
                    300,
                    KeyProperties.AUTH_BIOMETRIC_STRONG,
                )
            } else {
                @Suppress("DEPRECATION")
                spec.setUserAuthenticationValidityDurationSeconds(300)
            }
            if (strongBox && Build.VERSION.SDK_INT >= 28) spec.setIsStrongBoxBacked(true)
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(spec.build())
                generateKey()
            }
        }
        return if (Build.VERSION.SDK_INT >= 28 && !prefs.getBoolean(KEY_FORCE_TEE, false)) {
            runCatching { generate(true) }.getOrElse {
                prefs.edit().putBoolean(KEY_FORCE_TEE, true).apply()
                generate(false)
            }
        } else generate(false)
    }

    private val indexKey: SecretKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_INDEX_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance("HmacSHA256", "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_INDEX_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build())
            generateKey()
        }
    }

    private fun hashId(id: Long): String = Base64.encodeToString(
        Mac.getInstance("HmacSHA256").run { init(indexKey); doFinal(id.toString().toByteArray()) },
        Base64.NO_WRAP,
    )

    private data class HiddenConfig(val mode: HiddenChatMode, val ids: Set<Long>)
    companion object {
        fun findFragmentActivity(context: Context): FragmentActivity? {
            var current: Context? = context
            while (current != null) {
                if (current is FragmentActivity) return current
                current = (current as? ContextWrapper)?.baseContext
            }
            return null
        }

        private const val KEY_ALIAS = "pixelgram_titan_hidden_chats_v1"
        private const val KEY_FORCE_TEE = "force_tee_after_strongbox_failure"
        const val KEY_INDEX_ALIAS = "pixelgram_titan_hidden_chats_index_v1"
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_MODE = "mode"
        const val KEY_HASHES = "selected_hashes"
    }
}

@Composable
fun TitanHiddenChatsDialog(graph: com.pixelgram.android.AppGraph, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) { TitanHiddenChats.findFragmentActivity(context) }
    val hidden by graph.hiddenChats.state.collectAsState()
    val snapshot by graph.store.snapshot.collectAsState()
    var authError by remember { mutableStateOf(false) }
    var pickingChats by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Hidden chats") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                if (!hidden.unlocked) {
                    androidx.compose.material3.Text("Unlock to manage hidden chats using your enrolled strong biometric.")
                    if (authError) androidx.compose.material3.Text(
                        "Authentication could not be completed. Check that a fingerprint is enrolled and try again.",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                } else {
                    androidx.compose.material3.Text("Chat list mode", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    HiddenModeOption("Exclude selected chats", HiddenChatMode.ExcludeSelected, hidden.mode) { graph.hiddenChats.setMode(it) }
                    HiddenModeOption("Include only selected chats", HiddenChatMode.IncludeOnlySelected, hidden.mode) { graph.hiddenChats.setMode(it) }
                    androidx.compose.material3.HorizontalDivider()
                    androidx.compose.material3.Text("Selected chats", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    androidx.compose.material3.Text(
                        if (hidden.selectedChatIds.isEmpty()) "No chats selected" else "${hidden.selectedChatIds.size} chats selected",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.OutlinedButton(
                        onClick = { pickingChats = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Text("Choose chats")
                    }
                    snapshot.allChats.filter { it.id in hidden.selectedChatIds }.forEach { row ->
                        androidx.compose.material3.Text(
                            row.title,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!hidden.unlocked) androidx.compose.material3.TextButton(onClick = {
                if (activity == null) authError = true else graph.hiddenChats.authenticate(activity) { authError = !it }
            }) { androidx.compose.material3.Text("Unlock") }
            else androidx.compose.material3.TextButton(onClick = { graph.hiddenChats.lock(); onDismiss() }) { androidx.compose.material3.Text("Lock") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Close") } },
    )
    if (pickingChats && hidden.unlocked) {
        PeoplePicker(
            graph = graph,
            title = "Select hidden chats",
            initial = hidden.selectedChatIds.toList(),
            onDismiss = { pickingChats = false },
            everything = true,
        ) { selected ->
            val previous = hidden.selectedChatIds
            (previous - selected.toSet()).forEach { graph.hiddenChats.setSelected(it, false) }
            (selected.toSet() - previous).forEach { graph.hiddenChats.setSelected(it, true) }
            pickingChats = false
        }
    }

}

@Composable
private fun HiddenModeOption(label: String, mode: HiddenChatMode, selected: HiddenChatMode, onSelect: (HiddenChatMode) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected == mode, onClick = { onSelect(mode) })
        androidx.compose.material3.Text(label)
    }

}
