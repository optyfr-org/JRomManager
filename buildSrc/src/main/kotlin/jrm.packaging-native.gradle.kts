// Native-image installers, one per role, for Windows / Linux / macOS.
//
// Roles (answer: "un installeur par rôle"):
//   servers : jrmserver + jrmfullserver + jrmcli + WebClient/war content
//   gui     : jrmfx (JavaFX desktop, experimental — see jrmfx/build.gradle)
//
// Strategy: GraalVM nativeCompile produces the binaries. jpackage cannot wrap
// a native binary directly (it always builds a Java launcher + runtime), so
// each role goes through the documented two-step recipe:
//   1. jpackage --type app-image with a stub --main-jar  -> stock app-image layout
//   2. replace the java launcher(s) with the GraalVM native binaries,
//      drop native resources next to them (WebClient/war client dir, icons)
//   3. jpackage --app-image <dir> --type <msi|deb|rpm|dmg> -> platform installer
//
// Platform installers (answer: "jpackage natif"):
//   Windows : MSI  (needs WiX 3.x on PATH)
//   Linux   : DEB + RPM (needs dpkg / rpmbuild)
//   macOS   : DMG
//
// Signing (answer: "non signés"): installers are unsigned. Hook points via
// project properties (wired when secrets exist, no task rewiring needed):
//   -PnativeWinSignPfx=<path> -PnativeWinSignPass=<pwd>
//   -PnativeMacSignIdentity=<name> -PnativeMacNotarizeProfile=<profile>
// When set, the flags are appended to the step-3 jpackage command lines.
//
// CI: .github/workflows/native-release.yml builds the four OS jobs with
// GraalVM 25 and calls :nativeServersInstaller / :nativeGuiInstaller.
// Local build (same OS as host):
//   ./gradlew nativeServersInstaller   (or nativeGuiInstaller)
//   ./gradlew nativeInstallers         (both roles)
//
// Precompiled replacement for gradle/packaging-native.gradle.kts (verbatim).

import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.nio.file.Files

val currentOs = DefaultNativePlatform.getCurrentOperatingSystem()
val currentArch = DefaultNativePlatform.getCurrentArchitecture()

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
fun nativePlatformClassifier(): String {
    val osName = when {
        currentOs.isWindows -> "win"
        currentOs.isLinux -> "linux"
        currentOs.isMacOsX -> "macos"
        else -> currentOs.name.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-")
    }
    val archName = when {
        currentArch.isAmd64 -> "x64"
        currentArch.isArm64 -> "aarch64"
        currentArch.isI386 -> "x32"
        else -> currentArch.name.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")
    }
    return "$osName-$archName"
}

val nativeClassifier = nativePlatformClassifier()
val jpackageBin = "${System.getProperty("java.home")}/bin/jpackage"

// Per-OS default installer types, overridable with -PnativeInstallerTypes=msi,exe ...
val nativeInstallerTypes: List<String> =
    (project.findProperty("nativeInstallerTypes") as String?)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: when {
            currentOs.isWindows -> listOf("msi")
            currentOs.isLinux -> listOf("deb", "rpm")
            currentOs.isMacOsX -> listOf("dmg")
            else -> listOf("app-image")
        }

// jpackage input stubs (a minimal jar satisfies --main-jar for the step-1 app-image)
val nativeStubDir = layout.buildDirectory.dir("native-app/stub")

// The launcher binaries produced by nativeCompile tasks.
// Task names are nativeCompile (binary `main`) and nativeFullServerCompile
// (binary `fullServer`); there is no `nativeMainCompile`.
// Resolved lazily as task paths: the GraalVM plugin registers these tasks
// after root script evaluation, so eager tasks.named() fails at configure time.
val jrmServerBinary = ":jrmserver:nativeCompile"
val jrmFullServerBinary = ":jrmserver:nativeFullServerCompile"
val jrmCliBinary = ":jrmcli:nativeCompile"
val jrmFxBinary = ":jrmfx:nativeCompile"
// Output dirs of the native binaries (imageName per build.gradle).
fun jrmServerOut() = project(":jrmserver").layout.buildDirectory.dir("native/nativeCompile")
fun jrmFullServerOut() = project(":jrmserver").layout.buildDirectory.dir("native/nativeFullServerCompile")
fun jrmCliOut() = project(":jrmcli").layout.buildDirectory.dir("native/nativeCompile")
fun jrmFxOut() = project(":jrmfx").layout.buildDirectory.dir("native/nativeCompile")

fun signingArgs(): List<String> {
    val extra = mutableListOf<String>()
    if (currentOs.isWindows) {
        // Hook: codesigning via jpackage --win-sign-* (WiX + signtool on PATH).
        val pfx = project.findProperty("nativeWinSignPfx") as String?
        val pass = project.findProperty("nativeWinSignPass") as String?
        if (pfx != null && pass != null) {
            extra.addAll(
                listOf(
                    "--win-sign", "--win-sign-pfx", pfx, "--win-sign-storepass", pass,
                    "--win-sign-desc", (project.findProperty("nativeWinSignDesc") as String?) ?: "JRomManager",
                    "--win-sign-timestamping-authority", "http://timestamp.digicert.com"
                )
            )
        }
    } else if (currentOs.isMacOsX) {
        // Hook: --mac-sign + notarization via stored notarytool profile.
        val identity = project.findProperty("nativeMacSignIdentity") as String?
        val profile = project.findProperty("nativeMacNotarizeProfile") as String?
        if (identity != null) {
            extra.addAll(listOf("--mac-sign", "--mac-signing-key-user-name", identity))
            if (profile != null) extra.addAll(listOf("--mac-notarize", "--mac-notary-profile", profile))
        }
    }
    return extra
}

// ---------------------------------------------------------------------------
// Step 0: stub jar — jpackage step 1 needs a --main-jar; it is discarded after.
// ---------------------------------------------------------------------------
tasks.register<Jar>("nativeStubJar") {
    group = "native-installer"
    description = "Minimal stub jar for jpackage app-image scaffolding (discarded afterwards)."
    archiveFileName.set("stub.jar")
    destinationDirectory.set(nativeStubDir)
    // jpackage requires a Main-Class (or --main-class); the stub is discarded in step 2.
    manifest { attributes(mapOf("Main-Class" to "jrm.cli.JRomManagerCLI")) }
    from(rootDir) { include("dist/ver.properties") }
}

// ---------------------------------------------------------------------------
// SERVERS role: jrmserver + jrmfullserver + jrmcli + WebClient/war
// ---------------------------------------------------------------------------
// Step 1: stock app-image with three --add-launcher entries (java launchers,
// replaced by native binaries in step 2). The main launcher is jrmfullserver.
// NOTE: jpackage treats each --arguments occurrence as ONE launcher argument,
// so multi-token defaults are repeated per token ($APPDIR stays literal for
// runtime expansion by the installed launcher).
val serversArgs = listOf("--ws", "-c", "\$APPDIR/client", "-w", "\$APPDIR/work", "-d")
tasks.register("writeNativeLauncherProps") {
    group = "native-installer"
    description = "Writes jpackage --add-launcher property files into the stub input dir."
    dependsOn("nativeStubJar")
    doLast {
        val stub = nativeStubDir.get().asFile
        java.io.File(stub, "jrmserver-launcher.properties").writeText("win-console=true\n")
        java.io.File(stub, "jrmcli-launcher.properties").writeText("win-console=true\n")
    }
}
tasks.register<Exec>("nativeServersAppImage") {
    group = "native-installer"
    description = "Step 1: stock jpackage app-image scaffolding for the servers role."
    dependsOn("writeNativeLauncherProps")
    val out = layout.buildDirectory.dir("native-app/servers-image-parent")
    // jpackage refuses to overwrite an existing dest; Exec tasks are not incremental.
    doFirst { project.delete(out.get().asFile); out.get().asFile.mkdirs() }
    // --add-launcher property files resolve against the working dir, not --input.
    workingDir(nativeStubDir.get().asFile)
    commandLine(
        jpackageBin,
        "--type", "app-image",
        "--input", ".",
        "--dest", out.get().asFile.absolutePath,
        "--name", "JRomManager-Servers",
        "--main-jar", "stub.jar",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--add-launcher", "JRomManagerServer=jrmserver-launcher.properties",
        "--add-launcher", "JRomManagerCLI=jrmcli-launcher.properties"
    )
    serversArgs.forEach { a -> args("--arguments", a) }
}

// Step 2: swap java launchers for native binaries, drop adjacent resources.
tasks.register("stageNativeServersBinaries") {
    group = "native-installer"
    description = "Step 2: replaces app-image java launchers with GraalVM native binaries."
    dependsOn("nativeServersAppImage", jrmServerBinary, jrmFullServerBinary, jrmCliBinary, project(":WebClient").tasks.named("jar"))
    // locate the produced app-image (jpackage names it after --name).
    // Launcher layout differs per OS: <image>/bin/<launcher> on linux,
    // <image>\<launcher>.exe on windows, <image>/Contents/MacOS/<launcher> on macOS.
    val imageDir = layout.buildDirectory.dir("native-app/servers-image-parent/JRomManager-Servers")
    doLast {
        val img = imageDir.get().asFile
        val binCandidates = listOf(
            java.io.File(img, "bin"),
            img,
            java.io.File(img, "Contents/MacOS")
        )
        val targetDir = binCandidates.firstOrNull { it.isDirectory }
            ?: throw GradleException("servers app-image has no launcher dir under $img")
        // -PnativeStubBinaries=true: assembly dry-run without nativeCompile.
        // Copies the java launchers onto themselves (no-op swap) to validate
        // steps 2-3 wiring. Never used for real installers.
        val stubBinaries = project.hasProperty("nativeStubBinaries")
        if (stubBinaries) {
            logger.warn("nativeStubBinaries: installer will contain JAVA launchers, not native binaries")
        } else {
            val fullOut: java.io.File = jrmFullServerOut().get().asFile
            val srvOut: java.io.File = jrmServerOut().get().asFile
            val cliOut: java.io.File = jrmCliOut().get().asFile
            logger.lifecycle("native stage sources: $fullOut, $srvOut, $cliOut")
            project.copy {
                from(fullOut) { include("jrmfullserver*") }
                from(srvOut) { include("jrmserver*") }
                from(cliOut) { include("jrmcli*") }
                into(targetDir)
            }
        }
        // rename staged natives onto the jpackage launcher names.
        // Order matters: 'jrmserver*' prefix also matches 'jrmservers...' — the
        // fullserver rename runs first, then exact basename matching avoids collisions.
        if (!stubBinaries) {
            renameNativeExact(targetDir, "jrmfullserver", "JRomManager-Servers")
            renameNativeExact(targetDir, "jrmserver", "JRomManagerServer")
            renameNativeExact(targetDir, "jrmcli", "JRomManagerCLI")
        }
        // client content + helper scripts next to the image root (referenced via $APPDIR).
        // Two separate copies with absolute String paths: nested `into` inside
        // `from` mis-resolves the destination on Windows (observed: dest 'D:').
        val clientDest: String = java.io.File(img, "client").absolutePath
        project.copy {
            from("${rootDir}/WebClient/war")
            into(clientDest)
        }
        project.copy {
            from("${rootDir}/dist") {
                include("JRomManagerServer.*")
                include("JRomManagerFullServer.*")
                include("JRomManagerCLI.*")
            }
            into(img.absolutePath)
        }
    }
}

// Exact basename match (minus .exe): 'jrmserver' must not match a file
// starting with 'jrmserver' that belongs to another binary (e.g. a
// 'jrmserver-debug' companion or the not-yet-renamed 'jrmfullserver').
fun renameNativeExact(dir: java.io.File, fromBase: String, toName: String) {
    val found = dir.listFiles()?.firstOrNull {
        it.name == fromBase || it.name == "$fromBase.exe"
    } ?: throw GradleException("native binary '$fromBase(.exe)' not staged in $dir")
    val isExe = found.name.endsWith(".exe")
    val dest = java.io.File(dir, toName + if (isExe) ".exe" else "")
    removeReadOnly(dest)
    if (!found.renameTo(dest)) throw GradleException("cannot rename $found to $dest")
    dest.setExecutable(true)
}

fun renameNative(dir: java.io.File, fromPrefix: String, toName: String) {
    val found = dir.listFiles()?.firstOrNull { it.name.startsWith(fromPrefix) }
        ?: throw GradleException("native binary '$fromPrefix*' not staged in $dir")
    val isExe = found.name.endsWith(".exe")
    val dest = java.io.File(dir, toName + if (isExe) ".exe" else "")
    removeReadOnly(dest)
    if (!found.renameTo(dest)) throw GradleException("cannot rename $found to $dest")
    dest.setExecutable(true)
}

// jpackage marks generated launchers read-only; File.delete() fails on them (Windows).
fun removeReadOnly(f: java.io.File) {
    if (f.exists()) {
        f.setWritable(true)
        Files.delete(f.toPath())
    }
}

// Step 3: platform installer(s) from the native app-image.
// 'app-image' in nativeInstallerTypes means repackaging validation only
// (--app-image cannot combine with --type app-image): the staged image is
// copied to the installer dir. Used for local wiring tests without WiX/dpkg.
val serversAppImageOnly = nativeInstallerTypes == listOf("app-image")
tasks.register<Exec>("nativeServersInstaller") {
    group = "native-installer"
    description = "Step 3: servers-role native installer(s) $nativeInstallerTypes for $nativeClassifier."
    dependsOn("stageNativeServersBinaries")
    onlyIf { !serversAppImageOnly }
    val imageDir = layout.buildDirectory.dir("native-app/servers-image-parent/JRomManager-Servers")
    val outDir = layout.buildDirectory.dir("installer-native/servers")
    doFirst { project.delete(outDir.get().asFile); outDir.get().asFile.mkdirs() }
    commandLine(
        jpackageBin,
        "--name", "JRomManager-Servers",
        "--app-version", version.toString(),
        "--vendor", "optyfr",
        "--description", "JRomManager servers (native): FullServer, Server, CLI",
        "--copyright", "GPL-2.0",
        "--app-image", imageDir.get().asFile.absolutePath,
        "--dest", outDir.get().asFile.absolutePath
    )
    nativeInstallerTypes.forEach { t ->
        if (t != "app-image") args("--type", t)
    }
    if (currentOs.isWindows) {
        args("--icon", file("dist/JRomManager.ico").absolutePath,
            "--win-menu", "--win-shortcut", "--win-dir-chooser")
    } else if (currentOs.isLinux) {
        args("--icon", file("dist/JRomManager.png").absolutePath,
            "--linux-package-name", "jrommanager-native-servers",
            "--linux-app-category", "utils",
            "--linux-menu-group", "Utility",
            "--linux-shortcut")
    } else if (currentOs.isMacOsX) {
        args("--icon", file("dist/JRomManager.png").absolutePath,
            "--mac-package-name", "JRomManager-Servers")
    }
    args(signingArgs())
}

// ---------------------------------------------------------------------------
// GUI role: jrmfx (experimental native JavaFX)
// ---------------------------------------------------------------------------
tasks.register<Exec>("nativeGuiAppImage") {
    group = "native-installer"
    description = "Step 1: stock jpackage app-image scaffolding for the gui role."
    dependsOn("nativeStubJar")
    val out = layout.buildDirectory.dir("native-app/gui-image-parent")
    doFirst { project.delete(out.get().asFile); out.get().asFile.mkdirs() }
    workingDir(nativeStubDir.get().asFile)
    commandLine(
        jpackageBin,
        "--type", "app-image",
        "--input", ".",
        "--dest", out.get().asFile.absolutePath,
        "--name", "JRomManager",
        "--main-jar", "stub.jar",
        "--java-options", "-Dfile.encoding=UTF-8 -Dprism.maxvram=1G"
    )
}

tasks.register("stageNativeGuiBinaries") {
    group = "native-installer"
    description = "Step 2: replaces app-image java launcher with the GraalVM jrmfx binary."
    dependsOn("nativeGuiAppImage", jrmFxBinary)
    val imageDir = layout.buildDirectory.dir("native-app/gui-image-parent/JRomManager")
    doLast {
        val img = imageDir.get().asFile
        val binCandidates = listOf(
            java.io.File(img, "bin"),
            img,
            java.io.File(img, "Contents/MacOS")
        )
        val targetDir = binCandidates.firstOrNull { it.isDirectory }
            ?: throw GradleException("gui app-image has no launcher dir under $img")
        val fxOut: java.io.File = jrmFxOut().get().asFile
        project.copy {
            from(fxOut) { include("jrmfx*") }
            into(targetDir)
        }
        renameNativeExact(targetDir, "jrmfx", "JRomManager")
    }
}

val guiAppImageOnly = nativeInstallerTypes == listOf("app-image")
tasks.register<Exec>("nativeGuiInstaller") {
    group = "native-installer"
    description = "Step 3: gui-role native installer(s) $nativeInstallerTypes for $nativeClassifier."
    dependsOn("stageNativeGuiBinaries")
    onlyIf { !guiAppImageOnly }
    val imageDir = layout.buildDirectory.dir("native-app/gui-image-parent/JRomManager")
    val outDir = layout.buildDirectory.dir("installer-native/gui")
    doFirst { project.delete(outDir.get().asFile); outDir.get().asFile.mkdirs() }
    commandLine(
        jpackageBin,
        "--name", "JRomManager",
        "--app-version", version.toString(),
        "--vendor", "optyfr",
        "--description", "JRomManager desktop (native, JavaFX)",
        "--copyright", "GPL-2.0",
        "--app-image", imageDir.get().asFile.absolutePath,
        "--dest", outDir.get().asFile.absolutePath
    )
    nativeInstallerTypes.forEach { t ->
        if (t != "app-image") args("--type", t)
    }
    if (currentOs.isWindows) {
        args("--icon", file("dist/JRomManager.ico").absolutePath,
            "--win-menu", "--win-shortcut", "--win-dir-chooser")
    } else if (currentOs.isLinux) {
        args("--icon", file("dist/JRomManager.png").absolutePath,
            "--linux-package-name", "jrommanager-native",
            "--linux-app-category", "utils",
            "--linux-menu-group", "Utility",
            "--linux-shortcut")
    } else if (currentOs.isMacOsX) {
        args("--icon", file("dist/JRomManager.png").absolutePath,
            "--mac-package-name", "JRomManager")
    }
    args(signingArgs())
}

tasks.register("nativeInstallers") {
    group = "native-installer"
    description = "Builds both native-role installers for the current OS."
    dependsOn("nativeServersInstaller", "nativeGuiInstaller")
}
