import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

// Precompiled replacement for gradle/packaging-distributions.gradle.kts
// (root-only). Typed accessors (distributions {}) resolve as a real plugin;
// JrmProps replaces rootProject.extra["props"].
plugins {
    distribution
}
val rootProps = JrmProps.load(rootDir)
val daemonVersion = rootProps.getProperty("Commons-Daemon-Version")
val os = DefaultNativePlatform.getCurrentOperatingSystem()
val arch = DefaultNativePlatform.getCurrentArchitecture()

distributions {
    named("main") {
        distributionBaseName.set("JRomManager")
        distributionClassifier.set("nogui-noarch")
        contents {
            from(configurations.getByName("noarchConfig")) {
                into("lib")
                exclude("*.zip")
                include("*.jar")
            }
            from(tasks.named("jar"))
            if (os.isLinux) {
                from("$buildDir/jsvc/commons-daemon-$daemonVersion-native-src/unix") {
                    include("jsvc")
                    filePermissions { unix("0755") }
                }
            }
            from("$buildDir/commons-daemon-$daemonVersion-bin-windows") {
                filePermissions { unix("0755") }
            }
            from("dist/logs") {
                include(".do_not_delete")
                into("logs")
                dirPermissions { unix("0777") }
            }
            from("dist") {
                include("*.bat")
                include("*.sh")
                filePermissions { unix("0755") }
            }
        }
    }
    create("win") {
        distributionBaseName.set("JRomManager")
        distributionClassifier.set(
            when {
                arch.isI386 -> "win-x32"
                arch.isAmd64 -> "win-x64"
                arch.isArm32 -> "win-arm32"
                arch.isArm64 -> "win-arm64"
                else -> "win-" + arch.name
            }
        )
        contents {
            from(configurations.getByName("jlinkConfig")) {
                into("lib")
                exclude("*.zip")
                include("*.jar")
            }
            from(tasks.named("jar"))
            from("$buildDir/commons-daemon-$daemonVersion-bin-windows") {
                filePermissions { unix("0755") }
            }
            from("dist/logs") {
                include(".do_not_delete")
                into("logs")
                dirPermissions { unix("0777") }
            }
            from("dist") {
                include("*.bat")
                filePermissions { unix("0755") }
            }
            from("dist/full") {
                include("*.bat")
                filePermissions { unix("0755") }
            }
        }
    }
    create("linux") {
        distributionBaseName.set("JRomManager")
        distributionClassifier.set(
            when {
                arch.isI386 -> "linux-x32"
                arch.isAmd64 -> "linux-x64"
                arch.isArm32 -> "linux-arm32"
                arch.isArm64 -> "linux-arm64"
                else -> "linux-" + arch.name
            }
        )
        contents {
            from(configurations.getByName("jlinkConfig")) {
                into("lib")
                exclude("*.zip")
                include("*.jar")
            }
            from(tasks.named("jar"))
            from("$buildDir/jsvc/commons-daemon-$daemonVersion-native-src/unix") {
                include("jsvc")
                filePermissions { unix("0755") }
            }
            from("dist/logs") {
                include(".do_not_delete")
                into("logs")
                dirPermissions { unix("0777") }
            }
            from("dist") {
                include("*.sh")
                filePermissions { unix("0755") }
            }
            from("dist/full") {
                include("*.sh")
                filePermissions { unix("0755") }
            }
        }
    }
}

tasks.named("distTar", Tar::class.java) {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

tasks.named("distZip") { dependsOn("jar", ":WebClient:jar", "extractDaemonWin") }
tasks.named("distTar") { dependsOn("jar", ":WebClient:jar", "extractDaemonWin") }
tasks.named("installDist") { dependsOn("jar", ":WebClient:jar", "extractDaemonWin") }

if (os.isLinux) {
    tasks.named("winDistZip") { enabled = false }
    tasks.named("winDistTar") { enabled = false }
    tasks.named("linuxDistZip") { enabled = false }
    tasks.named("linuxDistTar", Tar::class.java) {
        dependsOn("jar", ":WebClient:jar", "compileDaemonLinux")
        compression = Compression.GZIP
        archiveExtension.set("tar.gz")
    }
    tasks.named("distZip") { dependsOn("compileDaemonLinux") }
    tasks.named("distTar") { dependsOn("compileDaemonLinux") }
    tasks.named("installDist") { dependsOn("compileDaemonLinux") }
    tasks.named("build") { dependsOn("linuxDistTar") }
} else if (os.isWindows) {
    tasks.named("linuxDistTar") { enabled = false }
    tasks.named("linuxDistZip") { enabled = false }
    tasks.named("winDistTar") { enabled = false }
    tasks.named("winDistZip") { dependsOn("jar", ":WebClient:jar", "extractDaemonWin") }
    tasks.named("build") { dependsOn("winDistZip") }
} else {
    tasks.named("linuxDistTar") { enabled = false }
    tasks.named("linuxDistZip") { enabled = false }
    tasks.named("winDistZip") { enabled = false }
    tasks.named("winDistTar") { enabled = false }
}

tasks.named("build") { dependsOn("installDist") }
