# uxmLib

[![](https://jitpack.io/v/siracozmen01/uxmLib.svg)](https://jitpack.io/#siracozmen01/uxmLib)

A small, modern toolkit for writing Paper plugins on Minecraft **1.21+**. It bundles the things every
plugin ends up rewriting — inventory GUIs, item building, commands, config, storage, integrations, HUD
overlays (scoreboard/title/actionbar/bossbar/tablist), an update-checker, and a config-driven condition
engine — behind a clean API, so you stop copy-pasting the same helpers between projects.

It targets **Paper 1.21+ and Java 21 only**. That's deliberate: no legacy cross-version reflection layers
to drag around, just the current Paper API used the way it's meant to be used. Everything is built with
`-Werror`, NullAway null-safety, Spotless formatting, and ArchUnit architecture tests, and unit-tested.

## Modules

Each module is published separately; pull only what you use.

| Module | What it gives you |
| --- | --- |
| `uxmlib-common` | Folia-ready `Scheduler`, MiniMessage `Text`, node-based `HoconConfig` + typed-record `RecordConfig`, an i18n message catalog + retargetable `Message`, a ReDoS-guarded `TimedRegex`, `Durations`/`Numbers`/`Sounds`/`SemanticVersion`/particle helpers |
| `uxmlib-item` | A fluent `ItemBuilder` (name/lore/enchants/flags/banner/map/components, with removers), sealed `SkullData`, registry lookups, component-safe + gzip serialization, single-key `isSimilar`, typed PDC + UUID codec, HOCON→item loader, async skull resolver |
| `uxmlib-gui` | Inventory-menu framework — simple/paginated/scrolling/storage/typed menus, per-viewer & animated items, fillers, interaction control, navigation, config-driven state menus, an in-game config editor, click audit log, async/declarative click pipeline, unified anvil/chat/sign input, server-side Dialogs |
| `uxmlib-command` | A Brigadier facade + annotation DSL over a platform-neutral node IR: args/suggestions/permissions, `@Range`/`@Length`, `@Cooldown`, `@CommandPriority`, flags & switches, orphan args, async execution, validator/context/condition + annotation-replacer SPIs |
| `uxmlib-storage` | Pooled (HikariCP) + cached (Caffeine) JDBC, a cross-dialect query builder (SQLite/MySQL/Postgres/H2), upserts, migrations, schema introspection & column ops, keyset paging, write-behind + two-tier player cache, and cross-server row-sync |
| `uxmlib-integration` | PlaceholderAPI read **and** expansion registration, Vault **and** VaultUnlocked economy, LuckPerms hooks (online & offline, group listing), native-Display holograms (pools/widgets/leaderboards, live skin resolver, mixed item/block/text lines, per-viewer content), an advancement-toast API, a Discord webhook with a fluent embed builder |
| `uxmlib-hud` | Native-Adventure HUD overlays — flicker-free diff sidebar, title/subtitle, sticky actionbar, bossbar (with modes), tablist, per-tick update batching, animated/ticker text |
| `uxmlib-update` | A notify-only release update-checker (GitHub/Modrinth) with a build-time version constant — never self-downloads |
| `uxmlib-condition` | A declarative condition engine (placeholder comparator + failure policy) and a config-driven action engine |
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
    // ...and uxmlib-common / uxmlib-storage / uxmlib-integration / uxmlib-hud /
    // uxmlib-update / uxmlib-condition as needed
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
menu.filler().fillBorder(GuiItem.display(pane));     // border / row / column / rect / fill helpers
menu.set(2, 5, GuiItem.button(icon, e -> click()));  // 1-indexed row, col
menu.addItem(a, b, c);                               // drop into the next empty slots
menu.onDefaultClick(e -> {});                        // fallback for empty-slot clicks
menu.onClose(event -> { /* ... */ });
menu.updateTitle(Text.mini("<green>Updated"));       // live title change
menu.open(player);

// Paginated, scrolling, and non-chest (hopper/dispenser) shapes:
PaginatedGui shop = Guis.paginated().title(Text.mini("Shop")).rows(6).build();
products.forEach(p -> shop.addPageItem(GuiItem.button(p.icon(), e -> buy(p))));
shop.open(player);

ScrollingGui list = Guis.scrolling(ScrollType.VERTICAL).rows(4).build();
entries.forEach(e -> list.addScrollItem(GuiItem.display(e.icon())));   // scrollNext()/scrollPrevious()

// A storage menu holds real items (take/place allowed) and keeps them across opens:
StorageGui vault = Guis.storage().rows(3).build();
vault.setContents(saved);
vault.onClose(e -> persist(vault.contents()));

// Fine-grained interaction control on any menu:
Guis.gui().rows(3).allow(InteractionModifier.ITEM_TAKE).build();

// Per-viewer items: dynamic (computed per player), stateful (first matching condition), animated:
menu.set(4, GuiItem.dynamic(ctx -> headOf(ctx.viewer())));
menu.set(5, GuiItem.stateful()
        .display(ctx -> ctx.viewer().hasPermission("vip"), vipIcon)
        .display(ctx -> true, normalIcon).build());
menu.set(6, GuiItem.animated(List.of(frame1, frame2), Duration.ofMillis(250)));
Guis.install(plugin, scheduler);          // the Scheduler overload enables animation/auto-refresh

// Display-modifier pipeline (viewer's own head, PlaceholderAPI, per-viewer transforms):
DisplayModifiers.apply(item, DisplayModifiers.of(
        DisplayModifiers.viewerSkull(),
        DisplayModifiers.placeholders(Placeholders::apply)));   // any (viewer, text) -> text seam

// Populate a paginated menu straight from a domain list:
shop.populate(products, ItemPopulator.of(p -> p.icon(), (p, e) -> buy(p)));

// Multi-screen navigation with a back-stack:
GuiNavigator nav = new GuiNavigator();
nav.open(player, mainMenu);
subMenu.set(8, GuiItem.back(nav, backArrow));   // also nextPage/previousPage/scrollNext helpers

// Define a menu entirely in HOCON (operators re-skin; code owns the actions):
MenuActions actions = new MenuActions().register("buy", e -> openShop(e));
SimpleGui fromFile = MenuConfig.load(configNode, actions);

// Click/open sounds:
Guis.gui().rows(3).clickSound(click).openSound(open).build();

// Anvil text input:
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
EconomyBridge.orDummy().deposit(player, 100);   // resolves Vault or VaultUnlocked; no-op dummy otherwise
LuckPermsHook.find().flatMap(lp -> lp.prefix(player)).ifPresent(prefix -> /* ... */);
String text = Placeholders.apply(player, "Hi %player_name%");   // no-op without PlaceholderAPI

Holograms.builder().line(Text.mini("<yellow>Spawn")).glow(Color.YELLOW).spawnAt(location);

new DiscordWebhook(url).sendEmbed(DiscordEmbed.colored("Alert", "Server started", 0x00FF00));
```

### HUD overlays

```java
// Flicker-free sidebar (only changed lines re-send), titles, sticky actionbar, countdown bossbar:
SidebarManager sidebars = new SidebarManager(Bukkit.getScoreboardManager());
Sidebar sb = sidebars.create(player, Text.mini("<gold>Server"));
sb.lines(List.of(Text.mini("<gray>Online: <white>42"), Text.mini("<gray>Map: <white>spawn")));
sb.show();

new Titles().show(player, Text.mini("<green>Welcome"), Text.mini("<gray>have fun"));
new Tablist().set(player, header, footer);

new ActionBarManager(scheduler, server).show(player, Text.mini("<yellow>Saved!"), Duration.ofSeconds(3));
new BossBarManager(scheduler, server).countdown(player, Text.mini("<red>Event"), Duration.ofMinutes(1));
```

### Conditions & actions

```java
// A config-driven gate: resolve %...% through an injected resolver, then compare; with a failure message.
ConditionList gate = ConditionList.of(
        PlaceholderCondition.parse("%player_level% >= 30"), Text.mini("<red>You need level 30"));
boolean allowed = gate.test(ConditionRequest.forPlayer(player));

// Named actions, parsed once into closures and run in order:
ActionList.parse(List.of("[message] <green>Hi %player_name%", "[console] heal %player_name%")).run(ctx);
```

### Update-checker

```java
// Notify-only: console log + a clickable on-join message; it never self-downloads.
UpdateChecker checker = new UpdateChecker(
        scheduler, new GitHubReleaseProvider("you", "your-plugin"), UxmLibVersion.VERSION);
new UpdateNotifier(plugin, scheduler, checker, "yourplugin.update.notify")
        .start(Duration.ofSeconds(40), Duration.ofHours(6));
```

## Building from source

```bash
./gradlew build                   # compile, format check, static analysis, tests
./gradlew :uxmlib-all:shadowJar   # the standalone plugin jar
./gradlew publishToMavenLocal     # install every module to ~/.m2 to try locally
```

## License

MIT — see [LICENSE](LICENSE). Use it anywhere, including closed-source plugins.
