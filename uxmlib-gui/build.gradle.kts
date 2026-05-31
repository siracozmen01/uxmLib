plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common"))
    api(project(":uxmlib-item"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
}
