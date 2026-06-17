package com.tneff.cyppie.feature.onboarding

import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * KAN-10 — verifies the testTag bootstrap on the **Android** renderer via Robolectric (JVM, no
 * emulator; ADR-0013):
 * - AK1: the onboarding root enables `testTagsAsResourceId` (the property that maps every `testTag`
 *   to an Android **resource-id** for Maestro). Asserted directly on the semantics tree.
 * - AK2/AK3: the welcome screen's contract IDs ([OnboardingTestTags]) are exposed and selectable.
 *
 * Device-level resource-id matching (UIAutomator `By.res`) is validated by the test agent via
 * Maestro against the installed app — out of Dev scope here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class OnboardingTestTagsAndroidTest {

    @Test
    fun rootEnablesTestTagsAsResourceId() = runComposeUiTest {
        setContent { OnboardingRoot() }
        val nodes = onAllNodes(
            SemanticsMatcher.expectValue(SemanticsPropertiesAndroid.TestTagsAsResourceId, true),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(
            nodes.isNotEmpty(),
            "Onboarding root must set testTagsAsResourceId=true so testTags map to Android resource-ids",
        )
    }

    @Test
    fun welcomeContractTagsAreExposedAndClickable() = runComposeUiTest {
        setContent { OnboardingRoot() }
        onNodeWithTag(OnboardingTestTags.WELCOME_START).assertExists().assertIsEnabled().assertHasClickAction()
        onNodeWithTag(OnboardingTestTags.WELCOME_IMPORT).assertExists().assertIsEnabled().assertHasClickAction()
    }
}
