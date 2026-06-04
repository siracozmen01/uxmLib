plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    // Lettuce backs the byte[] Redis pub/sub — a soft-dependency: compileOnly here, constructed only when a
    // Redis URI is configured, so a consumer that never touches Redis ships nothing extra; one that does adds
    // io.lettuce:lettuce-core. Deliberately NOT depending on uxmlib-storage (which drags HikariCP/Caffeine/
    // sqlite-jdbc) so a pure cross-server-messaging consumer stays lean.
    compileOnly(libs.lettuce.core)

    // Lettuce on the test runtime so the bus links; the round-trip test runs against a reachable Redis
    // (skips cleanly when none is) — see RedisBusIntegrationTest.
    testImplementation(libs.lettuce.core)
    testImplementation(libs.bundles.testing)
}
