// Precompiled replacement for gradle/packaging-jars.gradle.kts (root-only).
// JrmProps replaces the rootProject.extra["props"] lookup.
val rootProps = JrmProps.load(rootDir)

// NOTE: extendsFrom(runtimeClasspath) only. The legacy `runtime`
// configuration no longer exists in Gradle 9 and no dependency in this build
// uses the `runtime` declaration configuration anyway.
val runtimeClasspathRef = configurations.getByName("runtimeClasspath")
configurations {
    create("jlinkConfig")
    create("noarchConfig") {
        extendsFrom(runtimeClasspathRef)
    }
}

dependencies {
    "jlinkConfig"(project(":jrmstandalone"))
    "jlinkConfig"(project(":jrmfx"))
    "jlinkConfig"(project(":jrmcli"))
    "jlinkConfig"(project(":jrmserver"))
    "noarchConfig"(project(":jrmcli"))
    "noarchConfig"(project(":jrmserver"))
}

configurations.named("jlinkConfig") {
    val runtimeAttributes = configurations.getByName("runtimeClasspath").attributes
    runtimeAttributes.keySet().forEach { key ->
        @Suppress("UNCHECKED_CAST")
        attributes.attribute(key as Attribute<Any>, runtimeAttributes.getAttribute(key) as Any)
    }
}

tasks.named("jar", Jar::class.java) {
    val mainClassName = "jrm.server.Server"
    manifest {
        val path = configurations.getByName("noarchConfig").filter { it.name.endsWith(".jar") }
            .joinToString(" ") { "lib/" + it.name }
        attributes(
            mapOf(
                "Manifest-Version" to "1.0",
                "Specification-Title" to "JRomManager",
                "Specification-Version" to rootProps.getProperty("Specification-Version"),
                "Implementation-Title" to "jrm",
                "Implementation-Version" to rootProps.getProperty("Implementation-Version"),
                "Main-Class" to mainClassName,
                "Class-Path" to path
            )
        )
        archiveFileName.set(project.name + ".jar")
    }
}

tasks.register<Jar>("cliFatJar") {
    val mainClassName = "jrm.cli.JRomManagerCLI"
    manifest {
        attributes(
            mapOf(
                "Manifest-Version" to "1.0",
                "Specification-Title" to "JRomManager",
                "Specification-Version" to rootProps.getProperty("Specification-Version"),
                "Implementation-Title" to "jrm",
                "Implementation-Version" to rootProps.getProperty("Implementation-Version"),
                "Main-Class" to mainClassName
            )
        )
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveBaseName.set("JRomManagerCLI")
    val cli = project(":jrmcli")
    val cliMain = cli.the<SourceSetContainer>()["main"]
    from(cliMain.output.classesDirs)
    from(cliMain.resources)
    from({ cli.configurations.getByName("runtimeClasspath").map { if (it.isDirectory) it else zipTree(it) } })
}

tasks.named("build") {
    dependsOn("cliFatJar")
}
