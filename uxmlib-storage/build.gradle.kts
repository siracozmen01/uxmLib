plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common"))
    api(libs.hikari)
    api(libs.caffeine)

    // The Component column codec in SqlType serializes through Adventure + MiniMessage. Paper bundles both
    // at runtime (as it does for the rest of the toolkit), so they stay compileOnly here, never shaded.
    compileOnly(libs.bundles.adventure)

    // The SQLite driver is the default backend, so it ships as an api dependency. MariaDB/MySQL and
    // PostgreSQL are network options a consumer opts into; declared compileOnly here and added by the
    // consumer when they select that backend.
    api(libs.sqlite.jdbc)
    compileOnly(libs.mariadb.jdbc)
    compileOnly(libs.postgresql)

    // Storage is pure infra (no Paper). Tests run a real in-memory SQLite, so they are plain JUnit. The
    // SqlType Component codec needs Adventure + MiniMessage on the test runtime since they are compileOnly.
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.bundles.adventure)
}
