pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "uxmlib"

include(
    ":uxmlib-bom",
    ":uxmlib-common",
    ":uxmlib-item",
    ":uxmlib-command",
    ":uxmlib-gui",
    ":uxmlib-bedrock",
    ":uxmlib-storage",
    ":uxmlib-redis",
    ":uxmlib-integration",
    ":uxmlib-hud",
    ":uxmlib-update",
    ":uxmlib-condition",
    ":uxmlib-npc",
    ":uxmlib-packet",
    ":uxmlib-nametags",
    ":uxmlib-all",
)
