# uxmLib

[![](https://jitpack.io/v/siracozmen01/uxmLib.svg)](https://jitpack.io/#siracozmen01/uxmLib)

A small, modern toolkit for writing Paper plugins on Minecraft **1.21+**. It bundles the things every
plugin ends up rewriting — inventory GUIs, item building, commands, config, storage, a few integrations —
behind a clean API, so you stop copy-pasting the same helpers between projects.

It targets **Paper 1.21+ and Java 21 only**. That's deliberate: no legacy cross-version reflection layers
to drag around, just the current Paper API used the way it's meant to be used. Everything is built with
`-Werror`, NullAway null-safety, Spotless formatting, and ArchUnit architecture tests, and unit-tested.

## Modules

Each module is published separately; pull only what you use.

| Module | What it gives you |
| --- | --- |
| `uxmlib-common` | Folia-ready `Scheduler`, MiniMessage `Text` helpers, typed `HoconConfig` |
| `uxmlib-item` | A fluent `ItemBuilder`, sealed `SkullData`, registry lookups, item serialization |
| `uxmlib-gui` | Inventory-menu framework — single/paginated/typed menus, click routing, anvil input |
| `uxmlib-command` | A thin Brigadier facade plus an annotation-driven command DSL |
| `uxmlib-storage` | Pooled (HikariCP) + cached (Caffeine) JDBC, a query builder, and migrations |
| `uxmlib-integration` | PlaceholderAPI / Vault / LuckPerms hooks, native-Display holograms, a Discord webhook |
| `uxmlib-bom` | A bill of materials to align every module to one version |
| `uxmlib-all` | The aggregate; also the standalone server-side plugin jar |

## Install

uxmLib is published through [JitPack](https://jitpack.io/#siracozmen01/uxmLib). Add the repository, then
the modules you need — JitPack serves each module of the build under the `com.github.siracozmen01.uxmLib`
group, with the git tag as the version.

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.siracozmen01.uxmLib:uxmlib-gui:0.1.0")
    implementation("com.github.siracozmen01.uxmLib:uxmlib-item:0.1.0")
    implementation("com.github.siracozmen01.uxmLib:uxmlib-command:0.1.0")
    // ...and uxmlib-common / uxmlib-storage / uxmlib-integration as needed
}
```

Relocate `com.uxplima.uxmlib` into your own jar when you shade. Alternatively, drop the standalone
`uxmlib-all` jar on the server and have your plugins depend on it.

## Quick tour

### Items

```java
ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
        .name(Text.mini("<gradient:#ff5555:#ffaa00>Flameblade</gradient>"))
        .lore(Text.mini("<gray>A legendary weapon"))
        .enchant(Items.enchantment("sharpness"), 5)   // 1.21 lost the static constants; look up by key
        .flags(ItemFlag.HIDE_ENCHANTS)
        .unbreakable(true)
        .build();

ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
        .skull(SkullData.ofName("Notch"))
        .build();

String saved = ItemSerialization.toBase64(sword);          // survives every component
ItemStack back = ItemSerialization.fromBase64(saved);
```

### GUIs

```java
Guis.install(plugin);   // once, in onEnable

SimpleGui menu = Guis.gui().title(Text.mini("<dark_aqua>Menu")).rows(3).build();
menu.set(13, GuiItem.button(icon, event -> event.getWhoClicked().sendMessage("clicked")));
menu.onClose(event -> { /* ... */ });
menu.open(player);

// Paginated, hopper/dispenser shapes, and anvil text input:
PaginatedGui shop = Guis.paginated().title(Text.mini("Shop")).rows(6).build();
products.forEach(p -> shop.addPageItem(GuiItem.button(p.icon(), e -> buy(p))));
shop.open(player);

new AnvilInput(plugin).install();
new AnvilInput(plugin).open(player, prompt, result -> {
    if (result instanceof AnvilResult.Submitted s) handle(s.text());
});
```

### Commands

Thin facade, or the annotation DSL — both register through Paper's Brigadier lifecycle:

```java
// Annotation-driven: no hand-built nodes
@Command(name = "money", description = "Manage balances")
class MoneyCommand {
    @Subcommand("pay") @Permission("money.pay")
    void pay(Sender sender, @Arg("target") String target, @Arg(value = "amount", min = 1) int amount) {
        sender.send(Text.mini("<green>Paid " + amount + " to " + target));
    }
}
AnnotatedCommands.register(plugin, new MoneyCommand());

// Or build the tree yourself with the Cmd facade:
CommandRegistrar.register(plugin,
        Cmd.literal("ping").requires(Cmd.permission("x.ping"))
                .executes(ctx -> { Sender.of(ctx.getSource()).send(Text.mini("pong")); return Cmd.OK; }),
        "Replies with pong");
```

### Config

```java
HoconConfig config = HoconConfig.load(dataFolder.resolve("config.conf"), ConfigCodecs.bukkit());

int limit = config.getInt("homes.limit", 3);
Material icon = config.getNode("ui", Settings.class, fallback).icon();   // codecs map Material/Color/Key

ConfigProperty<Integer> live = config.intProperty("homes.limit", 3);
live.onChange(value -> rebuildLimits(value));   // fires on reload when the value changes
config.reload();
```

### Storage

```java
Database db = Database.builder().sqlite(dataFolder.resolve("data.db")).build();   // SQLite default, WAL
Sql sql = new Sql(db);
sql.execute("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, coins INTEGER)");

new MigrationRunner(db).apply(List.of(
        new Migration(1, "init", "CREATE TABLE warps (name TEXT PRIMARY KEY)")));   // runs once each

Query q = SelectBuilder.from("players").where("coins", ">=", 100).orderByDescending("coins").limit(10).build();
List<String> top = sql.query(q, row -> row.getString("uuid"));   // injection-safe, bound params
```

### Integrations

```java
VaultEconomy.find().ifPresent(eco -> eco.deposit(player, 100));
LuckPermsHook.find().flatMap(lp -> lp.prefix(player)).ifPresent(prefix -> /* ... */);
String text = Placeholders.apply(player, "Hi %player_name%");   // no-op without PlaceholderAPI

Holograms.builder().line(Text.mini("<yellow>Spawn")).glow(Color.YELLOW).spawnAt(location);

new DiscordWebhook(url).sendEmbed(DiscordEmbed.colored("Alert", "Server started", 0x00FF00));
```

## Building from source

```bash
./gradlew build                   # compile, format check, static analysis, tests
./gradlew :uxmlib-all:shadowJar   # the standalone plugin jar
./gradlew publishToMavenLocal     # install every module to ~/.m2 to try locally
```

## License

MIT — see [LICENSE](LICENSE). Use it anywhere, including closed-source plugins.
