// Precompiled replacement for gradle/aggregated-javadoc.gradle.kts (root-only).
tasks.register<Javadoc>("aggregatedJavadocs") {
    description = "Generate javadocs from all child projects as if it was a single project"
    group = "Documentation"
    destinationDir = file("$buildDir/docs/javadoc")
    title = "${project.name} $version API"
    (options as StandardJavadocDocletOptions).apply {
        author(true)
        links("https://docs.oracle.com/en/java/javase/21/docs/api", "https://openjfx.io/javadoc/21/")
    }

    val javadocTasks = subprojects.mapNotNull {
        it.tasks.findByName("javadoc") as? Javadoc
    }.filter { it.enabled }
    dependsOn(javadocTasks)

    source(javadocTasks.map { it.project.layout.buildDirectory.dir("generated/sources/delombok/java/main").get().asFile })
    classpath = files(javadocTasks.map { it.project.the<SourceSetContainer>()["main"].compileClasspath })
    javadocTasks.flatMap { it.includes }.forEach { include(it) }
    javadocTasks.flatMap { it.excludes }.forEach { exclude(it) }
}

tasks.register<Zip>("docZip") {
    dependsOn("aggregatedJavadocs")
    archiveClassifier.set("javadocs")
    from(tasks.named("javadoc", Javadoc::class.java).map { it.outputs.files })
    into("docs")
    archiveFileName.set(project.name + "-" + project.version + "-apidocs.zip")
    destinationDirectory.set(file("$buildDir/distributions/"))
}

tasks.named("build") {
    dependsOn("docZip")
}
