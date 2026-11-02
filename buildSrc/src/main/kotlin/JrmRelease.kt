/**
 * Installer-safe application versions derived from the project version.
 *
 * jpackage --app-version ends up verbatim in native package metadata whose
 * grammar is stricter than a SemVer pre-release (e.g. 4.0.0-beta.1):
 * - RPM Version fields forbid '-' (it separates version from release), so
 *   pre-release dashes become '~', which also sorts before the final release
 *   (4.0.0~beta.1 < 4.0.0); any other illegal char becomes '.'.
 * - MSI ProductVersion must be numeric dotted (up to 4 parts), so only the
 *   leading numeric core is kept ("4.0.0-beta.1" -> "4.0.0").
 * - macOS (dmg/pkg) CFBundleVersion must be period-separated non-negative
 *   integers ("4.0.0-beta.1" -> "4.0.0"): jpackage rejects anything else
 *   (e.g. "1.b.3") with error.version-string-invalid-component and exits 1.
 * - Anything else (deb, ...) uses the project version as-is.
 */
object JrmRelease {
    fun appVersionFor(projectVersion: String, installerType: String?): String = when (installerType) {
        "rpm" -> projectVersion.replace('-', '~').replace(Regex("[^A-Za-z0-9.+~^]"), ".")
        "msi", "dmg", "pkg" -> projectVersion.substringBefore('-').split('.').map { part -> part.filter(Char::isDigit) }.filter { it.isNotEmpty() }.joinToString(".")
        else -> projectVersion
    }
}
