plugins {
    alias(libs.plugins.freefair.lombok)
    eclipse
    application
    id("jrm.delombok-javadoc")
}

dependencies {
    "compileOnly"(libs.jsr305)
    "implementation"(project(":jrmcore"))
    "implementation"(project(":res-icons"))
    "implementation"(libs.commons.codec)
    "implementation"(libs.jcommander)
    "implementation"(libs.commons.io)
    "implementation"(libs.commons.lang3)
    "implementation"(libs.streamex)
    "implementation"(libs.minimal.json)
    "implementation"(libs.javax.activation)
    "runtimeOnly"(libs.jspecify)

    "testImplementation"(libs.junit.jupiter.api)
    "testImplementation"(libs.junit.jupiter.engine)
    "testRuntimeOnly"(libs.junit.platform.launcher)
}

application {
    mainClass.set("jrm.JRomManagerMulti")
}

tasks.named<JavaExec>("run") {
    jvmArgs()
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Manifest-Version" to "1.0",
                "Specification-Title" to "JRomManager Standalone"
            )
        )
    }
}

tasks.named<Javadoc>("javadoc") {
    exclude("**/LICENSE")
}

tasks.named<Test>("test") {
    val tmpDir = temporaryDir
    systemProperty("file.encoding", "utf-8")
    systemProperty("java.io.tmpdir", "$tmpDir")
    systemProperty("jrommanager.dir", "$tmpDir")
}

// The sonar plugin classes are not on this script's compile classpath;
// set skipProject without a typed reference.
extensions.findByName("sonar")?.let { sonarExt ->
    sonarExt.javaClass.getMethod("setSkipProject", Boolean::class.javaPrimitiveType).invoke(sonarExt, true)
}
