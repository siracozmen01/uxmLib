plugins {
    id("maven-publish")
}

// A library module is consumed two ways (the project's "both" distribution choice): pulled as a Maven
// artifact and shaded by the consumer, or run inside the standalone uxmlib-all plugin. This convention
// publishes the binary + sources + javadoc with proper POM metadata so JitPack / a Maven repo can serve it.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("uxmLib — a modern toolkit library for Paper 1.21+ plugins")
                url.set("https://github.com/siracozmen01/uxmLib")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("siracozmen")
                        name.set("Sirac Ozmen")
                    }
                }
                scm {
                    url.set("https://github.com/siracozmen01/uxmLib")
                    connection.set("scm:git:https://github.com/siracozmen01/uxmLib.git")
                    developerConnection.set("scm:git:git@github.com:siracozmen01/uxmLib.git")
                }
            }
        }
    }
}
