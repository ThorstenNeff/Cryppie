package com.tneff.cyppie.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The handful of Material Symbols the design system actually uses, inlined as [ImageVector]s so we
 * do **not** ship the full `material-icons-extended` set (~2000 icons) into the JS/Wasm bundles
 * (and avoid that artifact's deprecation). Path data is copied verbatim from the official Material
 * icons (Apache-2.0). All are 24×24, black fill — `Icon(...)` applies the actual tint.
 */
object CryptasaIcons {
    val Error: ImageVector by lazy {
        materialVector("Cryptasa.Error") {
            moveTo(12.0f, 2.0f)
            curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
            reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
            reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
            reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
            close()
            moveTo(13.0f, 17.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(2.0f)
            close()
            moveTo(13.0f, 13.0f)
            horizontalLineToRelative(-2.0f)
            lineTo(11.0f, 7.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(6.0f)
            close()
        }
    }

    val Info: ImageVector by lazy {
        materialVector("Cryptasa.Info") {
            // Material "info" (filled): solid disc with the "i" (dot over bar) cut out (same winding as Error).
            moveTo(12.0f, 2.0f)
            curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
            reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
            reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
            reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
            close()
            moveTo(13.0f, 9.0f) // dot (upper)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(2.0f)
            close()
            moveTo(13.0f, 17.0f) // bar (lower)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(-6.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(6.0f)
            close()
        }
    }

    val Warning: ImageVector by lazy {
        materialVector("Cryptasa.Warning") {
            moveTo(1.0f, 21.0f)
            horizontalLineToRelative(22.0f)
            lineTo(12.0f, 2.0f)
            lineTo(1.0f, 21.0f)
            close()
            moveTo(13.0f, 18.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(2.0f)
            close()
            moveTo(13.0f, 14.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(-4.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(4.0f)
            close()
        }
    }

    val Check: ImageVector by lazy {
        materialVector("Cryptasa.Check") {
            moveTo(9.0f, 16.17f)
            lineTo(4.83f, 12.0f)
            lineToRelative(-1.42f, 1.41f)
            lineTo(9.0f, 19.0f)
            lineTo(21.0f, 7.0f)
            lineToRelative(-1.41f, -1.41f)
            close()
        }
    }

    /** Refresh / reload affordance (Wallet-Home, KAN-81). */
    val Refresh: ImageVector by lazy {
        materialVector("Cryptasa.Refresh") {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4.0f, 12.0f, 4.0f)
            curveToRelative(-4.42f, 0.0f, -7.99f, 3.58f, -7.99f, 8.0f)
            reflectiveCurveToRelative(3.57f, 8.0f, 7.99f, 8.0f)
            curveToRelative(3.73f, 0.0f, 6.84f, -2.55f, 7.73f, -6.0f)
            horizontalLineToRelative(-2.08f)
            curveToRelative(-0.82f, 2.33f, -3.04f, 4.0f, -5.65f, 4.0f)
            curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
            reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
            curveToRelative(1.66f, 0.0f, 3.14f, 0.69f, 4.22f, 1.78f)
            lineTo(13.0f, 11.0f)
            horizontalLineToRelative(7.0f)
            verticalLineTo(4.0f)
            lineToRelative(-2.35f, 2.35f)
            close()
        }
    }

    val CloudOff: ImageVector by lazy {
        materialVector("Cryptasa.CloudOff") {
            moveTo(19.35f, 10.04f)
            curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
            curveToRelative(-1.48f, 0.0f, -2.85f, 0.43f, -4.01f, 1.17f)
            lineToRelative(1.46f, 1.46f)
            curveTo(10.21f, 6.23f, 11.08f, 6.0f, 12.0f, 6.0f)
            curveToRelative(3.04f, 0.0f, 5.5f, 2.46f, 5.5f, 5.5f)
            verticalLineToRelative(0.5f)
            horizontalLineTo(19.0f)
            curveToRelative(1.66f, 0.0f, 3.0f, 1.34f, 3.0f, 3.0f)
            curveToRelative(0.0f, 1.13f, -0.64f, 2.11f, -1.56f, 2.62f)
            lineToRelative(1.45f, 1.45f)
            curveTo(23.16f, 18.16f, 24.0f, 16.68f, 24.0f, 15.0f)
            curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
            close()
            moveTo(3.0f, 5.27f)
            lineToRelative(2.75f, 2.74f)
            curveTo(2.56f, 8.15f, 0.0f, 10.77f, 0.0f, 14.0f)
            curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
            horizontalLineToRelative(11.73f)
            lineToRelative(2.0f, 2.0f)
            lineTo(21.0f, 20.73f)
            lineTo(4.27f, 4.0f)
            lineTo(3.0f, 5.27f)
            close()
            moveTo(7.73f, 10.0f)
            lineToRelative(8.0f, 8.0f)
            horizontalLineTo(6.0f)
            curveToRelative(-2.21f, 0.0f, -4.0f, -1.79f, -4.0f, -4.0f)
            reflectiveCurveToRelative(1.79f, -4.0f, 4.0f, -4.0f)
            horizontalLineToRelative(1.73f)
            close()
        }
    }

    /** Auto-mirrored for RTL (SPEC §5.5). */
    val ArrowBack: ImageVector by lazy {
        materialVector("Cryptasa.ArrowBack", autoMirror = true) {
            moveTo(20.0f, 11.0f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12.0f, 4.0f)
            lineToRelative(-8.0f, 8.0f)
            lineToRelative(8.0f, 8.0f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13.0f)
            horizontalLineTo(20.0f)
            verticalLineToRelative(-2.0f)
            close()
        }
    }

    /** Reveal-password affordance (ONB-3 eye toggle); reveal state is conveyed via contentDescription. */
    val Visibility: ImageVector by lazy {
        materialVector("Cryptasa.Visibility") {
            moveTo(12.0f, 4.5f)
            curveTo(7.0f, 4.5f, 2.73f, 7.61f, 1.0f, 12.0f)
            curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
            reflectiveCurveToRelative(9.27f, -3.11f, 11.0f, -7.5f)
            curveTo(21.27f, 7.61f, 17.0f, 4.5f, 12.0f, 4.5f)
            close()
            moveTo(12.0f, 17.0f)
            curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
            reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
            reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
            reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
            close()
            moveTo(12.0f, 9.0f)
            curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
            reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
            reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
            reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
            close()
        }
    }

    /** Create / "new" affordance (ONB-2 create-wallet card). */
    val AddCircle: ImageVector by lazy {
        materialVector("Cryptasa.AddCircle") {
            moveTo(12.0f, 2.0f)
            curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
            reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
            reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
            reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
            close()
            moveToRelative(5.0f, 11.0f)
            horizontalLineToRelative(-4.0f)
            verticalLineToRelative(4.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(-4.0f)
            horizontalLineTo(7.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(4.0f)
            verticalLineTo(7.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(4.0f)
            horizontalLineToRelative(4.0f)
            verticalLineToRelative(2.0f)
            close()
        }
    }

    /** Selected-state affordance (Wallet-Home account switcher, KAN-113 H3). */
    val CheckCircle: ImageVector by lazy {
        materialVector("Cryptasa.CheckCircle") {
            moveTo(12.0f, 2.0f)
            curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
            reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
            reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
            reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
            close()
            moveToRelative(-2.0f, 15.0f)
            lineToRelative(-5.0f, -5.0f)
            lineToRelative(1.41f, -1.41f)
            lineTo(10.0f, 14.17f)
            lineToRelative(7.59f, -7.59f)
            lineTo(19.0f, 8.0f)
            lineToRelative(-9.0f, 9.0f)
            close()
        }
    }

    /** Import / download affordance (ONB-2 import-wallet card). */
    val Download: ImageVector by lazy {
        materialVector("Cryptasa.Download") {
            moveTo(19.0f, 9.0f)
            horizontalLineToRelative(-4.0f)
            verticalLineTo(3.0f)
            horizontalLineTo(9.0f)
            verticalLineToRelative(6.0f)
            horizontalLineTo(5.0f)
            lineToRelative(7.0f, 7.0f)
            lineToRelative(7.0f, -7.0f)
            close()
            moveTo(5.0f, 18.0f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(14.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineTo(5.0f)
            close()
        }
    }

    /** Copy-to-clipboard affordance (KAN-78 Receive — copy address). */
    val ContentCopy: ImageVector by lazy {
        materialVector("Cryptasa.ContentCopy") {
            moveTo(16.0f, 1.0f)
            horizontalLineTo(4.0f)
            curveTo(2.9f, 1.0f, 2.0f, 1.9f, 2.0f, 3.0f)
            verticalLineToRelative(14.0f)
            horizontalLineToRelative(2.0f)
            verticalLineTo(3.0f)
            horizontalLineToRelative(12.0f)
            verticalLineTo(1.0f)
            close()
            moveTo(19.0f, 5.0f)
            horizontalLineTo(8.0f)
            curveTo(6.9f, 5.0f, 6.0f, 5.9f, 6.0f, 7.0f)
            verticalLineToRelative(14.0f)
            curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
            horizontalLineToRelative(11.0f)
            curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
            verticalLineTo(7.0f)
            curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
            close()
            moveToRelative(0.0f, 16.0f)
            horizontalLineTo(8.0f)
            verticalLineTo(7.0f)
            horizontalLineToRelative(11.0f)
            verticalLineToRelative(14.0f)
            close()
        }
    }

    /** Trailing affordance on navigation cards; auto-mirrored for RTL (points left in `ar`). */
    val ChevronRight: ImageVector by lazy {
        materialVector("Cryptasa.ChevronRight", autoMirror = true) {
            moveTo(10.0f, 6.0f)
            lineTo(8.59f, 7.41f)
            lineTo(13.17f, 12.0f)
            lineToRelative(-4.58f, 4.59f)
            lineTo(10.0f, 18.0f)
            lineToRelative(6.0f, -6.0f)
            close()
        }
    }

    /** Settings gear (KAN-173 — the Home-header entry to the network Settings screen). Material "settings". */
    val Settings: ImageVector by lazy {
        materialVector("Cryptasa.Settings") {
            moveTo(19.14f, 12.94f)
            curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
            curveToRelative(0.0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
            lineToRelative(2.03f, -1.58f)
            curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
            lineToRelative(-1.92f, -3.32f)
            curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
            lineToRelative(-2.39f, 0.96f)
            curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
            lineTo(14.4f, 2.81f)
            curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
            horizontalLineToRelative(-3.84f)
            curveToRelative(-0.24f, 0.0f, -0.43f, 0.17f, -0.47f, 0.41f)
            lineToRelative(-0.36f, 2.54f)
            curveToRelative(-0.59f, 0.24f, -1.13f, 0.57f, -1.62f, 0.94f)
            lineToRelative(-2.39f, -0.96f)
            curveToRelative(-0.22f, -0.08f, -0.47f, 0.0f, -0.59f, 0.22f)
            lineTo(2.74f, 8.87f)
            curveToRelative(-0.12f, 0.21f, -0.08f, 0.47f, 0.12f, 0.61f)
            lineToRelative(2.03f, 1.58f)
            curveToRelative(-0.05f, 0.3f, -0.09f, 0.63f, -0.09f, 0.94f)
            reflectiveCurveToRelative(0.02f, 0.64f, 0.07f, 0.94f)
            lineToRelative(-2.03f, 1.58f)
            curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
            lineToRelative(1.92f, 3.32f)
            curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
            lineToRelative(2.39f, -0.96f)
            curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
            lineToRelative(0.36f, 2.54f)
            curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
            horizontalLineToRelative(3.84f)
            curveToRelative(0.24f, 0.0f, 0.44f, -0.17f, 0.47f, -0.41f)
            lineToRelative(0.36f, -2.54f)
            curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
            lineToRelative(2.39f, 0.96f)
            curveToRelative(0.22f, 0.08f, 0.47f, 0.0f, 0.59f, -0.22f)
            lineToRelative(1.92f, -3.32f)
            curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
            lineToRelative(-2.01f, -1.58f)
            close()
            moveTo(12.0f, 15.6f)
            curveToRelative(-1.98f, 0.0f, -3.6f, -1.62f, -3.6f, -3.6f)
            reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
            reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
            reflectiveCurveToRelative(-1.62f, 3.6f, -3.6f, 3.6f)
            close()
        }
    }
}

private inline fun materialVector(
    name: String,
    autoMirror: Boolean = false,
    crossinline pathBuilder: PathBuilder.() -> Unit,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply {
        path(fill = SolidColor(Color.Black)) { pathBuilder() }
    }.build()
