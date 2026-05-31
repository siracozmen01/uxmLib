plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
    // Brigadier ships inside Paper at runtime; declare it compileOnly so the wrapper can reference the
    // node-builder types without shading them.
    compileOnly(libs.brigadier)

    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
    testImplementation(libs.brigadier)
    testImplementation(libs.mockbukkit)
}
