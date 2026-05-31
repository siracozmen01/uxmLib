plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure) // Paper ships Adventure at runtime
    api(libs.configurate.hocon)

    // The Bukkit-facing seams (Scheduler, Text) need Paper + Adventure on the test runtime,
    // since the production set declares them compileOnly.
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
}
