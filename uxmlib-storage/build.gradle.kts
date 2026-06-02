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
    // H2 (pure-Java embedded) is an opt-in backend like the network drivers: compileOnly here, consumer adds it.
    compileOnly(libs.h2)

    // Lettuce backs the optional RedisDataSynchronizer (cross-server pub/sub). It is a soft-dependency:
    // compileOnly here, constructed only when a Redis URI is configured, so a consumer that never touches
    // Redis ships nothing extra. On the test runtime so the adapter compiles and links against the real API.
    compileOnly(libs.lettuce.core)

    // Storage is pure infra (no Paper). Tests run a real in-memory SQLite, so they are plain JUnit. The
    // SqlType Component codec needs Adventure + MiniMessage on the test runtime since they are compileOnly.
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.bundles.adventure)
    // The H2 dialect round-trip test runs against a real in-memory H2, so the driver is on the test runtime.
    testImplementation(libs.h2)
    // Lettuce on the test runtime so RedisDataSynchronizer links and a smoke/integration test can construct it.
    testImplementation(libs.lettuce.core)
}
