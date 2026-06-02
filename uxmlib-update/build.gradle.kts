plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common")) // Scheduler + Text seams
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)

    // MockBukkit smoke-tests the join-notify wiring; the version compare + JSON parse are plain JUnit.
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
    testImplementation(libs.bundles.testing)
}

// Generate UxmLibVersion.java from project.version at build time so the update-checker's notion of its own
// version can never drift from the Gradle project version. Module-local (not buildSrc) to avoid global risk.
val generatedVersionDir = layout.buildDirectory.dir("generated/sources/version/java/main")

val generateVersion by tasks.registering {
    val outputDir = generatedVersionDir
    val version = project.version.toString()
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val pkgDir = outputDir.get().dir("com/uxplima/uxmlib/update").asFile
        pkgDir.mkdirs()
        val target = pkgDir.resolve("UxmLibVersion.java")
        target.writeText(
            """
            package com.uxplima.uxmlib.update;

            import org.jspecify.annotations.NullMarked;

            /** Generated from the Gradle project version at build time. Do not edit by hand. */
            @NullMarked
            public final class UxmLibVersion {

                /** The library version this jar was built from. */
                public static final String VERSION = "$version";

                private UxmLibVersion() {}
            }
            """.trimIndent() + "\n",
        )
        // Reproducible builds: a fixed mtime so the generated file never churns the jar timestamp.
        target.setLastModified(0)
    }
}

sourceSets.main {
    java.srcDir(generatedVersionDir)
}

// Every task that consumes the main Java sources must see the generated constant first. Declaring the
// dependency explicitly keeps Gradle's task-ordering validation happy (Spotless and javadoc read it too).
tasks.named("compileJava") {
    dependsOn(generateVersion)
}

tasks.named("sourcesJar") {
    dependsOn(generateVersion)
}

tasks.named("javadoc") {
    dependsOn(generateVersion)
}

tasks.matching { it.name.startsWith("spotlessJava") }.configureEach {
    dependsOn(generateVersion)
}
