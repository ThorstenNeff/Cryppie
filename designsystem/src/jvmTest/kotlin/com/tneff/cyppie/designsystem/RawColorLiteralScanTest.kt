package com.tneff.cyppie.designsystem

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * AK3 (KAN-7) — token coverage: no raw colour values in component/screen sources.
 *
 * Enforces PRD-00 FR-1 ("every colour value references a named token, never a raw value"):
 * scans the `commonMain` Kotlin sources of the design-system **components** and the feature
 * **screens** for raw `Color(0x…)` hex literals. Such literals are only allowed inside the
 * token definitions under `…/theme/`. `icons/CryptasaIcons.kt` is exempt (vector path fills use
 * `Color.Black` by convention — see ADR/HANDOFF).
 *
 * JVM-only (`jvmTest`) because it reads source files from disk; it asserts on source text, not
 * on runtime behaviour, so it is fully deterministic and needs no device.
 */
class RawColorLiteralScanTest {

    private val rawHexColor = Regex("""Color\(\s*0x[0-9A-Fa-f]{6,8}\s*\)""")

    /** Path segments whose sources legitimately define raw colour values. */
    private val excludedPathParts = listOf("${sep}theme${sep}")
    private val excludedFileNames = setOf("CryptasaIcons.kt")

    @Test
    fun componentsAndScreensUseNoRawColorLiterals() {
        val repoRoot = findRepoRoot()
        val scanRoots = buildList {
            add(File(repoRoot, "designsystem/src/commonMain"))
            File(repoRoot, "feature").listFiles()?.forEach { feature ->
                add(File(feature, "src/commonMain"))
            }
        }.filter { it.isDirectory }

        assertTrue(
            scanRoots.any { it.path.contains("designsystem") },
            "Could not locate designsystem/src/commonMain under $repoRoot — scan root resolution broken",
        )

        val violations = mutableListOf<String>()
        for (root in scanRoots) {
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> excludedPathParts.none { file.path.contains(it) } }
                .filter { file -> file.name !in excludedFileNames }
                .forEach { file ->
                    file.readLines().forEachIndexed { idx, line ->
                        if (rawHexColor.containsMatchIn(line)) {
                            val rel = file.relativeTo(repoRoot).path
                            violations += "$rel:${idx + 1}: ${line.trim()}"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail(
                "Raw Color(0x…) literals must live in …/theme/ only (PRD-00 FR-1). " +
                    "Found ${violations.size}:\n" + violations.joinToString("\n"),
            )
        }
    }

    /** Walk up from the test working dir until the Gradle root (settings + designsystem) is found. */
    private fun findRepoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val hasSettings = File(dir, "settings.gradle.kts").isFile || File(dir, "settings.gradle").isFile
            val hasModule = File(dir, "designsystem").isDirectory
            if (hasSettings && hasModule) return dir
            dir = dir.parentFile
        }
        fail("Could not locate Cyppie repo root from ${System.getProperty("user.dir")}")
    }

    private companion object {
        val sep: String = File.separator
    }
}
