import de.undercouch.gradle.tasks.download.Download
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

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
    dest(layout.buildDirectory.file("commons-daemon-$daemonVersion-bin-windows.zip"))
    onlyIfModified(true)
}

tasks.register<Copy>("extractDaemonWin") {
    dependsOn("downloadDaemonWin")
    from(zipTree(layout.buildDirectory.file("commons-daemon-$daemonVersion-bin-windows.zip")))
    include("amd64/*.exe", "prunmgr.exe")
    eachFile {
        path = name
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("commons-daemon-$daemonVersion-bin-windows"))
}

tasks.register<Download>("downloadDaemonLinux") {
    src("https://archive.apache.org/dist/commons/daemon/source/commons-daemon-$daemonVersion-native-src.tar.gz")
    dest(layout.buildDirectory.file("commons-daemon-$daemonVersion-native-src.tar.gz"))
    onlyIfModified(true)
}

tasks.register<Copy>("extractDaemonLinux") {
    dependsOn("downloadDaemonLinux")
    from(tarTree(layout.buildDirectory.file("commons-daemon-$daemonVersion-native-src.tar.gz")))
    into(layout.buildDirectory.dir("jsvc"))
}

tasks.register<Exec>("configureDaemonLinux") {
    dependsOn("extractDaemonLinux")
    workingDir(layout.buildDirectory.dir("jsvc/commons-daemon-$daemonVersion-native-src/unix"))
    commandLine("./configure", "--silent", "--with-java=" + System.getProperty("java.home"))
    standardOutput = ByteArrayOutputStream()
    doLast {
        print((standardOutput as ByteArrayOutputStream).toString().trim())
    }
}

tasks.register<Exec>("compileDaemonLinux") {
    dependsOn("configureDaemonLinux")
    workingDir(layout.buildDirectory.dir("jsvc/commons-daemon-$daemonVersion-native-src/unix"))
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
        // System (java.*/jdk.*) modules must be declared explicitly: the plugin maps
        // code packages to system modules via "$javaHome/jmods", which does not exist on
        // JEP 493 linkable runtimes (Temurin 25, used by CI), so derivation silently drops
        // them there and the image fails at runtime (e.g. java.desktop for
        // java.beans.Beans). excludeRequires keeps full-JDK builds compiling (derivation
        // already emits them); requires re-adds them exactly once where derivation missed
        // them. (Third-party modules keep coming from derivation: their jars are scanned
        // directly and work on both layouts.)
        excludeRequires(
            "java.compiler",
            "java.datatransfer",
            "java.desktop",
            "java.instrument",
            "java.logging",
            "java.management",
            "java.naming",
            "java.rmi",
            "java.scripting",
            "java.sql",
            "java.transaction.xa",
            "java.xml",
            "jdk.httpserver"
        )
        requires("ch.qos.logback.classic")
        requires("java.compiler")
        requires("java.datatransfer")
        requires("java.desktop")
        requires("java.instrument")
        requires("java.logging")
        requires("java.management")
        requires("java.naming")
        requires("java.rmi")
        requires("java.scripting")
        requires("java.sql")
        requires("java.transaction.xa")
        requires("java.xml")
        requires("jdk.httpserver")
        requires("jdk.zipfs")
        // Pull the JDK ALPN provider into the image: Jetty's ALPNServerConnectionFactory looks up
        // ALPNProcessor$Server via ServiceLoader, and jlink only includes provider modules that are
        // reachable through a requires edge (without this, FullServer fails with "No Server ALPNProcessors!").
        requires("org.eclipse.jetty.alpn.java.server")
    }
    // --sun-misc-unsafe-memory-access=allow on every launcher: Fory calls the terminally
    // deprecated sun.misc.Unsafe::staticFieldOffset, and without this flag every start prints a
    // warning that cannot be silenced from code.
    // AOT portability (JEP 483/514): the cache records UseCompressedOops.
    // MaxRAMPercentage alone makes the heap (and thus the oops mode)
    // machine-dependent: CI trains at ~5G, a 64G desktop runs at 48G with oops
    // off -> "Unable to map shared spaces". Pin oops explicitly and cap the heap
    // so it stays in the compressed range everywhere. (UseCompressedClassPointers
    // is deprecated in 25, so only UseCompressedOops is pinned.)
    // --add-modules=jdk.internal.vm.ci is explicit so training (which reuses
    // these jvmArgs) and the jlink-generated runtime launchers agree; otherwise
    // the cache reports "Mismatched values for jdk.module.addmods" and disables
    // optimized module handling / AOT-linked classes.
    launcher {
        name = "JRomManager"
        moduleName = "jrm.merged.module"
        mainClass = "jrm.fx.JRomManagerMulti"
        jvmArgs = mutableListOf("-Xlog:aot", "-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "-XX:MaxHeapSize=8G", "-XX:+UseCompressedOops", "--add-modules=jdk.internal.vm.ci", "--sun-misc-unsafe-memory-access=allow", "--enable-native-access=javafx.graphics,jrm.merged.module", "-XX:AOTCache=\$APPDIR/aot/JRomManager.aot")
    }
    secondaryLauncher {
        name = "JRomManagerSwing"
        moduleName = "jrm.merged.module"
        mainClass = "jrm.JRomManagerMulti"
        jvmArgs = mutableListOf("-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "-XX:MaxHeapSize=8G", "-XX:+UseCompressedOops", "--add-modules=jdk.internal.vm.ci", "--sun-misc-unsafe-memory-access=allow", "--enable-native-access=jrm.merged.module", "-XX:AOTCache=\$APPDIR/aot/JRomManagerSwing.aot")
    }
    secondaryLauncher {
        name = "JRomManagerCLI"
        moduleName = "jrm.merged.module"
        mainClass = "jrm.cli.JRomManagerCLI"
        winConsole = true
        // Note: no org.jline.terminal.ffm entry: jline is merged into jrm.merged.module,
        // so there is no such module in the image and naming it only prints an "Unknown module" warning.
        jvmArgs = mutableListOf("-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "-XX:MaxHeapSize=8G", "-XX:+UseCompressedOops", "--add-modules=jdk.internal.vm.ci", "--sun-misc-unsafe-memory-access=allow", "--enable-native-access=jrm.merged.module", "-XX:AOTCache=\$APPDIR/aot/JRomManagerCLI.aot")
    }
    secondaryLauncher {
        name = "JRomManagerServer"
        moduleName = "jrm.merged.module"
        mainClass = "jrm.server.Server"
        winConsole = true
        jvmArgs = mutableListOf("-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "-XX:MaxHeapSize=8G", "-XX:+UseCompressedOops", "--add-modules=jdk.internal.vm.ci", "--sun-misc-unsafe-memory-access=allow", "-XX:AOTCache=\$APPDIR/aot/JRomManagerServer.aot")
        args = mutableListOf("--workpath", "%HOMEPATH%/.jrommanager")
    }
    secondaryLauncher {
        name = "JRomManagerFullServer"
        moduleName = "jrm.merged.module"
        mainClass = "jrm.fullserver.FullServer"
        winConsole = true
        jvmArgs = mutableListOf("-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "-XX:MaxHeapSize=8G", "-XX:+UseCompressedOops", "--add-modules=jdk.internal.vm.ci", "--sun-misc-unsafe-memory-access=allow", "-XX:AOTCache=\$APPDIR/aot/JRomManagerFullServer.aot")
        args = mutableListOf("--workpath", "%HOMEPATH%/.jrommanager")
    }
    jpackage {
        // JPackageData uses plain Strings (only image/installerOptions are ListProperty).
        installerType = project.findProperty("installerType") as String?
        targetPlatformName = project.findProperty("targetPlatform") as String?
        // RPM Version fields forbid '-' and MSI ProductVersion / macOS
        // CFBundleVersion must be numeric: map e.g. 4.0.0-beta.1 to
        // 4.0.0~beta.1 (rpm) / 4.0.0 (msi, dmg) via JrmRelease.
        appVersion = JrmRelease.appVersionFor(version.toString(), installerType)
        // getter returns File but setter takes Object: pass the lazy Directory provider.
        setInstallerOutputDir(layout.buildDirectory.dir("installer"))
        imageName = "JRomManager"
        // Per-OS icon: jpackage validates the top-level --icon against the
        // target bundler (dmg requires .icns, deb/rpm want .png, msi/exe
        // want .ico). A single .ico here makes the Mac DMG bundler warn
        // 'not an ICNS file' and fall back to the default icon.
        icon = when (installerType) {
            "dmg" -> "dist/JRomManager.icns"
            "deb", "rpm" -> "dist/JRomManager.png"
            else -> "dist/JRomManager.ico"
        }
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
            // macOS icons must be .icns (.ico only triggers a "not an ICNS
            // file" warning and falls back to the default icon).
            imageOptions.set(listOf("--icon", "dist/JRomManager.icns"))
            installerOptions.set(
                listOf(
                    "--mac-package-name", "jrommanager",
                    "--mac-app-category", "public.app-category.utilities",
                    "--mac-package-identifier", "org.optyfr.jrommanager"
                )
            )
        }
    }
}

tasks.named("prepareMergedJarsDir") {
    dependsOn("jar")
}

// AOT cache training (JEP 483/514): one .aot file per launcher, trained with the jlink image's own
// java after `jlink` and before `jpackage`, then shipped at $APPDIR/aot/<Launcher>.aot.
// Only runs when `jpackage` is in the task graph, so plain build/test never trains.
// Caches are written outside the jlink image (build/aot-caches) because jpackage copies
// build/image/app into the runtime, not into $APPDIR; aotCachesIntoImage then places them
// at $APPDIR/aot: lib/app on Linux, Contents/app on macOS, app on Windows.
val aotFixtureDir = file("dist/aot-fixture")
val aotCachesDir = layout.buildDirectory.dir("aot-caches")
tasks.register("aotCaches") {
    dependsOn("jlink")
    onlyIf { gradle.taskGraph.hasTask(":jpackage") }
    doLast {
        val imageDir = project.layout.buildDirectory.dir("image").get().asFile
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val isLinux = osName.contains("linux")
        val isMac = osName.contains("mac") || osName.contains("darwin")
        // JavaFX/Swing training needs a display (xvfb-run on Linux). macos-x64 CI
        // hangs on CGLChoosePixelFormat until the 10 min timeout, while macos-aarch64
        // trains fine, so skip the GUI trainers only on Intel macOS.
        val arch = System.getProperty("os.arch").lowercase()
        val isMacX64 = isMac && !arch.contains("aarch64") && !arch.contains("arm64")
        val guiLaunchers = setOf("JRomManager", "JRomManagerSwing")
        val javaBin = File(imageDir, "bin/java" + if (isWindows) ".exe" else "")
        val appDir = File(imageDir, "app")
        val outDir = aotCachesDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val jlinkExt = project.extensions.getByType<org.beryx.jlink.data.JlinkPluginExtension>()
        val primary = jlinkExt.launcherData.get()
        val launchers = listOf(
            Triple(primary.name, jlinkExt.moduleName.get() + "/" + jlinkExt.mainClass.get(), primary.jvmArgs)
        ) + jlinkExt.secondaryLaunchers.get().map { Triple(it.name, it.moduleName + "/" + it.mainClass, it.jvmArgs) }
        for ((name, main, rawJvmArgs) in launchers) {
            if (isMacX64 && name in guiLaunchers) {
                project.logger.warn("Skipping AOT training for $name on macOS-x64 (no display); launcher will run without AOT cache")
                continue
            }
            val jvmArgs = if (rawJvmArgs == org.beryx.jlink.data.LauncherData.UNDEFINED_ARGS)
                emptyList()
            else
                rawJvmArgs.filter { !it.startsWith("-XX:AOTCache=") }
            val out = File(outDir, "$name.aot")
            val trainDir = project.layout.buildDirectory.dir("aot-train/$name").get().asFile.apply { deleteRecursively(); mkdirs() }
            val isServer = name.endsWith("Server")
            // Servers take --work <dir>; desktop launchers are multi-user and resolve their work
            // path from user.home, so point -Duser.home at the temp dir (work path = <dir>/.jrommanager).
            val workPath = if (isServer) trainDir else File(trainDir, ".jrommanager")
            File(workPath, "roms").mkdirs()
            project.copy {
                from(aotFixtureDir)
                into(workPath)
            }
            val fixture = File(workPath, "aot-fixture.dat").absolutePath
            val cmd = mutableListOf<String>()
            if (isLinux && !isServer && name != "JRomManagerCLI")
                cmd += listOf("xvfb-run", "-a")
            cmd += javaBin.absolutePath
            cmd += jvmArgs
            cmd += listOf(
                "-XX:AOTCacheOutput=${out.absolutePath}",
                "-Djrm.aot.train=true",
                "-Djrm.aot.fixture=$fixture"
            )
            if (!isServer)
                cmd += "-Duser.home=${trainDir.absolutePath}"
            cmd += listOf("-p", appDir.absolutePath, "-m", main)
            if (isServer) {
                val port = ServerSocket(0).use { it.localPort }
                cmd += listOf("--bind", "127.0.0.1", "--http", port.toString(), "--work", trainDir.absolutePath)
                if (name == "JRomManagerFullServer") {
                    // exercise the HTTPS/HTTP2 (ALPN) connector during training as well
                    val httpsPort = ServerSocket(0).use { it.localPort }
                    cmd += listOf("--https", httpsPort.toString())
                }
            }
            project.logger.lifecycle("AOT training $name")
            val proc = ProcessBuilder(cmd).directory(trainDir).redirectErrorStream(true).start()
            if (name == "JRomManagerCLI") {
                proc.outputStream.use { os ->
                    os.write("load \"$fixture\"\nscan\nexit\n".toByteArray(Charsets.UTF_8))
                    os.flush()
                }
            } else {
                // EOF on stdin: the servers' interactive stop loop must not block on hasNextLine()
                proc.outputStream.close()
            }
            val pump = Thread {
                try {
                    proc.inputStream.transferTo(System.out)
                } catch (_: java.io.IOException) {
                    // process ended
                }
            }
            pump.isDaemon = true
            pump.start()
            if (!proc.waitFor(10, TimeUnit.MINUTES)) {
                proc.destroyForcibly()
                if (name in guiLaunchers) {
                    project.logger.warn("AOT training for $name timed out; continuing without its cache")
                    out.delete()
                    continue
                }
                throw GradleException("AOT training for $name timed out")
            }
            if (proc.exitValue() != 0) {
                if (name in guiLaunchers) {
                    project.logger.warn("AOT training for $name exited with code ${proc.exitValue()}; continuing without its cache")
                    out.delete()
                    continue
                }
                throw GradleException("AOT training for $name exited with code ${proc.exitValue()}")
            }
            if (!out.isFile) {
                if (name in guiLaunchers) {
                    project.logger.warn("AOT training for $name produced no cache; continuing without it")
                    continue
                }
                throw GradleException("AOT training for $name produced no cache at $out")
            }
            project.logger.lifecycle("AOT cache $name: ${out.length()} bytes")
        }
    }
}

// Copies the trained caches into the jpackage app image at $APPDIR/aot after jpackageImage
// (which wipes its output dir, so this must run after it).
tasks.register("aotCachesIntoImage") {
    dependsOn("jpackageImage", "aotCaches")
    onlyIf { gradle.taskGraph.hasTask(":jpackage") }
    doLast {
        // JDK 25 jpackage $APPDIR (ApplicationLayoutUtils / packaging overview):
        //   Linux  <image>/lib/app
        //   macOS  <image>.app/Contents/app
        //   Windows <image>/app
        // badass-jlink prefers <imageName> over <imageName>.app when that directory exists,
        // so creating <imageName>/ on macOS hides the bundle and jpackage reports
        // "Missing .jpackage.xml". The same mistake on Linux creates <image>/app instead of
        // <image>/lib/app. Create the app directory itself, then aot inside it.
        val jlinkExt = project.extensions.getByType<org.beryx.jlink.data.JlinkPluginExtension>()
        val imageName = jlinkExt.launcherData.get().name ?: project.name
        val osName = System.getProperty("os.name").lowercase()
        val isMac = osName.contains("mac")
        val isWindows = osName.contains("win")
        val imageRoot = project.layout.buildDirectory.dir("jpackage").get().asFile
        val macBundle = File(imageRoot, "$imageName.app")
        val plain = File(imageRoot, imageName)
        if (isMac && macBundle.isDirectory && plain.isDirectory)
            plain.deleteRecursively()
        val appImage = when {
            isMac && macBundle.isDirectory -> macBundle
            plain.isDirectory -> plain
            macBundle.isDirectory -> macBundle
            else -> throw GradleException("Unable to find the jpackage application image in ${imageRoot.absolutePath}")
        }
        val appDir = File(appImage, when {
            isMac -> "Contents/app"
            isWindows -> "app"
            else -> "lib/app"
        })
        if (!appDir.isDirectory && !appDir.mkdirs())
            throw GradleException("cannot create jpackage app directory: ${appDir.absolutePath}")
        if (!isWindows) {
            val strayApp = File(appImage, "app")
            if (strayApp != appDir && strayApp.isDirectory && !File(strayApp, ".jpackage.xml").isFile)
                strayApp.deleteRecursively()
        }
        val dest = File(appDir, "aot")
        if (!dest.isDirectory && !dest.mkdirs())
            throw GradleException("cannot create AOT directory: ${dest.absolutePath}")
        project.copy {
            from(aotCachesDir)
            into(dest)
        }
        project.logger.lifecycle("AOT caches installed into ${dest.absolutePath}")
        // Launchers whose training was skipped (macOS GUI) still ship
        // -XX:AOTCache=$APPDIR/aot/<Launcher>.aot in their .cfg. A missing cache
        // makes the JVM fail at startup, so strip the flag when the file is absent.
        val launcherNames = listOf(jlinkExt.launcherData.get().name) + jlinkExt.secondaryLaunchers.get().map { it.name }
        for (launcherName in launcherNames) {
            val cache = File(dest, "$launcherName.aot")
            if (cache.isFile) continue
            val cfg = File(appDir, "$launcherName.cfg")
            if (!cfg.isFile) {
                project.logger.warn("AOT cache missing for $launcherName but no ${cfg.absolutePath} to patch")
                continue
            }
            val lines = cfg.readLines()
            val kept = lines.filterNot { it.trim().startsWith("java-options=-XX:AOTCache=") || it.trim() == "-XX:AOTCache=" || it.contains("-XX:AOTCache=") }
            if (kept.size != lines.size) {
                cfg.writeText(kept.joinToString("\n") + "\n")
                project.logger.warn("Stripped dangling -XX:AOTCache for $launcherName (no cache trained)")
            }
        }
    }
}

tasks.named("jpackage") {
    dependsOn("aotCachesIntoImage")
}


