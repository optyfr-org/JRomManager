plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Nebula ospackage plugin classes, needed on buildSrc's compile classpath
    // so the jrm.packaging-ospackage precompiled plugin can reference them
    // with static types. Version mirrors gradle/libs.versions.toml
    // (nebula-ospackage) — buildSrc cannot read the parent catalog, so keep
    // both in sync when bumping.
    implementation("com.netflix.nebula:gradle-ospackage-plugin:12.3.0")
}

configurations.all {
    resolutionStrategy {
        // aircompressor buffer-reuse info leak (< 2.0.3: crafted Snappy/LZ4
        // input can expose prior output-buffer contents). nebula-ospackage
        // 12.3.0 pulls 0.27; force the catalog's patched 2.0.3, mirroring the
        // root buildscript block. buildSrc cannot read the parent catalog, so
        // keep in sync with gradle/libs.versions.toml (aircompressor).
        force("io.airlift:aircompressor:2.0.3")
        eachDependency {
            // CVE-2025-48924 (commons-lang3 < 3.18.0: uncontrolled recursion in
            // ClassUtils.getClass on very long inputs). nebula-ospackage 12.3.0
            // still pulls 3.14.0 (latest as of 2026-03-03); force the catalog's
            // patched version (3.21.0). buildSrc cannot read the parent catalog,
            // so keep in sync with gradle/libs.versions.toml (commons-lang3).
            if (requested.group == "org.apache.commons" && requested.name == "commons-lang3") {
                useVersion("3.21.0")
                because("CVE-2025-48924 fixed in 3.18.0; buildSrc pins patched 3.21.0")
            }
        }
        // CVE-2026-3505 (BC-JAVA bcpg < 1.84: unbounded PGP AEAD chunk size
        // -> pre-auth resource exhaustion). nebula-ospackage 12.3.0 still
        // pulls bcpg/bcprov-jdk15on 1.69 via redline/jdeb. Substitute the
        // catalog's patched jdk18on 1.86 pair, mirroring the root buildscript
        // block. buildSrc cannot read the parent catalog, so keep in sync
        // with gradle/libs.versions.toml (bouncycastle).
        dependencySubstitution {
            substitute(module("org.bouncycastle:bcpg-jdk15on")).using(module("org.bouncycastle:bcpg-jdk18on:1.86")).because("CVE-2026-3505 fixed in 1.84; buildSrc pins patched 1.86")
            substitute(module("org.bouncycastle:bcprov-jdk15on")).using(module("org.bouncycastle:bcprov-jdk18on:1.86")).because("CVE-2026-3505 fixed in 1.84; buildSrc pins patched 1.86")
        }
    }
}
