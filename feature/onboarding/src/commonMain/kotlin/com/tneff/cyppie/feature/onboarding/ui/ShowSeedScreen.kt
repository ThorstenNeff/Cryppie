package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaCheckbox
import com.tneff.cyppie.designsystem.components.CryptasaDialog
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.SeedWordCell
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import com.tneff.cyppie.feature.onboarding.SecureScreenEffect
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_back
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_password_show
import com.tneff.cyppie.feature.onboarding.generated.resources.common_continue
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedshow_ack
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedshow_copy
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedshow_err_gen_action
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedshow_err_gen_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedshow_err_gen_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedshow_subtitle
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedshow_title
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedshow_warn_body
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedshow_warn_title
import org.jetbrains.compose.resources.stringResource

private const val SEED_MASK = "••••••"

/**
 * ONB-6 — Show the freshly generated seed phrase (SPEC_ONBOARDING_SCREEN6, create flow). The phrase
 * is generated once via the `:wallet` CSPRNG (held in the flow `OnboardingViewModel`); on failure a
 * **blocking** dialog appears with no insecure fallback. Words are **covered until tapped**, behind
 * a hard danger warning; "continue" is gated on the "I've written it down" checkbox. No share/cloud
 * action; copy is an opt-in convenience. Screenshots blocked ([SecureScreenEffect]); phrase never
 * logged. Copy from resources, tokens only, testTags `onb_show_seed_*`.
 */
@Composable
fun ShowSeedScreen(
    words: List<String>,
    wordCount: Int,
    generationFailed: Boolean,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecureScreenEffect()
    LaunchedEffect(Unit) { onGenerate() }

    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val clipboard = LocalClipboardManager.current

    if (generationFailed) {
        CryptasaDialog(
            title = stringResource(Res.string.onb_seedshow_err_gen_title),
            body = stringResource(Res.string.onb_seedshow_err_gen_body),
            confirmText = stringResource(Res.string.onb_seedshow_err_gen_action),
            onConfirm = onRetry,
            modifier = Modifier.testTag(OnboardingTestTags.SHOW_SEED_ERROR_DIALOG),
        )
        return
    }

    var revealed by rememberSaveable { mutableStateOf(false) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    val revealHint = stringResource(Res.string.cd_password_show)

    Box(
        modifier = modifier.fillMaxSize().background(colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = 520.dp).fillMaxSize().padding(horizontal = spacing.xl)) {
            CryptasaTopAppBar(onBack = onBack, backContentDescription = stringResource(Res.string.cd_back))

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    text = stringResource(Res.string.onb_seedshow_title),
                    style = CryptasaTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.onb_seedshow_subtitle),
                    style = CryptasaTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                CryptasaBanner(
                    title = stringResource(Res.string.onb_seedshow_warn_title),
                    description = stringResource(Res.string.onb_seedshow_warn_body),
                    tone = CryptasaBannerTone.Danger,
                )

                // Word grid, covered until tapped (Trust-UX §7).
                Box(
                    modifier = if (revealed) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .testTag(OnboardingTestTags.SHOW_SEED_REVEAL)
                            .semantics { contentDescription = revealHint }
                            .clickable { revealed = true }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        for (row in 0 until (wordCount + 2) / 3) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                for (col in 0 until 3) {
                                    val index = row * 3 + col
                                    if (index < wordCount) {
                                        SeedWordCell(
                                            index = index + 1,
                                            word = if (revealed) words.getOrElse(index) { "" } else SEED_MASK,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    if (!revealed) {
                        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = CryptasaIcons.Visibility,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }

                if (revealed) {
                    Text(
                        text = stringResource(Res.string.onb_seedshow_copy),
                        style = CryptasaTheme.typography.labelSmall,
                        color = colors.primary,
                        modifier = Modifier
                            .clickable {
                                clipboard.setText(AnnotatedString(words.take(wordCount).joinToString(" ")))
                            }
                            .padding(vertical = spacing.xs),
                    )
                }

                CryptasaCheckbox(
                    checked = acknowledged,
                    onCheckedChange = { acknowledged = it },
                    label = stringResource(Res.string.onb_seedshow_ack),
                )
            }

            CryptasaButton(
                text = stringResource(Res.string.common_continue),
                onClick = onContinue,
                enabled = acknowledged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.md)
                    .testTag(OnboardingTestTags.SHOW_SEED_CONTINUE),
            )
        }
    }
}
