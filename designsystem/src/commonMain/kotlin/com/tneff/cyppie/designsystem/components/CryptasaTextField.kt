package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.foundation.clickableIcon
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * Text input with the inline-error pattern (HANDOFF §3 "TextField" + §5.2.1).
 *
 * Anatomy: label (`labelSmall`) · box height 56, radius `md`, value (`body`) + optional trailing
 * icon · helper/error line (`helper`) with leading icon. States derive from inputs:
 * resting/filled/focus/error/disabled. Error binds outline + helper + icon to `danger`; the field
 * is annotated `error` for screen readers and the message is a live region (§5.4). Token-bound;
 * direction-agnostic (uses `start`/`end` via default Row/Arrangement).
 *
 * For secret entry (BIP-39 seed import ONB-5, password screens ONB-3/4) the call site must pass
 * [autoCorrect] = false and [capitalization] = KeyboardCapitalization.None so the keyboard does not
 * suggest/auto-correct/auto-capitalise secrets (§5.3 / NFR-1).
 */
@Composable
fun CryptasaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    errorText: String? = null,
    helperText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    autoCorrect: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: ImageVector? = null,
    trailingIconContentDescription: String? = null,
    onTrailingIconClick: (() -> Unit)? = null,
) {
    val colors = CryptasaTheme.colors
    val radius = CryptasaTheme.radius
    val spacing = CryptasaTheme.spacing
    val errorMessage = errorText
    val isError = errorMessage != null
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val outlineColor = when {
        !enabled -> colors.outline
        isError -> colors.danger
        focused -> colors.primary
        else -> colors.outline
    }
    val borderWidth = if (focused && !isError) 2.dp else 1.dp
    val boxBackground = if (enabled) colors.surface else colors.surfaceVariant
    val valueColor = if (enabled) colors.onSurface else colors.onSurfaceVariant
    val shape = RoundedCornerShape(radius.md)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = CryptasaTheme.typography.labelSmall, color = colors.onSurface)

        Box(
            modifier = Modifier
                .padding(top = spacing.xs)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(boxBackground)
                .border(borderWidth, outlineColor, shape)
                .padding(horizontal = spacing.md)
                .then(if (errorMessage != null) Modifier.semantics { error(errorMessage) } else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = singleLine,
                        textStyle = CryptasaTheme.typography.body.copy(color = valueColor),
                        cursorBrush = SolidColor(colors.primary),
                        visualTransformation = visualTransformation,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType,
                            imeAction = imeAction,
                            autoCorrectEnabled = autoCorrect,
                            capitalization = capitalization,
                        ),
                        interactionSource = interactionSource,
                        decorationBox = { inner ->
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = CryptasaTheme.typography.body,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }

                val effectiveTrailing = trailingIcon ?: if (isError) CryptasaIcons.Error else null
                if (effectiveTrailing != null) {
                    val tint = if (isError && trailingIcon == null) colors.danger else colors.onSurfaceVariant
                    Icon(
                        imageVector = effectiveTrailing,
                        contentDescription = trailingIconContentDescription,
                        tint = tint,
                        modifier = Modifier
                            .padding(start = spacing.xs)
                            .size(24.dp)
                            .then(
                                if (onTrailingIconClick != null && enabled) {
                                    Modifier.clickableIcon(onClick = onTrailingIconClick)
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }
            }
        }

        val supporting = errorMessage ?: helperText
        if (supporting != null) {
            Row(
                modifier = Modifier
                    .padding(top = spacing.xxs)
                    .then(if (isError) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                if (isError) {
                    Icon(
                        imageVector = CryptasaIcons.Error,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = supporting,
                    style = CryptasaTheme.typography.helper,
                    color = if (isError) colors.danger else colors.onSurfaceVariant,
                )
            }
        }
    }
}
