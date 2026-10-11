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
        buildArgs.addAll(JrmNative.commonBuildArgs)
    }
    agent {
        enabled.set(true)
        defaultMode.set("standard")
        tasksToInstrumentPredicate.set(Predicate { task -> task is Test })
        metadataCopy {
            inputTaskNames.addAll("test", "runFullServer")
            outputDirectories.add("src/main/resources/META-INF/native-image/${project.group}/${project.name}")
            mergeWithExisting.set(true)
        }
    }
    binaries {
        named("main") {
            imageName.set("jrmserver")
            mainClass.set("jrm.server.Server")
            buildArgs.addAll(
                "-J--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-J--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                "-J--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "-J--add-opens=java.base/java.util=ALL-UNNAMED",
                // Builder JVM only; not a runtime image argument.
                "-J--enable-native-access=ALL-UNNAMED",
                "--enable-url-protocols=http,https"
            )
            runtimeArgs.addAll("--ws", "-d", "-c", "$rootDir/WebClient/war", "-w", "%HOMEPATH%/.jrommanager")
        }
        create("fullServer") {
            imageName.set("jrmfullserver")
            mainClass.set("jrm.fullserver.FullServer")
            // Start native jrmfullserver with -C/--cert. Webclient static serving from the
            // image heap needs a runtime smoke check; if resource: listing fails, pass --client <dir>.
            // listLibrariesMissingMetadata does not list Logback, so no extra converter entries.
            buildArgs.addAll(
                "-J--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-J--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                "-J--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "-J--add-opens=java.base/java.util=ALL-UNNAMED",
                // Builder JVM only; not a runtime image argument.
                "-J--enable-native-access=ALL-UNNAMED",
                "--enable-url-protocols=http,https"
            )
            runtimeArgs.addAll("--ws", "-c", "$rootDir/WebClient/war", "-d", "-w", "%HOMEPATH%/.jrommanager")
        }
    }
}

tasks.named("compileJava") {
    dependsOn(":WebClient:jar")
}

val wcjar = project(":WebClient").tasks.named<Jar>("jar")

dependencies {
    "compileOnly"(libs.jsr305)
    "implementation"(project(":jrmcore"))
    "implementation"(project(":res-icons"))
    "implementation"(libs.jcommander)
    "implementation"(libs.commons.io)
    "implementation"(libs.commons.daemon)
    "implementation"(libs.commons.lang3)
    "implementation"(libs.bundles.jetty.server.stack)
    "implementation"(libs.minimal.json)
    "implementation"(libs.gson)
    "implementation"(libs.streamex)
    "implementation"(libs.commons.dbutils)
    "implementation"(libs.h2)
    "implementation"(libs.jbcrypt)
    "implementation"(libs.jna)
    "implementation"(libs.argon2.jvm)
    "implementation"(libs.bundles.logging)
    "implementation"(files(wcjar))
    "runtimeOnly"(libs.jspecify)

    "testImplementation"(libs.bundles.jetty.client.stack)
    "testImplementation"(libs.awaitility)
    "testImplementation"(libs.junit.jupiter)
    "testRuntimeOnly"(libs.junit.platform.launcher)
    "testImplementation"(libs.assertj.core)
    "testImplementation"(libs.mockito.core)
}

tasks.named<Javadoc>("javadoc") {
    exclude("**/LICENSE")
}

tasks.named<Test>("test") {
    dependsOn(":WebClient:jar")
    val tmpDir = temporaryDir
    systemProperty("JRomManager.rootPath", "$rootDir")
    systemProperty("file.encoding", "utf-8")
    systemProperty("java.io.tmpdir", "$tmpDir")
    systemProperty("jrommanager.dir", "$tmpDir")
}

application {
    mainClass.set("jrm.server.Server")
}

tasks.named<JavaExec>("run") {
    group = "application"
    description = "Runs the JRomManager server with all dependencies."
    classpath = the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("jrm.server.Server")
    jvmArgs(
        "--sun-misc-unsafe-memory-access=allow",
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED"
    )
    args(
        "--ws",
        "-c", "$rootDir/WebClient/war",
        "-w", "%HOMEPATH%/.jrommanager"
    )
    workingDir = project.rootDir
    standardInput = System.`in`
}

tasks.register<JavaExec>("runFullServer") {
    group = "application"
    description = "Runs the JRomManager server with all dependencies."
    classpath = the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("jrm.fullserver.FullServer")
    jvmArgs(
        "--sun-misc-unsafe-memory-access=allow",
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED"
    )
    args(
        "--ws",
        "-c", "$rootDir/WebClient/war",
        "-d", "-w", "%HOMEPATH%/.jrommanager"
    )
    workingDir = project.rootDir
    standardInput = System.`in`
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to "JRomManager Server",
                "Implementation-Title" to "jrmserver"
            )
        )
    }
}
