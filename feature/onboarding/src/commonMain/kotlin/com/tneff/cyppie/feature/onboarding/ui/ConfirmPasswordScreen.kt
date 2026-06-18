package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.ConfirmError
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_back
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_password_hide
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_password_show
import com.tneff.cyppie.feature.onboarding.generated.resources.common_continue
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pwc_err_empty
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pwc_err_mismatch
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pwc_helper
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pwc_label
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pwc_subtitle
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_pwc_title
import com.tneff.cyppie.feature.onboarding.validateConfirmPassword
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * ONB-4 — Confirm app password (SPEC_ONBOARDING_SCREEN4). Re-enter field compared live against the
 * [password] captured on ONB-3; "continue" stays visible but disabled until the two match. Errors
 * (empty / mismatch) surface on focus-loss and clear when fixed; a mismatch never clears the ONB-3
 * input (both held in the flow `OnboardingViewModel`). Masked + eye toggle, no autofill/logging
 * (§5.3); copy from resources, tokens only, `onb_confirm_*` testTags, adaptive (≤480 dp, scroll).
 */
@Composable
fun ConfirmPasswordScreen(
    value: String,
    password: String,
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

    val error: ConfirmError? = validateConfirmPassword(value, password)
    val valid = error == null
    val showError = touched && error != null
    val errorRes: StringResource? = when (error) {
        ConfirmError.Empty -> Res.string.onb_pwc_err_empty
        ConfirmError.Mismatch -> Res.string.onb_pwc_err_mismatch
        null -> null
    }

    Box(
        modifier = modifier.fillMaxSize().background(colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = spacing.xl)) {
            CryptasaTopAppBar(onBack = onBack, backContentDescription = stringResource(Res.string.cd_back))

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    text = stringResource(Res.string.onb_pwc_title),
                    style = CryptasaTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.onb_pwc_subtitle),
                    style = CryptasaTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                CryptasaTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = stringResource(Res.string.onb_pwc_label),
                    helperText = stringResource(Res.string.onb_pwc_helper),
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
                    errorTestTag = OnboardingTestTags.CONFIRM_ERROR,
                    modifier = Modifier
                        .testTag(OnboardingTestTags.CONFIRM_INPUT)
                        .onFocusChanged { focus ->
                            if (focus.isFocused) wasFocused = true else if (wasFocused) touched = true
                        },
                )
            }

            CryptasaButton(
                text = stringResource(Res.string.common_continue),
                onClick = onNext,
                enabled = valid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.md)
                    .testTag(OnboardingTestTags.CONFIRM_CONTINUE),
            )
        }
    }
}
