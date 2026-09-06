plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
    alias(libs.plugins.paperweight.userdev)
}

// The shared, MIT-clean packet foundation: the generic Mojang-mapped machinery (Adventure -> vanilla component
// conversion, bundle building, the stream-codec buffer trick, the guarded accessor reflection, the entity-id
// allocator) that more than one packet feature needs. The nametag renderer was the first consumer; the tablist
// renderer is the second. NMS is quarantined to the small helper classes here, mirroring uxmlib-nametags. The
// Netty plumbing (channel resolve, packet send) comes from uxmlib-pipeline.
dependencies {
    api(project(":uxmlib-pipeline"))

    // The Mojang-mapped dev bundle supplies the Paper API *and* the server internals (net.minecraft,
    // org.bukkit.craftbukkit) the packet construction needs; it replaces the plain paper-api compile
    // dependency for the main source set. Paper's runtime remapper maps the Mojang-mapped classes back
    // to the server mappings at load when the consumer ships the namespace manifest attribute.
    paperweight.devBundle(libs.devbundle.modern)
    compileOnly(libs.bundles.adventure) // Paper ships Adventure at runtime
    // Paper bundles Netty at runtime but does not export it through its POM; the channel/pipeline types are
    // a compileOnly dependency pinned to the version the server ships. Infra dep; the consumer never shades it.
    compileOnly(libs.netty.transport)

    // The dev bundle is kept off the test classpath (see below), so the plain Paper API and Adventure are
    // brought in directly for the MockBukkit-driven tests; Paper API also supplies JOML's Vector3f.
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
    testImplementation(libs.bundles.testing)
    testImplementation(libs.netty.transport)
}

// Keep the Mojang-mapped dev bundle off the test classpath. MockBukkit drives the plugin against the plain
// Paper API, and the full server's static initializers throw if their classes leak onto the unit-test
// runtime. compileOnly alone is what the helpers need: net.minecraft is provided by the live server.
paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
}

// This module and uxmlib-nametags apply paperweight-userdev against the same dev bundle, so both derive
// the same work directory and both want its lock. org.gradle.parallel is on, so in one build the two
// setup tasks reach for that lock at the same moment and the second dies with "lock file is currently
// held by this process": two tasks of one build, not two builds on one machine, which is why waiting
// and running it again always worked and why it came back on every cold cache.
//
// Ordering them costs one setup's wait on a cold cache and nothing after that, because the second task
// finds the work done. The pair is named rather than generalised: a third module applying paperweight
// would have to join the chain here, and a comment that says so is worth more than a clever rule.
tasks.named("paperweightUserdevSetup") {
    mustRunAfter(":uxmlib-nametags:paperweightUserdevSetup")
}
