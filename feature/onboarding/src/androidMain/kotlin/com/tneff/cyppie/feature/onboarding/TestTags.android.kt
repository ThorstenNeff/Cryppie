package com.tneff.cyppie.feature.onboarding

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

actual fun Modifier.enableTestTagsAsResourceId(): Modifier =
    this.semantics { testTagsAsResourceId = true }
