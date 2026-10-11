plugins {
    alias(libs.plugins.freefair.lombok)
    eclipse
    id("jrm.delombok-javadoc")
}

dependencies {
    "compileOnly"(libs.jsr305)
    "implementation"(libs.sevenzipjbinding)
    "implementation"(libs.jtrrntzip)
    "implementation"(libs.commons.codec)
    "implementation"(libs.commons.io)
    "implementation"(libs.commons.text)
    "implementation"(libs.commons.compress)
    "implementation"(libs.streamex)
    "runtimeOnly"(libs.jspecify)
    "implementation"(libs.javax.activation)
    "implementation"(libs.minimal.json)
    "implementation"(libs.zip4j)
    "implementation"(libs.fory.core)

    "testImplementation"(libs.bundles.junit.jupiter.stack)
    "testImplementation"(libs.assertj.core)
    "testImplementation"(libs.mockito.core)
    "testRuntimeOnly"(libs.junit.platform.launcher)
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to "JRomManager Core",
                "Implementation-Title" to "jrmcore"
            )
        )
    }
}

tasks.named<Javadoc>("javadoc") {
    exclude("**/LICENSE")
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("html5", true)
    }
}

tasks.named<Test>("test") {
    val tmpDir = temporaryDir
    systemProperty("file.encoding", "utf-8")
    systemProperty("java.io.tmpdir", "$tmpDir")
    systemProperty("jrommanager.dir", "$tmpDir")
}
