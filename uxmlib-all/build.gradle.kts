plugins {
    id("uxmlib.java-conventions")
    alias(libs.plugins.shadow)
}

// The aggregate: every module on the API surface, plus a thin JavaPlugin so uxmlib can also be dropped
// onto a server as a single standalone dependency jar (the "both" distribution choice). Consumers who
// prefer to shade pull the individual uxmlib-* artifacts instead.
dependencies {
    api(project(":uxmlib-common"))
    api(project(":uxmlib-item"))
    api(project(":uxmlib-command"))
    api(project(":uxmlib-gui"))
    api(project(":uxmlib-storage"))
    api(project(":uxmlib-integration"))
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

tasks.shadowJar {
    archiveClassifier.set("")
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
