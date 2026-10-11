plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Pinned in gradle/libs.versions.toml (foojay-resolver); kept literal here
// because the settings plugins block cannot read the catalog.
// (gradle/libs.versions.toml is picked up as the default `libs` catalog
// automatically — no dependencyResolutionManagement block needed.)

rootProject.name = "JRomManager"
include("jrmcore")
include("jrmcli")
include("res-icons")
include("jrmstandalone")
include("jrmfx")
include("jrmserver")
include("WebClient")
