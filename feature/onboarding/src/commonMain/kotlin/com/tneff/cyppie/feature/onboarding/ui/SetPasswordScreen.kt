package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.PasswordStrength
import com.tneff.cyppie.designsystem.components.PasswordStrengthIndicator
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import com.tneff.cyppie.feature.onboarding.PwError
import com.tneff.cyppie.feature.onboarding.evaluatePasswordStrength
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_back
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_password_hide
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_password_show
import com.tneff.cyppie.feature.onboarding.generated.resources.common_continue
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_err_empty
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_err_short
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_err_weak
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_err_whitespace
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_helper_minlen
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_label
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_placeholder
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_strength_label
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_strength_medium
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_strength_strong
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_strength_weak
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_subtitle
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pw_title
import com.tneff.cyppie.feature.onboarding.validatePassword
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * ONB-3 — Set app password (SPEC_ONBOARDING_SCREEN3). Masked [CryptasaTextField] with an eye toggle
 * (reveal), live [PasswordStrengthIndicator], and a "continue" button that stays visible but disabled
 * until the password is valid (≥8, not blank, not weak). Errors (empty / whitespace-only / too short /
 * too weak) surface after the field loses focus and clear when fixed.
 *
 * Security (§5.3): masked, `autoCorrect=false`, no capitalization/suggestions, no logging. The value
 * is hoisted to the flow `OnboardingViewModel` (in-memory only) so it survives navigation; at-rest
 * handling/zeroization is the secure-storage work (Screen 8, ADR-0009). All copy from resources.
 */
@Composable
fun SetPasswordScreen(
    value: String,
    onValueChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing

    var revealed by rememberSaveable { mutableStateOf(false) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }

    val error: PwError? = validatePassword(value)
    val strength = evaluatePasswordStrength(value)
    val valid = error == null
    val showError = touched && error != null

    val errorRes: StringResource? = when (error) {
        PwError.Empty -> Res.string.onb_pw_err_empty
        PwError.Whitespace -> Res.string.onb_pw_err_whitespace
        PwError.TooShort -> Res.string.onb_pw_err_short
        PwError.Weak -> Res.string.onb_pw_err_weak
        null -> null
    }

    Box(
        modifier = modifier.fillMaxSize().background(colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = spacing.xl),
        ) {
            CryptasaTopAppBar(onBack = onBack, backContentDescription = stringResource(Res.string.cd_back))

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    text = stringResource(Res.string.onb_pw_title),
                    style = CryptasaTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.onb_pw_subtitle),
                    style = CryptasaTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                CryptasaTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = stringResource(Res.string.onb_pw_label),
                    placeholder = stringResource(Res.string.onb_pw_placeholder),
                    helperText = stringResource(Res.string.onb_pw_helper_minlen),
                    errorText = if (showError && errorRes != null) stringResource(errorRes) else null,
                    keyboardType = KeyboardType.Password,
                    autoCorrect = false,
                    capitalization = KeyboardCapitalization.None,
                    visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = CryptasaIcons.Visibility,
                    trailingIconContentDescription = stringResource(
                        if (revealed) Res.string.cd_password_hide else Res.string.cd_password_show,
                    ),
                    onTrailingIconClick = { revealed = !revealed },
                    trailingIconTestTag = OnboardingTestTags.PASSWORD_REVEAL,
                    errorTestTag = OnboardingTestTags.PASSWORD_ERROR,
                    modifier = Modifier
                        .testTag(OnboardingTestTags.PASSWORD_INPUT)
                        .onFocusChanged { focus ->
                            if (focus.isFocused) wasFocused = true else if (wasFocused) touched = true
                        },
                )

                if (strength != PasswordStrength.None) {
                    val wordRes = when (strength) {
                        PasswordStrength.Weak -> Res.string.onb_pw_strength_weak
                        PasswordStrength.Medium -> Res.string.onb_pw_strength_medium
                        PasswordStrength.Strong -> Res.string.onb_pw_strength_strong
                        PasswordStrength.None -> Res.string.onb_pw_strength_weak
                    }
                    PasswordStrengthIndicator(
                        strength = strength,
                        label = stringResource(Res.string.onb_pw_strength_label, stringResource(wordRes)),
                        modifier = Modifier.testTag(OnboardingTestTags.PASSWORD_STRENGTH),
                    )
                }
            }

            CryptasaButton(
                text = stringResource(Res.string.common_continue),
                onClick = onNext,
                enabled = valid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.md)
                    .testTag(OnboardingTestTags.PASSWORD_CONTINUE),
            )
        }
    }
}
