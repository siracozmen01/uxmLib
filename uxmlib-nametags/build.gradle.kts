plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
    alias(libs.plugins.paperweight.userdev)
}

// A from-scratch, MIT-clean per-viewer nametag renderer. The packet objects are built against the
// Mojang-mapped dev bundle and quarantined to a single NMS class; everything else stays pure. The Netty
// plumbing (channel resolve, packet send) is reused from uxmlib-npc, so this module depends on it.
dependencies {
    api(project(":uxmlib-common"))
    api(project(":uxmlib-npc"))

    // The Mojang-mapped dev bundle supplies the Paper API *and* the server internals (net.minecraft,
    // org.bukkit.craftbukkit) the packet construction needs; it replaces the plain paper-api compile
    // dependency for the main source set. Paper's runtime remapper maps the Mojang-mapped classes back
    // to the server mappings at load when the consumer ships the namespace manifest attribute.
    paperweight.paperDevBundle(libs.versions.paper.get())
    compileOnly(libs.bundles.adventure) // Paper ships Adventure at runtime
    // Paper bundles Netty at runtime but does not export it through its POM; the channel/pipeline types are
    // a compileOnly dependency pinned to the version the server ships. Infra dep; the consumer never shades it.
    compileOnly(libs.netty.transport)

    testImplementation(libs.mockbukkit)
    testImplementation(libs.bundles.testing)
    testImplementation(libs.netty.transport)
}

// Keep the Mojang-mapped dev bundle off the test classpath. MockBukkit drives the plugin against the plain
// Paper API, and the full server's static initializers throw if their classes leak onto the unit-test
// runtime. compileOnly alone is what the renderer needs — net.minecraft is provided by the live server.
paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
}
