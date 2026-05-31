plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure) // Paper ships Adventure at runtime
    api(libs.configurate.hocon)
}
