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
    compileOnly(libs.worldguard.bukkit)
    compileOnly(libs.towny)

    // Tests exercise the absent-plugin path (MockBukkit) and the pure JSON/spec logic (plain JUnit).
    // LuckPerms is on the test runtime so the hook class loads (and reports absent) without a real plugin.
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
    testImplementation(libs.luckperms.api)
    // Vault on the test runtime lets a fake Economy service exercise the bridge's format/currency
    // delegation and the service-register rebinding; production still treats Vault as compileOnly.
    testImplementation(libs.vault.api)
    // WorldGuard and Towny on the test runtime let the region adapter classes load so the present-guard
    // (no plugin under MockBukkit -> empty) is asserted; production still treats both as compileOnly.
    testImplementation(libs.worldguard.bukkit)
    testImplementation(libs.towny)
}
