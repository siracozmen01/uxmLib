// No root `plugins {}` block. Spotless / Error Prone / NullAway are pulled onto every
// subproject's buildscript classpath via the buildSrc convention plugin; redeclaring them here
// with `apply false` makes Gradle 9.x fail with "plugin already on classpath with an unknown version".

allprojects {
    // JitPack publishes under com.github.UXPLIMA.uxm-lib, which is how every plugin names this
    // library. Pass -PprojectGroup to publish locally under the same coordinates, so a plugin can
    // build against a version JitPack has not served yet without editing its own build file.
    group = project.findProperty("projectGroup")?.toString() ?: "com.uxplima.uxmlib"
    version = project.findProperty("projectVersion")?.toString() ?: "0.46.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
        maven("https://jitpack.io")                       // Vault API
        maven("https://repo.codemc.io/repository/creatorfromhell/") // VaultUnlocked API
        maven("https://maven.enginehub.org/repo/")        // WorldGuard / WorldEdit
        maven("https://repo.glaremasters.me/repository/towny/") // Towny
        maven("https://repo.opencollab.dev/main/")        // Floodgate and Cumulus
    }
}
