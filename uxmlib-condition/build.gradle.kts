plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common")) // Text seam for rendering failure messages
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
    // The two wallet backends that name a type of another plugin. Both are soft: every typed line sits in a
    // method reached only past a plugin-present guard, so a server without them loads none of their code.
    // The other economies (Vault, VaultUnlocked, PlayerPoints, EcoBits) are descriptions rather than
    // coordinates, so they add nothing to this list and nothing to the build.
    compileOnly(libs.treasury.api)
    compileOnly(libs.placeholderapi)

    // The comparator and the placeholder condition are pure logic and unit-test as plain JUnit; MockBukkit
    // only smoke-tests the Player-bound wiring of a request against a real Paper server.
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
    testImplementation(libs.bundles.testing)
    // Treasury on the test runtime so a fake economy of its own shape can be built and the subscriber path
    // proved. Production still treats it as compileOnly, and the absent-plugin path is proved without it.
    testImplementation(libs.treasury.api)
}
