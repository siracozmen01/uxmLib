pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "uxmlib"

include(
    ":uxmlib-bom",
    ":uxmlib-common",
    ":uxmlib-item",
    ":uxmlib-command",
    ":uxmlib-gui",
    ":uxmlib-storage",
    ":uxmlib-redis",
    ":uxmlib-integration",
    ":uxmlib-hud",
    ":uxmlib-update",
    ":uxmlib-condition",
    ":uxmlib-npc",
    ":uxmlib-nametags",
    ":uxmlib-all",
)
