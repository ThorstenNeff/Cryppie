package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.components.CryptasaDialog
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.feature.onboarding.BiometricUnlockResult
import com.tneff.cyppie.feature.onboarding.SecureScreenEffect
import com.tneff.cyppie.feature.onboarding.UnlockError
import com.tneff.cyppie.feature.onboarding.UnlockViewModel
import com.tneff.cyppie.feature.onboarding.rememberBiometricUnlock
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_password_hide
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_password_show
import com.tneff.cyppie.feature.onboarding.generated.resources.common_cancel
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_biometric
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_biometric_failed
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_err_empty
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_err_lockout
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_err_wrong
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_forgot
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_password_label
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_password_placeholder
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_recover_confirm_action
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_recover_confirm_body
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_recover_confirm_title
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_subtitle
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_submit
import com.tneff.cyppie.feature.onboarding.generated.resources.unlock_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Brand wordmark (matches Welcome; PO decision, non-localized). */
private const val UNLOCK_WORDMARK = "Cyppie"

/**
 * KAN-92 — Returning-user unlock (SPEC_UNLOCK). Reuses the ONB-3 masked field; password →
 * `SeedVault.unlock` → `SeedSource` → app shell. Wrong password shows a no-reveal error; from the 5th
 * attempt the field/button lock with a live countdown (exp. backoff, no wipe). "Forgot password?"
 * opens a blocking confirm → non-custodial recovery via the import flow. Screenshots blocked. The
 * password never leaves the field/seam (zeroized there). Adaptive ≤480 dp.
 */
@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    onRecover: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnlockViewModel = koinViewModel(),
) {
    SecureScreenEffect()
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing

    var revealed by rememberSaveable { mutableStateOf(false) }
    var showRecover by remember { mutableStateOf(false) }
    var biometricFailed by remember { mutableStateOf(false) }

    val biometric = rememberBiometricUnlock()
    val biometricAvailable = remember { biometric.available() }
    val scope = rememberCoroutineScope()

    fun runBiometric() {
        scope.launch {
            when (biometric.unlock()) {
                BiometricUnlockResult.Success -> onUnlocked()
                BiometricUnlockResult.WrongPassword, BiometricUnlockResult.Error ->
                    biometricFailed = true
                // Cancelled / Unavailable: stay silently on the password path.
                else -> {}
            }
        }
    }

    // Auto-prompt biometrics once on entry (if enrolled & not locked out); the button is the retry.
    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable && !viewModel.lockedOut) runBiometric()
    }

    val errorText = when (viewModel.error) {
        UnlockError.Empty -> stringResource(Res.string.unlock_err_empty)
        UnlockError.Wrong, UnlockError.Failed -> stringResource(Res.string.unlock_err_wrong)
        null -> null
    }
    val canSubmit = viewModel.password.isNotBlank() && !viewModel.busy && !viewModel.lockedOut

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl, vertical = spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(text = UNLOCK_WORDMARK, style = CryptasaTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(
                text = stringResource(Res.string.unlock_title),
                style = CryptasaTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.unlock_subtitle),
                style = CryptasaTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (viewModel.lockedOut) {
                CryptasaBanner(
                    title = stringResource(Res.string.unlock_err_lockout, formatCountdown(viewModel.lockoutRemaining)),
                    tone = CryptasaBannerTone.Warning,
                    modifier = Modifier.testTag("unlock_err_lockout"),
                )
            }

            CryptasaTextField(
                value = viewModel.password,
                onValueChange = viewModel::updatePassword,
                label = stringResource(Res.string.unlock_password_label),
                placeholder = stringResource(Res.string.unlock_password_placeholder),
                errorText = errorText,
                enabled = !viewModel.lockedOut,
                keyboardType = KeyboardType.Password,
                autoCorrect = false,
                capitalization = KeyboardCapitalization.None,
                visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = CryptasaIcons.Visibility,
                trailingIconContentDescription = stringResource(
                    if (revealed) Res.string.cd_password_hide else Res.string.cd_password_show,
                ),
                onTrailingIconClick = { revealed = !revealed },
                errorTestTag = "unlock_err_wrong",
                modifier = Modifier.testTag("unlock_password"),
            )

            CryptasaButton(
                text = stringResource(Res.string.unlock_submit),
                onClick = { viewModel.submit(onUnlocked) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().testTag("unlock_submit"),
            )

            if (biometricAvailable) {
                CryptasaButton(
                    text = stringResource(Res.string.unlock_biometric),
                    onClick = { biometricFailed = false; runBiometric() },
                    style = CryptasaButtonStyle.Secondary,
                    enabled = !viewModel.lockedOut,
                    modifier = Modifier.fillMaxWidth().testTag("unlock_biometric"),
                )
                if (biometricFailed) {
                    Text(
                        text = stringResource(Res.string.unlock_biometric_failed),
                        style = CryptasaTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("unlock_biometric_failed"),
                    )
                }
            }

            Text(
                text = stringResource(Res.string.unlock_forgot),
                style = CryptasaTheme.typography.label,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable { showRecover = true }
                    .padding(vertical = spacing.xs)
                    .testTag("unlock_forgot"),
            )
        }
    }

    if (showRecover) {
        CryptasaDialog(
            title = stringResource(Res.string.unlock_recover_confirm_title),
            body = stringResource(Res.string.unlock_recover_confirm_body),
            confirmText = stringResource(Res.string.unlock_recover_confirm_action),
            onConfirm = {
                showRecover = false
                onRecover()
            },
            dismissText = stringResource(Res.string.common_cancel),
            onDismiss = { showRecover = false },
        )
    }
}

/** `mm:ss` countdown for the lockout banner. */
private fun formatCountdown(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
