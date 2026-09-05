plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common"))
    api(project(":uxmlib-item"))
    // api rather than implementation: TextInputInstaller.install and both public TextInput constructors
    // name BedrockDetector and BedrockScreen, so a consumer wiring Bedrock text input has to name them.
    api(project(":uxmlib-bedrock"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)

    // MockBukkit drives the real Paper API in tests; production declares Paper/Adventure compileOnly.
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
}
