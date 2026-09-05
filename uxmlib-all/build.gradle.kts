plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
    alias(libs.plugins.shadow)
}

// The aggregate: every module on the API surface, plus a thin JavaPlugin so uxmlib can also be dropped
// onto a server as a single standalone dependency jar (the "both" distribution choice). It is published
// too, so a consumer who wants the whole surface declares this one artifact and lets Gradle pull the
// modules in as transitive api dependencies, rather than listing thirteen coordinates by hand.
dependencies {
    api(project(":uxmlib-common"))
    api(project(":uxmlib-item"))
    api(project(":uxmlib-command"))
    api(project(":uxmlib-gui"))
    api(project(":uxmlib-bedrock"))
    api(project(":uxmlib-storage"))
    api(project(":uxmlib-redis"))
    api(project(":uxmlib-integration"))
    api(project(":uxmlib-hud"))
    api(project(":uxmlib-update"))
    api(project(":uxmlib-condition"))
    api(project(":uxmlib-pipeline"))
    api(project(":uxmlib-packet"))
    api(project(":uxmlib-nametags"))
    compileOnly(libs.paper.api)

    // Architecture guards analyse every module's bytecode (all are api deps, so they're on the test
    // classpath); paper-api is needed so ArchUnit can resolve the Bukkit types the rules reference.
    testImplementation(libs.archunit.junit)
    testImplementation(libs.paper.api)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

// This module wears two hats, and they must not share a file name. The plain jar is the API aggregate a
// consumer compiles against: it carries no third-party code, so the module signatures it exposes are the
// real ones. The shaded jar is the standalone server plugin, and it relocates its bundled libraries, which
// rewrites those same signatures. Publishing both without a classifier made the shaded one overwrite the
// plain one on disk, so consumers compiled against relocated types (a Map<Locale, ConfigurationNode> they
// had no way to produce) while the POM also pulled every module in cleanly beside it.
tasks.shadowJar {
    archiveClassifier.set("standalone")
    // Relocate the bundled infra libs under a per-library namespace so two plugins that both ship the
    // standalone jar (or shade it) never clash on the classpath. Our own com.uxplima.uxmlib stays put.
    relocate("org.spongepowered.configurate", "com.uxplima.uxmlib.libs.configurate")
    relocate("com.zaxxer.hikari", "com.uxplima.uxmlib.libs.hikari")
    relocate("com.github.benmanes.caffeine", "com.uxplima.uxmlib.libs.caffeine")
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// Giving it a classifier is also what publishes it: Shadow adds its variant to the java component only when
// the classifier is non-empty (otherwise it would collide with the plain jar), so the plugin now ships from
// Maven beside the aggregate as com.github.UXPLIMA.uxm-lib:uxmlib-all:VERSION:standalone.
