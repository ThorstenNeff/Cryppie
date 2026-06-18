package com.tneff.cyppie.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.SegmentedControl
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.MnemonicSupport
import com.tneff.cyppie.feature.onboarding.OnboardingTestTags
import com.tneff.cyppie.feature.onboarding.SecureScreenEffect
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.cd_back
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_cta
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_err_chars
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_err_checksum
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_err_invalidword
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_err_pastecount
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_err_toofew
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_paste
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_seg_12
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_seg_24
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_subtitle
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_seedin_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private sealed interface PasteIssue {
    data class Count(val got: Int) : PasteIssue
    data object Chars : PasteIssue
}

/**
 * ONB-5 — Import an existing wallet via its BIP-39 seed phrase (SPEC_ONBOARDING_SCREEN5). 12/24
 * [SegmentedControl], a word grid of editable cells with live per-word validation (red cell when a
 * word is not in the BIP-39 list, via [MnemonicSupport.isWord]) and prefix autocomplete
 * ([MnemonicSupport.suggestions]). "Import wallet" enables once every word is filled and valid; on
 * press the full-phrase checksum ([MnemonicSupport.isValid]) is checked — failure shows the danger
 * banner above the grid (no single cell blamed, since it's the combination).
 *
 * Security (§5.3): screenshots blocked ([SecureScreenEffect]); clipboard is **read-only** (never
 * written back); input is letters-only/lowercased; the phrase is never logged. Words live in the
 * flow `OnboardingViewModel` (in-memory, survive navigation); zeroization is Screen 8 (ADR-0009).
 */
@Composable
fun ImportSeedScreen(
    wordCount: Int,
    words: List<String>,
    onWordCountChange: (Int) -> Unit,
    onWordChange: (Int, String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecureScreenEffect()

    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val clipboard = LocalClipboardManager.current

    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    var checksumError by remember { mutableStateOf(false) }
    var pasteIssue by remember { mutableStateOf<PasteIssue?>(null) }

    val current = (0 until wordCount).map { words.getOrElse(it) { "" } }
    val allFilled = current.all { it.isNotBlank() }
    val allWordsValid = current.all { it.isNotBlank() && MnemonicSupport.isWord(it) }
    val canImport = allFilled && allWordsValid

    fun changeWord(index: Int, raw: String) {
        // Live-normalise: lowercase, letters only (rejects spaces/digits/symbols per cell).
        val normalized = raw.lowercase().filter { it.isLetter() }
        onWordChange(index, normalized)
        checksumError = false
        pasteIssue = null
    }

    fun handlePaste() {
        val text = clipboard.getText()?.text ?: return
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        pasteIssue = when {
            tokens.any { token -> token.any { !it.isLetter() } } -> PasteIssue.Chars
            tokens.size != 12 && tokens.size != 24 -> PasteIssue.Count(tokens.size)
            else -> null
        }
        if (tokens.size == 12 || tokens.size == 24) onWordCountChange(tokens.size)
        tokens.forEachIndexed { i, token ->
            if (i < words.size) onWordChange(i, token.lowercase().filter { it.isLetter() })
        }
        checksumError = false
    }

    fun attemptImport() {
        val phrase = current.map { it.trim().lowercase() }
        if (MnemonicSupport.isValid(phrase)) {
            checksumError = false
            onImport()
        } else {
            checksumError = true
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
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
                    text = stringResource(Res.string.onb_seedin_title),
                    style = CryptasaTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.onb_seedin_subtitle),
                    style = CryptasaTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                val seg12 = stringResource(Res.string.onb_seedin_seg_12)
                val seg24 = stringResource(Res.string.onb_seedin_seg_24)
                SegmentedControl(
                    options = listOf(12, 24),
                    selected = wordCount,
                    onSelect = onWordCountChange,
                    label = { if (it == 12) seg12 else seg24 },
                    optionTestTag = {
                        if (it == 12) OnboardingTestTags.SEED_WORDCOUNT_12 else OnboardingTestTags.SEED_WORDCOUNT_24
                    },
                )

                if (checksumError) {
                    CryptasaBanner(
                        title = stringResource(Res.string.onb_seedin_err_checksum),
                        tone = CryptasaBannerTone.Danger,
                        modifier = Modifier.testTag(OnboardingTestTags.SEED_ERROR_BANNER),
                    )
                }

                // Word grid, 3 columns.
                for (row in 0 until (wordCount + 2) / 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 3) {
                            val index = row * 3 + col
                            if (index < wordCount) {
                                val word = current[index]
                                SeedInputCell(
                                    number = index + 1,
                                    word = word,
                                    isError = word.isNotBlank() && !MnemonicSupport.isWord(word),
                                    focused = focusedIndex == index,
                                    onValueChange = { changeWord(index, it) },
                                    onFocusChanged = { focused -> if (focused) focusedIndex = index else if (focusedIndex == index) focusedIndex = null },
                                    modifier = Modifier.weight(1f).testTag(OnboardingTestTags.seedCell(index + 1)),
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (!allFilled) {
                    Text(
                        text = pluralStringResource(Res.plurals.onb_seedin_err_toofew, wordCount, wordCount),
                        style = CryptasaTheme.typography.helper,
                        color = colors.onSurfaceVariant,
                    )
                }

                pasteIssue?.let { issue ->
                    Text(
                        text = when (issue) {
                            is PasteIssue.Count -> stringResource(Res.string.onb_seedin_err_pastecount, issue.got, wordCount)
                            PasteIssue.Chars -> stringResource(Res.string.onb_seedin_err_chars)
                        },
                        style = CryptasaTheme.typography.helper,
                        color = colors.danger,
                    )
                }

                // Autocomplete suggestions for the focused cell.
                val focusWord = focusedIndex?.let { current.getOrElse(it) { "" } }.orEmpty()
                if (focusWord.isNotEmpty() && !MnemonicSupport.isWord(focusWord)) {
                    val suggestions = MnemonicSupport.suggestions(focusWord, limit = 5)
                    if (suggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            suggestions.forEach { suggestion ->
                                Text(
                                    text = suggestion,
                                    style = CryptasaTheme.typography.labelSmall,
                                    color = colors.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(CryptasaTheme.radius.full))
                                        .background(colors.primarySurface)
                                        .clickable { focusedIndex?.let { onWordChange(it, suggestion) } }
                                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                                )
                            }
                        }
                    }
                }

                CryptasaTextLink(
                    text = stringResource(Res.string.onb_seedin_paste),
                    onClick = { handlePaste() },
                    modifier = Modifier.testTag(OnboardingTestTags.SEED_PASTE),
                )
            }

            CryptasaButton(
                text = stringResource(Res.string.onb_seedin_cta),
                onClick = { attemptImport() },
                enabled = canImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.md)
                    .testTag(OnboardingTestTags.SEED_IMPORT),
            )
        }
    }
}

/** A single editable seed-word cell: index number + letters-only input; danger outline on error. */
@Composable
private fun SeedInputCell(
    number: Int,
    word: String,
    isError: Boolean,
    focused: Boolean,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val shape = RoundedCornerShape(CryptasaTheme.radius.md)
    val outline = when {
        isError -> colors.danger
        focused -> colors.primary
        else -> colors.outline
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(if (focused && !isError) 2.dp else 1.dp, outline, shape)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$number",
                style = CryptasaTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(end = spacing.xs),
            )
            BasicTextField(
                value = word,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).onFocusChanged { onFocusChanged(it.isFocused) },
                singleLine = true,
                textStyle = CryptasaTheme.typography.body.copy(color = if (isError) colors.danger else colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
        }
    }
}

/** Inline text link (primary) — used for the read-only "paste from clipboard" affordance. */
@Composable
private fun CryptasaTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = CryptasaTheme.typography.labelSmall,
        color = CryptasaTheme.colors.primary,
        modifier = modifier
            .clip(RoundedCornerShape(CryptasaTheme.radius.sm))
            .clickable(onClick = onClick)
            .padding(vertical = CryptasaTheme.spacing.xs),
    )
}
