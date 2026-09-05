plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

// EXPERIMENTAL: a from-scratch, MIT-clean Netty pipeline (no PacketEvents, which is GPL). Channel resolve,
// inject and eject, self-healing reorder, and a listener seam. It builds no packet and knows no entity: the
// features that do sit above it in uxmlib-packet and uxmlib-nametags.
//
// It compiles against plain paper-api on purpose. Everything above it applies paperweight and publishes
// Mojang mapped, which asks a consumer for the namespace manifest attribute; this module asks for nothing.
// That is the boundary the module exists to hold. See the package-info for the status note.
dependencies {
    api(project(":uxmlib-common")) // Scheduler abstraction (Folia-ready inject/reorder choreography)
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
    // Paper bundles Netty at runtime but paper-api's POM does not export it, so the channel/pipeline types are
    // a compileOnly dependency pinned to the version the server ships. Infra dep; the consumer never shades it.
    compileOnly(libs.netty.transport)

    // MockBukkit cannot give us a real Netty channel, so the reflective resolver and the inject/eject only
    // get a graceful-failure smoke test; the listener registry and the watchdog reorder decision are pure
    // logic and unit-test as plain JUnit.
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
    testImplementation(libs.bundles.testing)
    // The smoke tests reference io.netty.channel.Channel directly, so Netty is on the test classpath too.
    testImplementation(libs.netty.transport)
}
