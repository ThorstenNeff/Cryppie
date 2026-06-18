package com.tneff.cyppie.feature.onboarding.ui

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
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import com.tneff.cyppie.feature.onboarding.SecureScreenEffect
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_back
import com.tneff.cyppie.feature.onboarding.generated.resources.common_continue
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_backup_err_incomplete
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_backup_err_wrong
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_backup_reshow
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_backup_subtitle
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_backup_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_backup_warn_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_backup_warn_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_backup_word_label
import org.jetbrains.compose.resources.stringResource

private const val BACKUP_ATTEMPT_HINT_THRESHOLD = 3

/**
 * ONB-7 — Confirm backup (SPEC_ONBOARDING_SCREEN7, create flow). Challenges 3 random positions of the
 * generated phrase (held in `OnboardingViewModel`); the user re-enters those words to prove they
 * saved the phrase. Only a *wrong* field is flagged — the expected word is never revealed. "Confirm"
 * is disabled until all challenge fields are filled (and any flagged field is corrected); from the
 * 3rd failed attempt a warning banner offers to re-show the seed (→ ONB-6, same phrase). Screenshots
 * blocked ([SecureScreenEffect]); no plaintext logging. Copy from resources, tokens, `onb_backup_*` tags.
 */
@Composable
fun ConfirmBackupScreen(
    positions: List<Int>,
    expectedWords: List<String>,
    entries: Map<Int, String>,
    attempts: Int,
    onEnsureChallenge: () -> Unit,
    onWordChange: (Int, String) -> Unit,
    onConfirm: () -> Unit,
    onFailure: () -> Unit,
    onReshowSeed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecureScreenEffect()
    LaunchedEffect(Unit) { onEnsureChallenge() }

    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing

    var checkedWrong by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val firstWrong = checkedWrong.minOrNull()

    fun entry(pos: Int) = entries[pos].orEmpty()
    val allFilled = positions.isNotEmpty() && positions.all { entry(it).isNotBlank() }
    val canConfirm = allFilled && checkedWrong.isEmpty()

    fun attemptConfirm() {
        val wrong = positions.filter {
            entry(it).trim().lowercase() != expectedWords.getOrElse(it) { "" }.trim().lowercase()
        }.toSet()
        if (wrong.isEmpty()) {
            onConfirm()
        } else {
            checkedWrong = wrong
            onFailure()
        }
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
                    text = stringResource(Res.string.onb_backup_title),
                    style = CryptasaTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.onb_backup_subtitle),
                    style = CryptasaTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                if (attempts >= BACKUP_ATTEMPT_HINT_THRESHOLD) {
                    CryptasaBanner(
                        title = stringResource(Res.string.onb_backup_warn_title),
                        description = stringResource(Res.string.onb_backup_warn_body),
                        tone = CryptasaBannerTone.Warning,
                        actionText = stringResource(Res.string.onb_backup_reshow),
                        onActionClick = onReshowSeed,
                    )
                }

                positions.forEachIndexed { slot, pos ->
                    val isWrong = pos in checkedWrong
                    CryptasaTextField(
                        value = entry(pos),
                        onValueChange = {
                            onWordChange(pos, it.lowercase().filter { c -> c.isLetter() })
                            if (pos in checkedWrong) checkedWrong = checkedWrong - pos
                        },
                        label = stringResource(Res.string.onb_backup_word_label, pos + 1),
                        errorText = if (isWrong) stringResource(Res.string.onb_backup_err_wrong, pos + 1) else null,
                        keyboardType = KeyboardType.Password,
                        autoCorrect = false,
                        capitalization = KeyboardCapitalization.None,
                        errorTestTag = if (pos == firstWrong) OnboardingTestTags.BACKUP_ERROR else null,
                        modifier = Modifier.testTag(OnboardingTestTags.backupCell(slot + 1)),
                    )
                }

                if (!allFilled) {
                    Text(
                        text = stringResource(Res.string.onb_backup_err_incomplete),
                        style = CryptasaTheme.typography.helper,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            CryptasaButton(
                text = stringResource(Res.string.common_continue),
                onClick = { attemptConfirm() },
                enabled = canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.md)
                    .testTag(OnboardingTestTags.BACKUP_CONTINUE),
            )
        }
    }
}
