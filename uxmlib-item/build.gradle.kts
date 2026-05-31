plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)

    // MockBukkit drives the real Paper API in tests; the production set declares Paper/Adventure
    // compileOnly, so the test set needs them on its runtime classpath.
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
}
