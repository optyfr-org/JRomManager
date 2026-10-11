sourceSets {
    named("main") {
        java {
            setSrcDirs(listOf("src/main/java"))
        }
        resources {
            setSrcDirs(listOf("src/main/java"))
        }
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to "JRomManager Resources",
                "Implementation-Title" to "res-icons"
            )
        )
    }
}

tasks.named<Javadoc>("javadoc") {
    enabled = false
}

// The sonar plugin classes are not on this script's compile classpath;
// set skipProject without a typed reference.
extensions.findByName("sonar")?.let { sonarExt ->
    sonarExt.javaClass.getMethod("setSkipProject", Boolean::class.javaPrimitiveType).invoke(sonarExt, true)
}
