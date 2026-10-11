// Helper init script for scripts/refresh-locks.sh and
// scripts/update-verification-metadata.sh.
//
// Registers a `resolveAllForVerification` task in every project that resolves
// (and downloads) the artifacts of every resolvable configuration. Running the
// build with `--write-locks` or `--write-verification-metadata` plus this task
// covers the whole graph in one invocation, without compiling code or running tests.
//
// Usage:
//   ./gradlew --write-locks -I scripts/resolve-all.init.gradle.kts resolveAllForVerification
gradle.beforeProject {
    tasks.register("resolveAllForVerification") {
        group = "verification"
        description = "Resolves all resolvable configurations (lock/verification refresh support)."
        doLast {
            val failed = mutableListOf<String>()
            project.configurations.filter { it.isCanBeResolved }.forEach { cfg ->
                try {
                    cfg.incoming.artifactView { lenient(true) }.files.forEach { }
                } catch (e: Exception) {
                    failed.add(cfg.name)
                }
            }
            if (failed.isNotEmpty()) {
                logger.warn("[resolveAllForVerification] ${project.path}: skipped ${failed.size} configuration(s): ${failed.joinToString(", ")}")
            }
        }
    }
}
