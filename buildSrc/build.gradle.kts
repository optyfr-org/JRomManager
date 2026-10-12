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
