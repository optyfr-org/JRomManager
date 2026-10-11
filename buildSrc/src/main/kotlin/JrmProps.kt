import java.io.File
import java.util.Properties

/**
 * Reads the shared dist/ver.properties build metadata.
 *
 * Replaces the old rootProject.extra["props"] mechanism, which forced root <->
 * subproject evaluation ordering. Callers pass their [rootDir] (identical for
 * root and subprojects) and read directly, so no ordering constraint remains.
 */
object JrmProps {
    fun load(rootDir: File): Properties =
        Properties().apply {
            rootDir.resolve("dist/ver.properties").inputStream().use { load(it) }
        }

    fun specVersion(rootDir: File): String =
        load(rootDir).getProperty("Specification-Version")

    fun implVersion(rootDir: File): String =
        load(rootDir).getProperty("Implementation-Version")

    fun projectVersion(rootDir: File): String =
        "${specVersion(rootDir)}.${implVersion(rootDir)}"

    fun daemonVersion(rootDir: File): String =
        load(rootDir).getProperty("Commons-Daemon-Version")
}
