package dev.bee.beecode.app

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * The declared app version, checked against the release tags that shipped.
 *
 * ## Why this exists
 *
 * Because two releases shipped the same version and nothing noticed.
 *
 * `release.yml` names its assets from the git tag — `BeeCode-v0.2.0-android.apk` — while
 * the Android manifest reads `appVersionName`/`appVersionCode` from the version
 * catalogue. Nothing tied the two together, so the catalogue sat at `0.1.0`/`1` while
 * two tags came and went. `aapt dump badging` on the *published* v0.2.0 APK reports:
 *
 * ```
 * package: name='dev.bee.beecode' versionCode='1' versionName='0.1.0'
 * ```
 *
 * The filename said one thing and the artifact inside said another.
 *
 * ## Why versionCode is the serious half
 *
 * `versionName` is a human-facing string; a wrong one is embarrassing. `versionCode` is
 * what Android compares, and it upgrades only on a *strictly greater* value. Two
 * releases sharing code `1` are indistinguishable to the platform, so installing v0.2.0
 * over v0.1.0 was not an upgrade — it was a reinstall of a build Android already
 * believed it had. Every fix in that release was invisible to anyone who had the first
 * one, which is a shipped-and-silent failure rather than a build error.
 *
 * ## What this asserts, and what it deliberately does not
 *
 * That the catalogue's version is *at least* the newest release tag, and that a tag
 * matching it has a distinct version code. It does not require a tag to exist for the
 * current version: development happens ahead of the last release, which is the normal
 * state, and a test that forbade it would fail on every commit. It only fails when the
 * declared version has fallen *behind* what was already published — the actual defect.
 *
 * Tags come from `git`, not from a checked-in list, so this cannot drift from reality.
 * If git is unavailable the test skips rather than failing: a source tarball with no
 * `.git` is a legitimate way to build, and a version check is not worth breaking it.
 */
class ReleaseVersionTest {

    @Test
    fun theDeclaredVersionIsNotBehindTheNewestReleaseTag() {
        val declared = versionCatalogue("appVersionName") ?: fail(
            "appVersionName is missing from gradle/libs.versions.toml",
        )
        val newestTag = newestReleaseTag() ?: return

        val declaredParts = semanticParts(declared) ?: fail(
            "appVersionName \"$declared\" is not a three-part semantic version, so it " +
                "cannot be compared with the release tag \"v$newestTag\".",
        )
        val tagParts = semanticParts(newestTag) ?: return

        if (compareVersions(declaredParts, tagParts) < 0) {
            fail(
                "appVersionName is \"$declared\" but \"v$newestTag\" has already been " +
                    "released. The release workflow names its assets from the git tag " +
                    "and the Android manifest reads this value, so a release built now " +
                    "would ship a file called v$newestTag containing version $declared — " +
                    "which is exactly how v0.1.0 and v0.2.0 came to ship the same APK " +
                    "version. Bump appVersionName and appVersionCode in " +
                    "gradle/libs.versions.toml.",
            )
        }
    }

    /**
     * The version code must have moved at least as often as the released versions.
     *
     * A monotonic counter cannot be checked against its own value alone, so this uses
     * the release count as the floor: n published releases means at least n distinct
     * codes were needed, one per release. It catches the case that actually happened —
     * the counter left at 1 across two releases — without pinning a specific number.
     */
    @Test
    fun theVersionCodeHasBeenBumpedForEveryRelease() {
        val declaredCode = versionCatalogue("appVersionCode")?.toIntOrNull() ?: fail(
            "appVersionCode is missing from gradle/libs.versions.toml, or is not an integer",
        )
        val releaseCount = releaseTags().size
        if (releaseCount == 0) return

        if (declaredCode < releaseCount) {
            fail(
                "appVersionCode is $declaredCode but $releaseCount release tags exist " +
                    "(${releaseTags().joinToString(", ") { "v$it" }}). Android upgrades " +
                    "only on a strictly greater versionCode, so releases sharing one are " +
                    "the same build as far as the platform is concerned — installing the " +
                    "newer over the older is a reinstall, not an upgrade, and every fix " +
                    "in it stays invisible. Bump appVersionCode once per release.",
            )
        }
    }

    private companion object {
        /**
         * Read a `[versions]` entry from the catalogue.
         *
         * Parsed with a regex rather than a TOML library: `:shared` is deliberately
         * dependency-free, and this needs one flat key from one known section.
         */
        fun versionCatalogue(key: String): String? {
            val file = File(repoRoot(), "gradle/libs.versions.toml")
            if (!file.isFile) return null
            val match = Regex("""^\s*$key\s*=\s*"([^"]+)"\s*$""", RegexOption.MULTILINE)
                .find(file.readText())
            return match?.groupValues?.get(1)
        }

        /**
         * Release tags, newest last, with the leading `v` stripped.
         *
         * Only `v`-prefixed three-part tags count as releases: the repository also
         * carries `plan-v1`-style markers, which are not versions of the app.
         */
        fun releaseTags(): List<String> {
            val output = git("tag", "--list", "v*") ?: return emptyList()
            return output.lineSequence()
                .map { it.trim().removePrefix("v") }
                .filter { semanticParts(it) != null }
                .sortedWith { a, b -> compareVersions(semanticParts(a)!!, semanticParts(b)!!) }
                .toList()
        }

        fun newestReleaseTag(): String? = releaseTags().lastOrNull()

        /** Null rather than an exception when git is absent — see this class's KDoc. */
        fun git(vararg args: String): String? = try {
            val process = ProcessBuilder(listOf("git", *args))
                .directory(repoRoot())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() == 0) output else null
        } catch (_: java.io.IOException) {
            null
        }

        fun semanticParts(version: String): List<Int>? {
            val parts = version.split(".")
            if (parts.size != 3) return null
            return parts.map { it.toIntOrNull() ?: return null }
        }

        fun compareVersions(a: List<Int>, b: List<Int>): Int {
            a.indices.forEach { index ->
                val comparison = a[index].compareTo(b[index])
                if (comparison != 0) return comparison
            }
            return 0
        }

        /**
         * The repository root, passed by the build so this does not depend on the
         * working directory Gradle chooses. Same helper the pack tests use.
         */
        fun repoRoot(): File {
            System.getProperty("beecode.repoRoot")?.let { return File(it) }
            var candidate = File(".").absoluteFile
            repeat(6) {
                if (File(candidate, "gradle/libs.versions.toml").isFile) return candidate
                candidate = candidate.parentFile ?: return candidate
            }
            return File(".").absoluteFile
        }
    }
}
