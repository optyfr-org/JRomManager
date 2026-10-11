import java.util.function.Predicate
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.openjfx.gradle.JavaFXOptions

plugins {
    alias(libs.plugins.freefair.lombok)
    alias(libs.plugins.javafx.plugin)
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
    // Experimental. Stock native-image cannot statically link OpenJFX natives;
    // ship the JavaFX SDK platform libraries next to the binary.
    // javafx.web is on the default UI path (WebviewCellFactory). javafx.swing is unused
    // in main sources and stays in the JVM module set.
    agent {
        enabled.set(true)
        defaultMode.set("standard")
        tasksToInstrumentPredicate.set(Predicate { task -> task is Test })
        metadataCopy {
            inputTaskNames.addAll("test", "run")
            outputDirectories.add("src/main/resources/META-INF/native-image/jrm/jrmfx/")
            mergeWithExisting.set(true)
        }
    }
    binaries {
        named("main") {
            imageName.set("jrmfx")
            mainClass.set("jrm.fx.JRomManagerMulti")
            resources.autodetect()
            resources.includedPatterns.add(""".*\.dll$""")
            resources.includedPatterns.add("com/sun/javafx/.*")
            resources.includedPatterns.add("com/sun/prism/.*")
            resources.includedPatterns.add("com/sun/scenario/.*")
            resources.includedPatterns.add("com/sun/glass/.*")
            resources.includedPatterns.add("javafx/.*")
            // Per-OS native flags (native installers build on win/linux/macos runners).
            // Win-only glass/font classes must not be named on other OSes.
            val os = DefaultNativePlatform.getCurrentOperatingSystem()
            val osGlassInit = mutableListOf<String>()
            var prismOrder = listOf("-Dprism.maxvram=1G")
            if (os.isWindows) {
                osGlassInit.addAll(
                    listOf(
                        "--initialize-at-run-time=com.sun.glass.ui.win.WinApplication",
                        "--initialize-at-run-time=com.sun.glass.ui.win.WinPlatformFactory",
                        "--initialize-at-run-time=com.sun.javafx.font.directwrite.DWFactory"
                    )
                )
                // The Prism software pipeline loses the green channel in native-image
                // (all colors render magenta/violet); force the D3D hardware pipeline.
                prismOrder = listOf("-Dprism.maxvram=1G", "-Dprism.order=d3d")
            } else if (os.isLinux) {
                osGlassInit.addAll(
                    listOf(
                        "--initialize-at-run-time=com.sun.glass.ui.gtk.GtkApplication",
                        "--initialize-at-run-time=com.sun.glass.ui.gtk.GtkPlatformFactory",
                        "--initialize-at-run-time=com.sun.javafx.font.freetype.FTFactory"
                    )
                )
                // Headless/smoke runs fall back to software; prefer GPU when present.
                prismOrder = listOf("-Dprism.maxvram=1G", "-Dprism.order=es2,sw")
            } else if (os.isMacOsX) {
                osGlassInit.addAll(
                    listOf(
                        "--initialize-at-run-time=com.sun.glass.ui.mac.MacApplication",
                        "--initialize-at-run-time=com.sun.glass.ui.mac.MacPlatformFactory",
                        "--initialize-at-run-time=com.sun.javafx.font.coretext.CTFactory"
                    )
                )
                prismOrder = listOf("-Dprism.maxvram=1G", "-Dprism.order=es2,sw")
            }
            buildArgs.addAll(
                "-J--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                "-J--add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED",
                // Builder JVM only; not a runtime image argument.
                "-J--sun-misc-unsafe-memory-access=allow",
                "-J--enable-native-access=javafx.graphics,ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED",
                // RHScoreboard precedent: toolkit/shader classes must init at run time.
                // JavaFX 25 renamed com.sun.prism.* to com.sun.prism.impl.*.
                "--initialize-at-run-time=com.sun.glass.ui.Application",
                "--initialize-at-run-time=com.sun.glass.ui.Screen"
            )
            buildArgs.addAll(osGlassInit)
            // Native JavaFX resources per host OS (dll/so/dylib + shaders).
            resources.includedPatterns.add(""".*\.so$""")
            resources.includedPatterns.add(""".*\.dylib$""")
            if (os.isMacOsX) resources.includedPatterns.add(""".*\.jnilib$""")
            runtimeArgs.addAll(prismOrder)
        }
    }
}

extensions.configure<JavaFXOptions>("javafx") {
    version = "25"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web", "javafx.swing")
}

dependencies {
    "compileOnly"(libs.jsr305)
    "implementation"(project(":jrmcore"))
    "implementation"(project(":res-icons"))
    "implementation"(libs.jcommander)
    "implementation"(libs.commons.io)
    "implementation"(libs.commons.text)
    "implementation"(libs.commons.lang3)
    "implementation"(libs.gson)
    "implementation"(libs.minimal.json)

    "testImplementation"(libs.bundles.junit.jupiter.stack)
    "testImplementation"(libs.assertj.core)
    "testImplementation"(libs.mockito.core)
    "testRuntimeOnly"(libs.junit.platform.launcher)
    "testImplementation"(libs.testfx.junit)
    "testImplementation"(libs.testfx.assertj)
}

application {
    mainClass.set("jrm.fx.JRomManagerMulti")
}

tasks.named<JavaExec>("run") {
    jvmArgs(
        "--enable-native-access=javafx.graphics",
        "--add-opens", "javafx.controls/javafx.scene.control.skin=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow"
    )
}

tasks.named<Jar>("jar") {
    val rootProps = JrmProps.load(rootDir)
    manifest {
        attributes(
            mapOf(
                "Manifest-Version" to "1.0",
                "Specification-Title" to "JRomManager JFX",
                "Specification-Version" to rootProps.getProperty("Specification-Version"),
                "Implementation-Title" to "jrm",
                "Implementation-Version" to rootProps.getProperty("Implementation-Version")
            )
        )
    }
}

tasks.named<Javadoc>("javadoc") {
    exclude("**/*.fxml")
    exclude("**/*.css")
}

tasks.named<Test>("test") {
    val tmpDir = temporaryDir
    systemProperty("file.encoding", "utf-8")
    systemProperty("java.io.tmpdir", "$tmpDir")
    systemProperty("jrommanager.dir", "$tmpDir")
}
