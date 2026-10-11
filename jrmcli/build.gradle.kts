import java.util.function.Predicate

plugins {
    alias(libs.plugins.freefair.lombok)
    eclipse
    application
    alias(libs.plugins.graalvm.native)
    id("jrm.delombok-javadoc")
}

// Shared native-image defaults (charsets, Fory build-time init) from buildSrc
// JrmNative.commonBuildArgs.
graalvmNative {
    toolchainDetection.set(true)
    testSupport.set(false)
    binaries.all {
        // Required charsets: UTF-8, US-ASCII, CP437, CP1252.
        // -H:+AddAllCharsets is the only supported way to include ext charsets.
        // SevenZipJBinding loads its platform DLL from the classpath and calls back through JNI.
        buildArgs.addAll(JrmNative.commonBuildArgs)
    }
    agent {
        enabled.set(true)
        defaultMode.set("standard")
        // Restrict agent instrumentation to Test tasks (default matches every
        // JavaExec/Test task and slows interactive runs by ~30-60s).
        tasksToInstrumentPredicate.set(Predicate { task -> task is Test })
        metadataCopy {
            inputTaskNames.addAll("test")
            outputDirectories.add("src/main/resources/META-INF/native-image/${project.group}/${project.name}")
            mergeWithExisting.set(true)
        }
    }
    binaries {
        named("main") {
            imageName.set("jrmcli")
            mainClass.set("jrm.cli.JRomManagerCLI")
            buildArgs.addAll(
                "-J--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-J--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                "-J--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "-J--add-opens=java.base/java.util=ALL-UNNAMED",
                // Builder JVM only; not a runtime image argument.
                "-J--enable-native-access=ALL-UNNAMED"
            )
        }
    }
}

dependencies {
    "compileOnly"(libs.jsr305)
    "implementation"(project(":jrmcore"))
    "implementation"(libs.jcommander)
    "implementation"(libs.commons.io)
    "implementation"(libs.commons.lang3)
    "implementation"(libs.minimal.json)
    "implementation"(libs.jline)

    "testImplementation"(libs.bundles.junit.jupiter.stack)
    "testImplementation"(libs.assertj.core)
    "testImplementation"(libs.mockito.core)
    "testRuntimeOnly"(libs.junit.platform.launcher)
}

application {
    mainClass.set("jrm.cli.JRomManagerCLI")
}

tasks.named<JavaExec>("run") {
    args("-i")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to "JRomManager CLI",
                "Implementation-Title" to "jrmcli"
            )
        )
    }
}

tasks.named<Javadoc>("javadoc") {
    exclude("**/*.properties")
}

tasks.named<Test>("test") {
    jvmArgs("--add-opens", "jdk.unsupported/sun.misc=ALL-UNNAMED")
    val tmpDir = temporaryDir
    systemProperty("file.encoding", "utf-8")
    systemProperty("java.io.tmpdir", "$tmpDir")
    systemProperty("jrommanager.dir", "$tmpDir")
}
