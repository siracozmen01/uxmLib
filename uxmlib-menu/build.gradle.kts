plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

// The menu engine: a menu is a file, and this module is what reads one and runs it. It sits on uxmlib-gui,
// which holds the inventory a menu is drawn into, and on uxmlib-bedrock, which holds the native form a
// Bedrock client sees instead. It names no colour and no wording of its own: a consumer registers what its
// menus may say and do through MenuBindings, and the words come from that consumer's own catalogue.
dependencies {
    api(project(":uxmlib-gui"))
    api(project(":uxmlib-bedrock"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
    implementation(libs.configurate.hocon)

    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
    testImplementation(libs.configurate.hocon)
}
