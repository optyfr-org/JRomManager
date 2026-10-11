// Precompiled replacement for gradle/delombok-javadoc.gradle.kts (verbatim:
// only core Gradle API, no catalog needed since lombok pins come from the
// freefair plugin + TOML version via the subproject builds).
tasks.named("javadoc", Javadoc::class.java) {
    dependsOn("delombok")
    source(fileTree("$buildDir/generated/sources/delombok/java/main") {
        include("**/*.java")
    })
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        memberLevel = JavadocMemberLevel.PACKAGE
        outputLevel = JavadocOutputLevel.QUIET
        addBooleanOption("Xdoclint", true)
        addStringOption("Xmaxwarns", "0")
        addBooleanOption("-syntax-highlight", true)
    }
}
