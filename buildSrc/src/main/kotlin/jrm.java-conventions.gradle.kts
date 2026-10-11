import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

// Precompiled replacement for gradle/java-conventions.gradle.kts.
// As a real plugin, typed accessors (java {}) resolve — the
// extensions.configure<>() workarounds of the apply(from=) era are gone.
// No sonar {} block here (same as before): sonar properties live in the root
// build script and subprojects inherit them. No jsr305 here either: each
// subproject declares "compileOnly"(libs.jsr305) with its typed accessor.
plugins {
    java
    jacoco
}

// Shared dist/ver.properties, read directly: no rootProject.extra ordering.
val rootProps = JrmProps.load(rootDir)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
    options.encoding = "UTF-8"
    if (project.name == "WebClient") {
        options.release.set(17)
    }
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}

version = "${rootProps.getProperty("Specification-Version")}.${rootProps.getProperty("Implementation-Version")}"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
    extensions.configure<JacocoTaskExtension>("jacoco") {
        isEnabled = true
    }
    finalizedBy(tasks.named("jacocoTestReport"))
    minHeapSize = "256m"
    maxHeapSize = "3G"
    jvmArgs(
        "-XX:+EnableDynamicAgentLoading",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED"
    )
    testLogging {
        events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.SHORT
        showExceptions = true
        showCauses = true
        showStackTraces = true
        debug {
            events(
                TestLogEvent.STARTED, TestLogEvent.FAILED, TestLogEvent.PASSED,
                TestLogEvent.SKIPPED, TestLogEvent.STANDARD_ERROR, TestLogEvent.STANDARD_OUT
            )
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
        info.events = debug.events
        info.exceptionFormat = debug.exceptionFormat
        addTestListener(object : TestListener {
            override fun afterSuite(desc: TestDescriptor, result: TestResult) {
                if (desc.parent == null) {
                    val output = "Results: ${result.resultType} (${result.testCount} tests, " +
                        "${result.successfulTestCount} successes, ${result.failedTestCount} failures, " +
                        "${result.skippedTestCount} skipped)"
                    val startItem = "|  "
                    val endItem = "  |"
                    val repeatLength = startItem.length + output.length + endItem.length
                    println("\n" + "-".repeat(repeatLength) + "\n" + startItem + output + endItem + "\n" + "-".repeat(repeatLength))
                }
            }
        })
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Manifest-Version" to "1.0",
                "Specification-Title" to "JRomManager",
                "Specification-Version" to rootProps.getProperty("Specification-Version"),
                "Implementation-Title" to "jrm",
                "Implementation-Version" to rootProps.getProperty("Implementation-Version")
            )
        )
    }
    archiveFileName.set("jrm-" + project.name + "-" + project.version + ".jar")
}
