package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaDialog
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import com.tneff.cyppie.feature.onboarding.WalletSetupOutcome
import com.tneff.cyppie.feature.onboarding.rememberWalletStore
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.common_cancel
import com.tneff.cyppie.feature.onboarding.generated.resources.common_retry
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_setup_err_enc_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_setup_err_enc_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_setup_err_keystore_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_setup_err_keystore_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_setup_err_storage_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_setup_err_storage_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_setup_loading_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_setup_loading_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_setup_success_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_setup_success_title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private enum class SetupPhase { Loading, Success, EncryptionError, KeystoreError, StorageFull }

/**
 * ONB-8 — Encrypt the seed under the app password and persist it (SPEC_ONBOARDING_SCREEN8). On entry
 * the work starts automatically and is **not cancelable** (no back). Success shows a short "wallet
 * ready" confirmation, zeroizes the in-memory mnemonic, then auto-advances to ONB-9. Failures map to
 * the three spec frames: encryption / keystore → blocking dialog (retry / cancel), storage-full →
 * danger banner (retry). All work goes through the non-web [rememberWalletStore] seam (`:storage`).
 */
@Composable
fun WalletSetupScreen(
    words: List<String>,
    password: String,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    onSeedPersisted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val walletStore = rememberWalletStore()

    var phase by remember { mutableStateOf(SetupPhase.Loading) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        phase = SetupPhase.Loading
        // M4: KDF (PBKDF2 210k) + AES-GCM + file IO off the main thread to avoid an ANR.
        val outcome = withContext(Dispatchers.Default) { walletStore.persist(words, password) }
        phase = when (outcome) {
            WalletSetupOutcome.Success -> SetupPhase.Success
            WalletSetupOutcome.KeystoreError -> SetupPhase.KeystoreError
            WalletSetupOutcome.StorageFull -> SetupPhase.StorageFull
            WalletSetupOutcome.EncryptionError, WalletSetupOutcome.Unsupported -> SetupPhase.EncryptionError
        }
        if (phase == SetupPhase.Success) {
            onSeedPersisted()
            delay(1400)
            onSuccess()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            if (phase == SetupPhase.Success) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.successSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CryptasaIcons.Check,
                        contentDescription = null,
                        tint = colors.success,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = stringResource(Res.string.onb_setup_success_title),
                    style = CryptasaTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(OnboardingTestTags.SETUP_SUCCESS),
                )
                Text(
                    text = stringResource(Res.string.onb_setup_success_body),
                    style = CryptasaTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                ProgressRing(
                    diameter = 64.dp,
                    modifier = Modifier.testTag(OnboardingTestTags.SETUP_PROGRESS).semantics { contentDescription = "" },
                )
                Text(
                    text = stringResource(Res.string.onb_setup_loading_title),
                    style = CryptasaTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    text = stringResource(Res.string.onb_setup_loading_body),
                    style = CryptasaTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Storage-full = danger banner over the loading state, with retry.
        if (phase == SetupPhase.StorageFull) {
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                CryptasaBanner(
                    title = stringResource(Res.string.onb_setup_err_storage_title),
                    description = stringResource(Res.string.onb_setup_err_storage_body),
                    tone = CryptasaBannerTone.Danger,
                    actionText = stringResource(Res.string.common_retry),
                    onActionClick = { attempt++ },
                    modifier = Modifier.testTag(OnboardingTestTags.SETUP_STORAGE_BANNER),
                )
                CryptasaButton(
                    text = stringResource(Res.string.common_cancel),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    when (phase) {
        SetupPhase.EncryptionError -> SetupErrorDialog(
            title = stringResource(Res.string.onb_setup_err_enc_title),
            body = stringResource(Res.string.onb_setup_err_enc_body),
            onRetry = { attempt++ },
            onCancel = onCancel,
        )
        SetupPhase.KeystoreError -> SetupErrorDialog(
            title = stringResource(Res.string.onb_setup_err_keystore_title),
            body = stringResource(Res.string.onb_setup_err_keystore_body),
            onRetry = { attempt++ },
            onCancel = onCancel,
        )
        else -> {}
    }
}

@Composable
private fun SetupErrorDialog(title: String, body: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    CryptasaDialog(
        title = title,
        body = body,
        confirmText = stringResource(Res.string.common_retry),
        onConfirm = onRetry,
        dismissText = stringResource(Res.string.common_cancel),
        onDismiss = onCancel,
        modifier = Modifier.testTag(OnboardingTestTags.SETUP_ERROR_DIALOG),
    )
}
