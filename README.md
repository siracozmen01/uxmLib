# uxmLib

A small, modern toolkit for writing Paper plugins on Minecraft **1.21+**. It bundles the things every
plugin ends up rewriting — inventory GUIs, item building, commands, config, storage, a few integrations —
behind a clean API, so you stop copy-pasting the same helpers between projects.

It targets 1.21+ and Java 21 only. That's deliberate: no legacy cross-version reflection layers to drag
around, just the current Paper API used the way it's meant to be used.

## Modules

| Module | What it gives you |
| --- | --- |
| `uxmlib-common` | Version metadata, the scheduler/text/config foundations the rest builds on |
| `uxmlib-item` | A fluent `ItemBuilder` plus NBT / data-component / PDC helpers |
| `uxmlib-gui` | Inventory GUI framework — paginated menus, click routing, anvil text input |
| `uxmlib-command` | A thin layer over Paper's Brigadier commands |
| `uxmlib-storage` | Pooled (HikariCP) + cached (Caffeine) persistence plumbing |
| `uxmlib-integration` | PlaceholderAPI / Vault / LuckPerms hooks, native-display holograms, a Discord webhook client |
| `uxmlib-bom` | A bill of materials to align every module to one version |
| `uxmlib-all` | The aggregate; also the standalone server-side plugin jar |

## Using it

Two ways, pick what suits you:

- **Shade it.** Depend on the modules you need and relocate `com.uxplima.uxmlib` into your own jar.
- **Install it.** Drop the standalone `uxmlib` jar on the server and have your plugins depend on it.

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(platform("com.uxplima.uxmlib:uxmlib-bom:0.1.0"))
    implementation("com.uxplima.uxmlib:uxmlib-gui")
    implementation("com.uxplima.uxmlib:uxmlib-item")
}
```

## Building

```bash
./gradlew build           # compile, format check, static analysis, tests
./gradlew :uxmlib-all:shadowJar   # the standalone plugin jar
```

## License

MIT — see [LICENSE](LICENSE). Use it anywhere, including closed-source plugins.
