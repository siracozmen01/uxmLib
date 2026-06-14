plugins {
    `java-platform`
    `maven-publish`
}

// A BOM so a consumer can align every uxmlib-* artifact to one version with a single platform import.
dependencies {
    constraints {
        api(project(":uxmlib-common"))
        api(project(":uxmlib-item"))
        api(project(":uxmlib-command"))
        api(project(":uxmlib-gui"))
        api(project(":uxmlib-storage"))
        api(project(":uxmlib-redis"))
        api(project(":uxmlib-integration"))
        api(project(":uxmlib-hud"))
        api(project(":uxmlib-update"))
        api(project(":uxmlib-condition"))
        api(project(":uxmlib-npc"))
        api(project(":uxmlib-nametags"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            pom {
                name.set("uxmlib-bom")
                description.set("Bill of materials aligning all uxmlib-* module versions")
                url.set("https://github.com/siracozmen01/uxmLib")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
