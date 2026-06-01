plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common"))
    api(project(":uxmlib-item"))
    api(project(":uxmlib-integration"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)

    // MockBukkit drives the real Paper API in tests; production declares Paper/Adventure compileOnly.
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
}
