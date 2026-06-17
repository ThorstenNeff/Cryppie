package com.tneff.cyppie.feature.onboarding

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.enableTestTagsAsResourceId(): Modifier =
    this.semantics { testTagsAsResourceId = true }
