package com.pixelgram.android.feature.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pixelgram.android.AppGraph
import com.pixelgram.android.td.LIST_ARCHIVE
import com.pixelgram.android.td.LIST_MAIN
import com.pixelgram.android.ui.components.Avatar

fun launchTitanBiometricUnlock(
    context: Context,
    hiddenChats: TitanHiddenChats,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val activity = findFragmentActivity(context)
    if (activity == null) {
        onError("Activity context unavailable (ensure MainActivity extends FragmentActivity)")
        return
    }

    val bm = BiometricManager.from(activity)
    val strong = bm.canAuthenticate(BIOMETRIC_STRONG)
    val weakOrCred = bm.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)

    val authenticators = when {
        strong == BiometricManager.BIOMETRIC_SUCCESS -> BIOMETRIC_STRONG
        weakOrCred == BiometricManager.BIOMETRIC_SUCCESS -> BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        else -> {
            onError("No fingerprint or screen lock enrolled on this device")
            return
        }
    }

    val cryptoObject: BiometricPrompt.CryptoObject? = if (authenticators == BIOMETRIC_STRONG) {
        runCatching {
            val cipher = hiddenChats.prepareUnlockCipher()
            BiometricPrompt.CryptoObject(cipher)
        }.getOrNull()
    } else {
        null
    }

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                hiddenChats.markUnlocked(result.cryptoObject?.cipher)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onError(errString.toString())
                }
            }
        },
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Hidden chats")
        .setSubtitle("Authenticate to access hidden chats")
        .setAllowedAuthenticators(authenticators)
        .apply {
            if ((authenticators and DEVICE_CREDENTIAL) == 0) {
                setNegativeButtonText("Cancel")
            }
        }
        .build()

    if (cryptoObject != null) {
        prompt.authenticate(info, cryptoObject)
    } else {
        prompt.authenticate(info)
    }
}

private tailrec fun findFragmentActivity(context: Context?): FragmentActivity? = when (context) {
    null -> null
    is FragmentActivity -> context
    is ContextWrapper -> findFragmentActivity(context.baseContext)
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenChatsManagerSheet(
    graph: AppGraph,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val config by graph.hiddenChats.state.collectAsState()
    val snapshot by graph.store.snapshot.collectAsState()
    var errorText by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (!config.unlocked) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }

                Text(
                    text = "Hidden chats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "Authenticate with your fingerprint (Titan M2 hardware key) or screen lock to view or configure hidden chats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                errorText?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = {
                            errorText = null
                            launchTitanBiometricUnlock(
                                context = context,
                                hiddenChats = graph.hiddenChats,
                                onSuccess = { graph.store.touch() },
                                onError = { errorText = it },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Unlock")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        } else {
            val allChats = remember(snapshot.lists, search) {
                (snapshot.lists[LIST_MAIN].orEmpty() + snapshot.lists[LIST_ARCHIVE].orEmpty())
                    .distinctBy { it.id }
                    .filter { search.isBlank() || it.title.contains(search, ignoreCase = true) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Hidden chats (${config.selectedChatIds.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (config.strongBoxBacked) "Protected by Titan M2 StrongBox" else "Protected by Android Keystore",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(
                        onClick = {
                            graph.hiddenChats.lock()
                            graph.store.touch()
                            onDismiss()
                        },
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Lock now")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = config.mode == HiddenChatMode.ExcludeSelected,
                        onClick = {
                            graph.hiddenChats.setMode(HiddenChatMode.ExcludeSelected)
                            graph.store.touch()
                        },
                        label = { Text("Hide selected") },
                    )
                    FilterChip(
                        selected = config.mode == HiddenChatMode.IncludeOnlySelected,
                        onClick = {
                            graph.hiddenChats.setMode(HiddenChatMode.IncludeOnlySelected)
                            graph.store.touch()
                        },
                        label = { Text("Show ONLY selected") },
                    )
                }

                Text(
                    text = if (config.mode == HiddenChatMode.ExcludeSelected) {
                        "All chats are visible normally; checked chats below stay hidden until unlocked."
                    } else {
                        "All chats are hidden normally; ONLY checked chats below are visible when locked."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filter chats") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )

                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                ) {
                    items(allChats, key = { it.id }) { row ->
                        val selected = row.id in config.selectedChatIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    graph.hiddenChats.toggleChat(row.id)
                                    graph.store.touch()
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Avatar(
                                title = row.title,
                                seed = row.id,
                                path = row.photoPath,
                                minithumb = row.minithumb,
                                size = 40.dp,
                            )
                            Text(
                                text = row.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
