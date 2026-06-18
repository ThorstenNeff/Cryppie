package com.tneff.cyppie.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.LayoutDirection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection as UiLayoutDirection
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.onboarding.generated.resources.Res
import com.tneff.cyppie.feature.onboarding.generated.resources.onb_welcome_cta
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * KAN-35 — i18n + RTL bootstrap verification on the Android renderer (Robolectric; ADR-0013).
 *
 * - Locale: with the system locale set to `ar`, the welcome CTA resolves to its **Arabic**
 *   `composeResources` string, proving the 14-locale catalog loads and the base falls through
 *   correctly (no raw text, §5.5). `getString` reads the system [Locale] deterministically;
 *   the welcome screen itself renders via `stringResource` (covered by the testTag suite).
 * - RTL: the adaptive `OnboardingScaffold` mirrors its brand column to the trailing side under
 *   `LayoutDirection.Rtl`, proving the layout uses `start`/`end` (not `left`/`right`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class OnboardingI18nRtlAndroidTest {

    @Test
    fun welcomeCtaResolvesToArabicUnderArLocale() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        try {
            val cta = runBlocking { getString(Res.string.onb_welcome_cta) }
            assertEquals("هيا نبدأ", cta, "onb_welcome_cta must resolve to its Arabic translation under locale ar")
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun expandedScaffoldMirrorsBrandColumnUnderRtl() = runComposeUiTest {
        val direction = mutableStateOf(UiLayoutDirection.Ltr)
        setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.LayoutDirection(direction.value)) {
                CryptasaTheme {
                    OnboardingScaffold(
                        title = "Cyppie",
                        windowClass = OnboardingWindowClass.Expanded,
                        brand = { Box(Modifier.testTag("brand").size(8.dp)) },
                    ) {
                        Text("content", modifier = Modifier.testTag("content"))
                    }
                }
            }
        }
        waitForIdle()
        val brandLtr = onNodeWithTag("brand").getUnclippedBoundsInRoot().left.value

        runOnIdle { direction.value = UiLayoutDirection.Rtl }
        waitForIdle()
        val brandRtl = onNodeWithTag("brand").getUnclippedBoundsInRoot().left.value

        assertTrue(
            brandRtl > brandLtr,
            "Brand column must move from the leading (left) to the trailing (right) side under RTL " +
                "(LTR left=$brandLtr, RTL left=$brandRtl)",
        )
    }
}
