import de.undercouch.gradle.tasks.download.Download
import java.io.ByteArrayOutputStream

buildscript {
    // NOTE: `libs` is not visible inside buildscript. These pins mirror
    // gradle/libs.versions.toml (aircompressor, bouncycastle) and are kept in
    // sync by scripts/refresh-locks.sh — do not hand-edit.
    configurations.all {
        resolutionStrategy {
            force("io.airlift:aircompressor:2.0.3")
            dependencySubstitution {
                substitute(module("org.bouncycastle:bcpg-jdk15on")).using(module("org.bouncycastle:bcpg-jdk18on:1.86"))
                substitute(module("org.bouncycastle:bcprov-jdk15on")).using(module("org.bouncycastle:bcprov-jdk18on:1.86"))
            }
        }
    }
    configurations.getByName("classpath") {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    java
    application
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.gradle.download.task)
    alias(libs.plugins.freefair.lombok)
    alias(libs.plugins.javafx.plugin)
    alias(libs.plugins.beryx.jlink)
    jacoco
    alias(libs.plugins.javadoc.links)
    alias(libs.plugins.graalvm.native) apply false
    // jrm.packaging-* are applied by id below (after the java toolchain block):
    // their apply-time configuration inspection finalizes the toolchain spec.
}

dependencyLocking {
    lockAllConfigurations()
    // LENIENT = lock mismatches warn instead of failing the build.
    // Dynamic versions are still pinned; added/removed/shifted modules no longer error.
    lockMode.set(LockMode.LENIENT)
}

sonar {
    properties {
        property("sonar.projectKey", "optyfr-org_JRomManager")
        property("sonar.organization", "optyfr-org")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "utf-8")
        property("sonar.log.level", "WARNING")
        property("sonar.verbose", "false")
        property("sonar.scm.provider", "git")
        property("sonar.coverage.jacoco.xmlReportPaths", "${rootDir}/build/reports/jacoco/codeCoverageReport/codeCoverageReport.xml")
    }
}

// Shared ver.properties via buildSrc JrmProps (no extra["props"] ordering:
// convention plugins read dist/ver.properties directly through the helper).
version = JrmProps.projectVersion(rootDir)

tasks.register<JacocoReport>("codeCoverageReport") {
    subprojects {
        plugins.withType<JacocoPlugin>().configureEach {
            tasks.matching { t -> t.extensions.findByType<JacocoTaskExtension>() != null }.configureEach {
                val testTask = this
                sourceSets(project.the<SourceSetContainer>()["main"])
                executionData(testTask)
            }
        }
    }
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named("codeCoverageReport") {
    // provider {} keeps this lazy: eager subprojects.map would force child
    // evaluation during root configuration (breaking plugins using afterEvaluate).
    dependsOn(provider { subprojects.map { it.tasks.named("test") } })
}

apply(plugin = "java")

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "utf-8"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

apply(plugin = "eclipse")
extensions.configure<org.gradle.plugins.ide.eclipse.model.EclipseModel>("eclipse") {
    classpath {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

lombok {
    version.set(libs.versions.lombok.get())
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

subprojects {
    group = "jrm"
    apply(plugin = "jrm.java-conventions")
}

// Precompiled packaging plugins, applied by id AFTER the java toolchain block
// above: their bodies inspect configurations (e.g. attribute copying), which
// finalizes the toolchain spec — the old apply(from=) scripts ran here too.
apply(plugin = "jrm.packaging-jars")
apply(plugin = "jrm.packaging-distributions")
apply(plugin = "jrm.aggregated-javadoc")
apply(plugin = "jrm.packaging-native")
apply(plugin = "jrm.packaging-ospackage")

// NOTE: packaging-daemon tasks stay inlined here (not a precompiled plugin):
// they need the third-party Download task type on the script classpath,
// which only the root script (via plugins {} above) has.
// Daemon version comes from buildSrc JrmProps (dist/ver.properties).
val daemonVersion = JrmProps.daemonVersion(rootDir)
tasks.register<Download>("downloadDaemonWin") {
    src("https://archive.apache.org/dist/commons/daemon/binaries/windows/commons-daemon-$daemonVersion-bin-windows.zip")
    dest(file("$buildDir/commons-daemon-$daemonVersion-bin-windows.zip"))
    onlyIfModified(true)
}

tasks.register<Copy>("extractDaemonWin") {
    dependsOn("downloadDaemonWin")
    from(zipTree(file("$buildDir/commons-daemon-$daemonVersion-bin-windows.zip")))
    include("amd64/*.exe", "prunmgr.exe")
    eachFile {
        path = name
    }
    includeEmptyDirs = false
    into("$buildDir/commons-daemon-$daemonVersion-bin-windows")
}

tasks.register<Download>("downloadDaemonLinux") {
    src("https://archive.apache.org/dist/commons/daemon/source/commons-daemon-$daemonVersion-native-src.tar.gz")
    dest(file("$buildDir/commons-daemon-$daemonVersion-native-src.tar.gz"))
    onlyIfModified(true)
}

tasks.register<Copy>("extractDaemonLinux") {
    dependsOn("downloadDaemonLinux")
    from(tarTree(file("$buildDir/commons-daemon-$daemonVersion-native-src.tar.gz")))
    into("$buildDir/jsvc")
}

tasks.register<Exec>("configureDaemonLinux") {
    dependsOn("extractDaemonLinux")
    workingDir("$buildDir/jsvc/commons-daemon-$daemonVersion-native-src/unix")
    commandLine("./configure", "--silent", "--with-java=" + System.getProperty("java.home"))
    standardOutput = ByteArrayOutputStream()
    doLast {
        print((standardOutput as ByteArrayOutputStream).toString().trim())
    }
}

tasks.register<Exec>("compileDaemonLinux") {
    dependsOn("configureDaemonLinux")
    workingDir("$buildDir/jsvc/commons-daemon-$daemonVersion-native-src/unix")
    commandLine("make", "--silent")
    standardOutput = ByteArrayOutputStream()
    doLast {
        print((standardOutput as ByteArrayOutputStream).toString().trim())
    }
}

jlink {
    mergedModuleName.set("jrm.merged.module")
    configuration.set("jlinkConfig")
    addExtraDependencies("javafx")
    mergedModule {
        isAdditive = true
        excludeProvides(mapOf("service" to "org.apache.fory.shaded.org.codehaus.commons.compiler.ICompilerFactory"))
        requires("ch.qos.logback.classic")
        requires("jdk.zipfs")
        // java.desktop is auto-derived by the jlink plugin; re-declaring it breaks
        // the merged module-info compilation with "duplicate requires".
    }
    // --sun-misc-unsafe-memory-access=allow on every launcher: Fory calls the terminally
    // deprecated sun.misc.Unsafe::staticFieldOffset, and without this flag every start prints a
    // warning that cannot be silenced from code.
    launcher {
        name = "JRomManager"
        moduleName = "jrm.merged.module"
        mainClass = "jrm.fx.JRomManagerMulti"
        jvmArgs = mutableListOf("-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "--sun-misc-unsafe-memory-access=allow", "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED", "--add-opens=javafx.controls/javafx.scene.control.skin=jrm.merged.module", "--enable-native-access=javafx.graphics,jrm.merged.module")
    }
    secondaryLauncher {
        name = "JRomManagerSwing"
        moduleName = "jrm.merged.module"
        mainClass = "jrm.JRomManagerMulti"
        jvmArgs = mutableListOf("-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "--sun-misc-unsafe-memory-access=allow", "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED", "--enable-native-access=jrm.merged.module")
    }
    secondaryLauncher {
        name = "JRomManagerCLI"
        moduleName = "jrm.merged.module"
        mainClass = "jrm.cli.JRomManagerCLI"
        isWinConsole = true
        // Note: no org.jline.terminal.ffm entry: jline is merged into jrm.merged.module,
        // so there is no such module in the image and naming it only prints an "Unknown module" warning.
        jvmArgs = mutableListOf("-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "--sun-misc-unsafe-memory-access=allow", "--enable-native-access=jrm.merged.module", "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
    }
    secondaryLauncher {
        name = "JRomManagerServer"
        moduleName = "jrm.merged.module"
        mainClass = "jrm.server.Server"
        isWinConsole = true
        jvmArgs = mutableListOf("-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "--sun-misc-unsafe-memory-access=allow", "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
        args = mutableListOf("--workpath", "%HOMEPATH%/.jrommanager")
    }
    secondaryLauncher {
        name = "JRomManagerFullServer"
        moduleName = "jrm.merged.module"
        mainClass = "jrm.fullserver.FullServer"
        isWinConsole = true
        jvmArgs = mutableListOf("-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "--sun-misc-unsafe-memory-access=allow", "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
        args = mutableListOf("--workpath", "%HOMEPATH%/.jrommanager")
    }
    jpackage {
        // JPackageData uses plain Strings (only image/installerOptions are ListProperty).
        installerType = project.findProperty("installerType") as String?
        targetPlatformName = project.findProperty("targetPlatform") as String?
        // getter returns File but setter takes Object: no Kotlin property, call it explicitly.
        setInstallerOutputDir(file("$buildDir/installer"))
        imageName = "JRomManager"
        icon = "dist/JRomManager.ico"
        if (installerType == "msi") {
            imageOptions.set(listOf("--icon", "dist/JRomManager.ico"))
            installerOptions.set(listOf("--win-dir-chooser", "--win-menu", "--win-shortcut"))
            installerName = "JRomManager-$targetPlatformName"
        }
        if (installerType == "deb" || installerType == "rpm") {
            imageOptions.set(listOf("--icon", "dist/JRomManager.png"))
            installerOptions.set(
                listOf(
                    "--linux-package-name", "jrommanager",
                    "--linux-app-category", "utils",
                    "--linux-app-release", "1",
                    "--linux-rpm-license-type", "GPL-2",
                    "--linux-menu-group", "Utility",
                    "--linux-shortcut"
                )
            )
        }
        if (installerType == "dmg") {
            installerName = "JRomManager-$targetPlatformName"
        }
    }
}

tasks.named("prepareMergedJarsDir") {
    dependsOn("jar")
}


