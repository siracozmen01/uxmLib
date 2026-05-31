plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
    // Soft-depend integrations: reached only past a plugin-present guard, so a server without them is fine.
    compileOnly(libs.luckperms.api)
    compileOnly(libs.vault.api)
    compileOnly(libs.placeholderapi)

    // Tests exercise the absent-plugin path (MockBukkit) and the pure JSON/spec logic (plain JUnit).
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
}
