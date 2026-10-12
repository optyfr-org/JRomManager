import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.Dependency
import com.netflix.gradle.plugins.packaging.ProjectPackagingExtension
import com.netflix.gradle.plugins.rpm.Rpm
import org.gradle.kotlin.dsl.closureOf
import org.redline_rpm.header.Flags
import org.redline_rpm.header.Os
import org.redline_rpm.header.RpmType

// Precompiled replacement for gradle/packaging-ospackage.gradle (Groovy).
// Nebula's API is fully statically addressable from Kotlin:
//  - plain JavaBean setters for every scalar (get/set pairs in javap output),
//  - redline Os/RpmType/Flags/Architecture are plain Java enums/constants,
//  - setArch(Object) stringifies, so the "noarch"/"all" literals work,
//  - from(Object, Closure) overloads take groovy.lang.Closure, supplied via
//    org.gradle.kotlin.dsl.closureOf (kotlin-dsl is on the classpath).
// Bare Groovy names map as: BINARY -> RpmType.BINARY, LINUX -> Os.LINUX,
// NOARCH/all -> "noarch"/"all" (setArch stringifies), EQUAL -> Flags.EQUAL
// (see AliasHelper registrations in RpmPlugin/DebPlugin.applyAliases).
plugins {
    id("com.netflix.nebula.ospackage")
}

extensions.configure<ProjectPackagingExtension>("ospackage") {
    setPackageName("jrommanager")
    setPackageDescription("A Mame and Retrogaming Rom Manager, Java alternative to ClrMamePro")
    setRelease("1")
    setType(RpmType.BINARY)
    setLicense("GPL-2")
    setVendor("optyfr")
    setPackager("optyfr")
    setMaintainer("optyfr")
    setUser("root")
    setPermissionGroup("root")
    setUrl("https://github.com/optyfr/JRomManager")
    into("/opt/jrommanager")
    from(configurations.getByName("noarchConfig"), closureOf<org.gradle.api.file.CopySpec> {
        into("lib")
        exclude("*.zip")
        include("*.jar")
    })
    from(tasks.named("jar"))
    from("dist", closureOf<org.gradle.api.file.CopySpec> {
        include("*.bat")
        include("*.sh")
        include("*.shconf")
        filePermissions { unix("0755") }
    })
    from("dist", closureOf<org.gradle.api.file.CopySpec> {
        include("${name}.desktop")
        into("/usr/share/applications")
    })
    from("dist", closureOf<org.gradle.api.file.CopySpec> {
        include("${name}.png")
        into("/usr/share/pixmaps")
    })
    link("/usr/bin/jrommanager", "/opt/jrommanager/JRomManager-multi.sh")
}

val ospackageExt = extensions.getByType<ProjectPackagingExtension>()

tasks.named("buildDeb", Deb::class.java) {
    // Explicit per-task configuration (mirrors the inherited ospackage {}
    // defaults, so output does not depend on convention-mapping order).
    setPackageName(ospackageExt.packageName)
    setPackageDescription(ospackageExt.packageDescription)
    setRelease(ospackageExt.release)
    setType(RpmType.BINARY)
    setLicense(ospackageExt.license)
    setVendor(ospackageExt.vendor)
    setPackager(ospackageExt.packager)
    setMaintainer(ospackageExt.maintainer)
    setUser(ospackageExt.user)
    setPermissionGroup(ospackageExt.permissionGroup)
    setUrl(ospackageExt.url)
    setArch("all")
    requires("java25-runtime")
    // File content AND links come from the parent ospackage extension via
    // convention mapping (parentExten), exactly as the Groovy build left it.
    // Do NOT setLinks(ext.links) here: getAllLinks() merges parent + task
    // lists, so copying the parent list onto the task duplicates every link.
    dependsOn("distZip")
}

tasks.named("buildRpm", Rpm::class.java) {
    // Explicit per-task configuration (mirrors the inherited ospackage {}
    // defaults, so output does not depend on convention-mapping order).
    setPackageName(ospackageExt.packageName)
    setPackageDescription(ospackageExt.packageDescription)
    setRelease(ospackageExt.release)
    setType(RpmType.BINARY)
    setLicense(ospackageExt.license)
    setVendor(ospackageExt.vendor)
    setPackager(ospackageExt.packager)
    setMaintainer(ospackageExt.maintainer)
    setUser(ospackageExt.user)
    setPermissionGroup(ospackageExt.permissionGroup)
    setUrl(ospackageExt.url)
    setArch("noarch")
    setOs(Os.LINUX)
    setAddParentDirs(false)
    setCreateDirectoryEntry(false)
    val javaDep = Dependency("java", "1:25", Flags.EQUAL).or("java-25")
    setDependencies(mutableListOf(javaDep))
    // File content AND links come from the parent ospackage extension via
    // convention mapping (parentExten), exactly as the Groovy build left it.
    // Do NOT setLinks(ext.links) here: getAllLinks() merges parent + task
    // lists, so copying the parent list onto the task duplicates every link.
    dependsOn("distZip")
}

tasks.named("build") {
    dependsOn("buildRpm")
    dependsOn("buildDeb")
}
