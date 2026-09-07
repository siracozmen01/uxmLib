# uxmLib

[![build](https://github.com/UXPLIMA/uxm-lib/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/UXPLIMA/uxm-lib/actions/workflows/build.yml)
[![JitPack](https://jitpack.io/v/UXPLIMA/uxm-lib.svg)](https://jitpack.io/#UXPLIMA/uxm-lib)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Paper 26.2+](https://img.shields.io/badge/Paper-26.2%2B-brightgreen.svg)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-ready-success.svg)](https://docs.papermc.io/folia)

A modern, modular toolkit for writing **Paper 26.2+** plugins on **Java 21**. It bundles the parts every
plugin ends up re-implementing: inventory GUIs, item building, Brigadier commands, typed config, pooled
storage, soft-dependency integrations, HUD overlays, holograms, a notify-only update checker, and a
config-driven condition/action engine, behind a clean, documented API, so you stop copy-pasting the same
helpers from project to project.

It is built for **one server line, on purpose**. No cross-version reflection layers to carry around, and
no compatibility seams: just the current Paper API used the way it is meant to be used, **Folia-ready from line one**, and verified by
Error Prone, NullAway null-safety, Spotless formatting, ArchUnit architecture rules, and unit tests under
`-Werror`.

Pull only the modules you use as Maven artifacts and shade them, or drop the aggregate jar on the server
and depend on it as a normal plugin. Both work.

---

## Table of contents

- [Why uxmLib](#why-uxmlib)
- [Requirements](#requirements)
- [Modules](#modules)
- [Installation](#installation)
  - [Gradle (Kotlin DSL)](#gradle-kotlin-dsl)
  - [Gradle (Groovy DSL)](#gradle-groovy-dsl)
  - [Maven](#maven)
  - [Align versions with the BOM](#align-versions-with-the-bom)
  - [Standalone plugin jar](#standalone-plugin-jar)
  - [Shading and relocation](#shading-and-relocation)
- [Feature tour](#feature-tour)
  - [Text](#text)
  - [Style layer](#style-layer)
  - [Scheduling (Folia-ready)](#scheduling-folia-ready)
  - [Items](#items)
  - [GUIs](#guis)
  - [Menu engine](#menu-engine)
  - [Bedrock forms](#bedrock-forms)
  - [Commands](#commands)
  - [Configuration](#configuration)
  - [Storage](#storage)
  - [Integrations](#integrations)
  - [Holograms](#holograms)
  - [HUD overlays](#hud-overlays)
  - [Conditions & actions](#conditions--actions)
  - [Cross-server messaging](#cross-server-messaging)
  - [Update checker](#update-checker)
  - [Backup participation](#backup-participation)
  - [Experimental: packet layer](#experimental-packet-layer)
- [Architecture & quality](#architecture--quality)
- [Building from source](#building-from-source)
- [Versioning & stability](#versioning--stability)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgements](#acknowledgements)

---

## Why uxmLib

- **One platform, done well.** Paper 26.2 and up. Every API targets the current server, with no reflection
  machinery and no adapter layer to support servers that no longer exist.
- **Modular.** Each `uxmlib-*` module is published independently: take the GUI framework without the
  storage stack, or just the command DSL. Modules never depend "upward", so the graph stays a clean tree.
- **Folia-ready.** Nothing schedules through `BukkitScheduler`. The library's `Scheduler` abstraction maps
  cleanly onto Paper's global / region / entity / async schedulers, so the same plugin code runs unchanged
  on Folia.
- **Adventure-native.** All text is Adventure components built from MiniMessage. Legacy `§`/`&` colour
  codes are deliberately unsupported.
- **Null-safe and statically checked.** Every package is JSpecify `@NullMarked`; NullAway and Error Prone
  run as errors, formatting is enforced with Spotless (Palantir Java Format), and ArchUnit guards the
  module boundaries.
- **MIT, and clean-room.** Nothing is copied from GPL/AGPL/proprietary sources. The Minecraft-facing API is
  written from scratch; only neutral infrastructure (HikariCP, Caffeine, Configurate) is taken as a
  dependency. Use it anywhere, including in closed-source plugins.
- **Native where it can be.** GUIs, holograms, HUD overlays, and toasts use the public Paper/Adventure API
  (no packets, no per-version NMS), so they keep working across point releases.

## Requirements

| | |
| --- | --- |
| Server | Paper **26.2+** (the year-based line) |
| Java | **21** bytecode, built with a **25** toolchain. The server runs 25; the emitted class files stay at 21, so the modules that never touch a server can be used from an ordinary Java 21 project. |
| Build (consumers) | Gradle or Maven; the modules are plain Maven artifacts |

Adventure, MiniMessage, and Brigadier are provided by Paper at runtime: uxmLib references them at
compile time only and never ships them.

## Modules

Every module is published separately under the JitPack group `com.github.UXPLIMA.uxm-lib`; pull only
what you use. Modules marked **experimental** are previews with unstable APIs (see
[Versioning & stability](#versioning--stability)).

| Module | What it gives you |
| --- | --- |
| `uxmlib-common` | The shared foundation: a Folia-ready `Scheduler`, MiniMessage `Text`, a config-driven style layer (`Theme`/`Styler`/`Typography`/`StyleTokens`), node-based `HoconConfig` and typed-record `RecordConfig` with hot reload and live `ConfigProperty`, a MiniMessage-native i18n message catalog, a ReDoS-guarded `TimedRegex`, a `BackupParticipants` seam that lets a plugin flush its state before something copies its files, type-safe particle spawning, and `Durations`/`Numbers`/`Sounds`/`SemanticVersion`/`ServerVersion` helpers. |
| `uxmlib-item` | A fluent `ItemBuilder` (name, lore, enchantments, attributes, flags, durability, banners, components, with removers), sealed `SkullData` player heads with an async skin resolver, registry lookups for the key-based enchantments and attributes, component-safe and gzip serialization, single-key `isSimilar`, and typed persistent-data helpers. |
| `uxmlib-gui` | An inventory-menu framework: simple / paginated / scrolling / storage / typed (hopper, dispenser, …) menus; static, animated, dynamic, and per-viewer stateful items; border/row/column/rect fillers; interaction control; multi-screen navigation; menus defined in HOCON; unified anvil/chat/sign text input; the tile/lore/title/sound side of the style layer; and a facade over Paper's server-side Dialogs. |
| `uxmlib-bedrock` | Native Bedrock forms for a server that lets Bedrock clients in through Geyser or Floodgate: a `BedrockDetector` that answers whether a viewer is a Bedrock player, and a `BedrockScreen` that sends them a simple, modal, input, or custom form. The vocabulary a caller builds a form out of (`BedrockButton`, `BedrockWidget`, `BedrockImage`, `BedrockIcons`) names no SDK type, so a form is built and tested with nothing on the classpath. Each interface has a `NONE` constant and a `forServer` factory, so a Java-only server loads no Geyser or Floodgate class at all and a caller never guards the call itself. |
| `uxmlib-menu` | The data-driven menu engine: a menu is a file, and this module reads one and runs it. A HOCON spec parses into an immutable, platform-free model, which a renderer draws and a runtime clicks through. It carries paged and scrolling lists with a query and a sort, a requirement and an action on each of the four click sides, an expression language for slots and computed numbers, a property editor (text, number, toggle, enum, colour, list) over the unified text input, confirm and selector windows, and an icon provider registry that reads a head, a serialized stack, or an item from ItemsAdder, Oraxen, Nexo, CraftEngine, MMOItems, ExecutableItems, HeadDatabase and EntryStack past a plugin-present guard. A Bedrock viewer is handed the same menu as a native form. It names no colour and no wording of its own: a consumer registers what its menus may say and do through `MenuBindings`. |
| `uxmlib-command` | A thin facade over Paper's Brigadier (`Cmd`/`Args`/`Sender`/`CommandRegistrar`) **and** an annotation DSL on top of it: `@Command`/`@Subcommand`/`@Arg`, permissions, `@Range`/`@Length`, `@Cooldown`, flags and switches, async execution, help pagination, and resolver/validator/condition SPIs. |
| `uxmlib-storage` | Plain-JDBC persistence: a HikariCP-pooled `Database` (SQLite default; MySQL/MariaDB, PostgreSQL, H2 opt-in), an injection-safe `SelectBuilder`, parameterised `Sql`/`TxSql`, versioned migrations, a Caffeine-backed write-through / write-behind cache, a two-tier player-profile cache, and cross-server row sync. |
| `uxmlib-redis` | A low-level binary (`byte[]`) Redis pub/sub bus for fanning an opaque frame across the server nodes sharing one Redis (fail-degraded publish, per-subscription auto-reconnect), with no relational dependencies (Lettuce is a compile-only soft-dependency). |
| `uxmlib-integration` | Soft-dependency hooks reached only past a present-guard: PlaceholderAPI (read **and** expansion registration), Vault and VaultUnlocked economy, Vault permissions, LuckPerms, WorldGuard/Towny region queries, a transient advancement-toast API, an online-data lifecycle manager, a dependency-free Discord webhook, and native-`Display` [holograms](#holograms). |
| `uxmlib-hud` | Adventure-native HUD overlays, all through the public player API: a flicker-free diffing sidebar, title/subtitle, a sticky action bar, boss bars with a mode enum (permanent/filling/countdown/dynamic), tablist header/footer, per-tick text animators, and a nametag registry that composes several plugins' prefixes, suffixes and colours onto the one team a player may belong to. |
| `uxmlib-update` | A notify-only release update checker (GitHub / Modrinth providers) that compares a build-time version constant against the latest release and surfaces a permission-gated clickable join message. It never self-downloads. |
| `uxmlib-condition` | A declarative condition engine (operand comparison + placeholder resolution + failure policy) and its natural pair, a config-driven action engine (`[message]`, `[console]`, `[title]`, … parsed once into closures). Its `Wallet` seam ships soft backends for Vault, VaultUnlocked, PlayerPoints, EcoBits, Treasury, and any economy reachable by a placeholder and a console line, plus the player's own experience counted in points or in levels. |
| `uxmlib-pipeline` | **Experimental.** A from-scratch, MIT-clean Netty pipeline: channel resolve, idempotent inject/eject, a self-healing reorder watchdog, and a fail-open listener seam. It builds no packet and knows no entity. Alone in the packet family it needs no Mojang-mapped server, so a plugin that wants a pipeline and no server internals can take it on its own. |
| `uxmlib-packet` | **Experimental.** The shared Mojang-mapped packet helpers (Adventure→vanilla component conversion, bundling, the stream-codec buffer trick, guarded reflection, entity-id allocation) plus per-viewer tab-list, NPC, text-display, and inventory-item packet ports built on them. |
| `uxmlib-nametags` | **Experimental.** A from-scratch per-viewer nametag renderer (different prefixes/colours/visibility per viewer) over scoreboard-team and metadata packets, without touching the server-side scoreboard. |
| `uxmlib-bom` | A bill of materials so a consumer can align every `uxmlib-*` artifact to one version with a single platform import. |
| `uxmlib-all` | The aggregate of every module on the API surface. The same module also builds the standalone server-side plugin jar, published beside it under the `standalone` classifier. |

```mermaid
graph TD
    common[uxmlib-common]
    item[uxmlib-item] --> common
    command[uxmlib-command] --> common
    bedrock[uxmlib-bedrock]
    gui[uxmlib-gui] --> common
    gui --> item
    gui --> bedrock
    menu[uxmlib-menu] --> gui
    menu --> item
    menu --> bedrock
    storage[uxmlib-storage] --> common
    integration[uxmlib-integration] --> common
    hud[uxmlib-hud] --> common
    update[uxmlib-update] --> common
    condition[uxmlib-condition] --> common
    redis[uxmlib-redis]
    pipeline[uxmlib-pipeline] --> common
    packet[uxmlib-packet] --> pipeline
    nametags[uxmlib-nametags] --> common
    nametags --> pipeline
    nametags --> packet
```

## Installation

uxmLib is published through [JitPack](https://jitpack.io/#UXPLIMA/uxm-lib). Add the JitPack repository
(plus Paper's, since the modules compile against the Paper API), then the modules you need. JitPack serves
each module under the group `com.github.UXPLIMA.uxm-lib` with the git tag as the version.

> Replace `VERSION` with the latest released tag, the version shown on the JitPack badge above.
> There is no `com.github.UXPLIMA:uxm-lib` artifact: the group carries the repository name after a
> dot, and the coordinate always ends in a module.

> **The repository used to be called `uxmLib`.** Builds that ask for the old group
> `com.github.UXPLIMA.uxmLib` still resolve: GitHub redirects the old repository path, JitPack follows
> it, and every version built before the rename stays served from its cache. New builds should use
> `com.github.UXPLIMA.uxm-lib`. The old group works, but it rests on that redirect, and the redirect
> would be lost the moment anything else claimed the name `uxmLib` under this account, so nothing
> should ever be published under that name again.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.UXPLIMA.uxm-lib:uxmlib-gui:VERSION")
    implementation("com.github.UXPLIMA.uxm-lib:uxmlib-item:VERSION")
    implementation("com.github.UXPLIMA.uxm-lib:uxmlib-command:VERSION")
    // ...and uxmlib-common / uxmlib-menu / uxmlib-bedrock / uxmlib-storage /
    // uxmlib-integration / uxmlib-hud / uxmlib-update / uxmlib-condition as needed
}
```

### Want the whole surface in one line

`uxmlib-all` depends on every module, so declaring it pulls them all in:

```kotlin
dependencies {
    implementation("com.github.UXPLIMA.uxm-lib:uxmlib-all:VERSION")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://repo.papermc.io/repository/maven-public/' }
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.UXPLIMA.uxm-lib:uxmlib-gui:VERSION'
    implementation 'com.github.UXPLIMA.uxm-lib:uxmlib-item:VERSION'
}
```

### Maven

```xml
<repositories>
  <repository>
    <id>papermc</id>
    <url>https://repo.papermc.io/repository/maven-public/</url>
  </repository>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.UXPLIMA.uxm-lib</groupId>
  <artifactId>uxmlib-gui</artifactId>
  <version>VERSION</version>
</dependency>
```

### Align versions with the BOM

Importing the BOM lets you list modules without repeating the version on each one:

```kotlin
dependencies {
    implementation(platform("com.github.UXPLIMA.uxm-lib:uxmlib-bom:VERSION"))

    implementation("com.github.UXPLIMA.uxm-lib:uxmlib-gui")
    implementation("com.github.UXPLIMA.uxm-lib:uxmlib-item")
    implementation("com.github.UXPLIMA.uxm-lib:uxmlib-storage")
}
```

### Standalone plugin jar

Prefer not to shade? Take the standalone jar, drop it in the server's `plugins/` folder, and have your
plugins depend on it as a normal Paper dependency:

```yaml
# paper-plugin.yml
depend:
  - uxmlib
```

The standalone jar registers only the handful of listeners whose behaviour is driven entirely by item
persistent data; the rest of the library is consumed as an API.

It is a separate artifact from the aggregate you compile against: same module and version, `standalone`
classifier, because it bundles Configurate, HikariCP and Caffeine under relocated package names. Compiling
against relocated types would put classes on your compile classpath that no consumer can produce. Grab it
from the release assets, from Maven, or build it yourself:

```kotlin
// only if you need the file itself, e.g. to copy it into a server image
dependencies {
    runtimeOnly("com.github.UXPLIMA.uxm-lib:uxmlib-all:VERSION:standalone")
}
```

```bash
./gradlew :uxmlib-all:shadowJar   # build/libs/uxmlib-all-VERSION-standalone.jar
```

### Shading and relocation

When you shade individual modules into your own jar, relocate `com.uxplima.uxmlib` to avoid clashing with
another plugin that also bundles uxmLib:

```kotlin
tasks.shadowJar {
    relocate("com.uxplima.uxmlib", "com.yourplugin.libs.uxmlib")
}
```

The config and storage layers locate their codecs through the JDK `ServiceLoader`. If you enable the Shadow
plugin's `minimize()`, keep the service-provider files and their backing classes, or config parsing will
fail at runtime with a missing-serializer error:

```kotlin
tasks.shadowJar {
    minimize {
        exclude("META-INF/services/**")
        exclude(dependency("org.spongepowered:configurate-.*:.*"))
    }
}
```

## Feature tour

The examples below assume the MiniMessage text helper is imported:

```java
import com.uxplima.uxmlib.text.Text;
import net.kyori.adventure.text.Component;
```

### Text

`Text` is the single place a plugin turns a MiniMessage string into an Adventure `Component`.

```java
Component title = Text.mini("<gradient:#ff5555:#ffaa00>Welcome</gradient>");
Component greet = Text.mini("<gray>Hello, <player>!", Text.placeholder("player", name));

String plain = Text.plain(title);     // strip formatting for logs
String mm    = Text.serialize(title); // round-trip back to MiniMessage
```

Text a player typed is never a MiniMessage string. `paint` applies a style from your config to a body that
stays literal, so a chat message reading `<click:run_command:/op me>free rank</click>` is shown, not run:

```java
Component line = Text.paint(message, config.getString("chat.format-style")); // e.g. "<gray>"
```

### Style layer

A palette, the letters an interface is set in, and the tokens a message file writes instead of colours,
so a server changes its whole look by editing one file, and no plugin ships a hex code.

A theme has three layers. `palette` is the server's own colours under the server's own names, and nothing
outside that file speaks those names, so any of them may be renamed or dropped. `roles` is the shared
vocabulary a message writes: `accent`, `value`, `good`. Each role points at a palette name or a hex code,
and the map is open, so a server may add a job of its own and use it as a tag the same day. `wheel` is an
ordered list of colours that decoration is taken from, so a screen of tiles can differ tile by tile without
any file naming a colour.

```java
// One file for a suite of plugins, with this plugin's own file on top of it, key by key. The folder
// the suite shares is your word, not the library's; a plugin that stands alone reads its own file.
Theme theme = ThemeFiles.load(ThemeFiles.shared(dataFolder, "myTheme"), ThemeFiles.own(dataFolder));
Styler styler = new Styler(theme);

// A catalog line names a role, never a colour:
//   join.welcome = "<tag:'HOME'><body>Welcome back, <value><player></value>"
messages.reload(styler.style(catalog, MyKeys.values(), files, Locale.ENGLISH));

// On a /reload, hand the same styler the new palette rather than building a second one:
styler.reload(ThemeFiles.load(ThemeFiles.shared(dataFolder, "myTheme"), ThemeFiles.own(dataFolder)));

// Text a plugin computes has no catalog entry to style at load, so style it per viewer, and ask
// Messages which language that viewer is being served rather than reading the player's own locale,
// or a server that pins one gets catalog text in one language and computed text styled for another.
Component name = Text.mini(styler.apply(colour.displayName(), messages.localeOf(viewer)));
```

`Typography` converts a template to small capitals for the languages the theme names, leaving tags,
placeholders and anything inside `<plain>…</plain>` alone, so a translator writes ordinary words and nothing
rewrites the file they work in. It names none until your `theme.conf` does: a typeface is taste, and a
library that wrote your English in small capitals by itself would repaint a plugin that only wanted the
colours. `StyleTokens` then turns `<accent>`/`<value>` into the
theme's colours and `<tag:'HOME'>` into a bold category prefix. Both run once at load, not per message.

A value is inserted after that pass and is never converted, because a name, a nickname and a world are what
a player wrote. When a value is the interface talking instead (the name of an item, the word for a state),
write `<caps>…</caps>` around it and it is converted at render:

```
listed = "You listed <caps><item></caps> for <money><price>"   // the item is converted, the player is not
```

It is the mirror of `<plain>…</plain>` and it follows the same language list: turn small capitals off in
`theme.conf` and every `<caps>` goes quiet with the rest, so a screen is never half converted. Small
capitals are Latin only, so a language whose letters have no small-capital form simply does not write the
tag, which is why the choice is one line at a time rather than one switch for the server.

Menus draw from the same theme: `MenuTitles.centre` pads a window title into the middle of the frame,
`Tiles` puts a tile's title on the first lore line under a blank name (a single space: an empty one makes
the client fall back to the material's name) and paints it with a gradient the caller names, `LoreWrap` builds
the tooltip in one fixed order and wraps a description at a width the caller may set, and
`MenuSounds` reads the three menu sounds from config. Lore an operator wrote in a config file goes through
`Lore.lines`, which reads every line as body text and gives it the same column, padding and closing air as
lore built here, so a plugin that ships items never has to write that geometry down as a house rule of its
own. A file that draws its own glyphs and its own margins goes through `Lore.verbatim` instead, which adds
the padding and the closing air and leaves the geometry alone, so a plugin can still let its buyer write a
look that is nothing like this one.

The library ships `uxmlib/theme.conf` as a starting file a consumer copies and then owns: a palette, the
roles pointed at it, and every other block written out but commented, so what it turns on is visible and
short. A key it leaves out keeps the shipped answer.

What ships is the mechanism, and not a look. Every role answers, so `<value>` is a colour rather than seven
characters a player reads, and each one falls back to one of the sixteen colours Minecraft has always had:
the one palette that is neither ours nor invented. Nothing else is decided. No glyph is drawn, no category
takes a colour of its own, no gradient and no wheel exist, and no language is written in small capitals,
until your file names them. A `gradients { header = [...] }` block paints every `<h:'…'>` header across
those stops; leave it out and headers stay the flat accent colour.

A plugin that stands alone wants one file and nothing beneath it, which is `ThemeFiles.load(file)`. The
two-file form is for a suite that agrees on a folder name.

A name that is not a heading takes `<g:'UXM Network':wheel>`: the same lookup as a heading, painted across
every colour of the wheel in order, with no bold and in the letters the file wrote. That is the one token a
server list line or a title screen usually wants, and it keeps the seven colours in the theme rather than in
the file.

A heading may take its colour three ways, and the file that draws it picks the one that fits. The wheel is
the one that needs no name: `Tiles.titled(theme, title, lore, position)` paints the tile at that position
with that arc of the wheel, so twelve tiles read as twelve headings and the layout file stays free of
colour. A content file reaches the same wheel by writing the position in the token, `<h:'REWARDS':4>`,
which is what a hotbar or a list written in configuration uses. A heading that always means the same thing
names a role instead, `<h:'REWARDS':good>`. A sweep of your own is a named gradient in the file,
`<h:'REWARDS':mint>`. The library never picks for you: which tiles look alike is a decision about one
interface, and only the file that knows what the tiles are about can make it.

### Scheduling (Folia-ready)

One `Scheduler` interface covers Paper's four schedulers; build it once and inject it everywhere. Every
method returns a cancellable `TaskHandle`, so your plugin never touches `BukkitScheduler` and runs
unchanged on Folia.

```java
Scheduler scheduler = new PaperScheduler(plugin);

scheduler.global(() -> broadcast());                                  // next tick, global region
scheduler.regionLater(location, Duration.ofSeconds(2), () -> grow()); // region-threaded
scheduler.entityTimer(player, Duration.ZERO, Duration.ofSeconds(1),
        handle -> { if (done) handle.cancel(); });                    // entity-threaded, repeating
scheduler.async(() -> fetchFromApi());                                // off the main thread
```

### Items

```java
ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
        .name(Text.mini("<gradient:#ff5555:#ffaa00>Flameblade</gradient>"))
        .lore(Text.mini("<gray>A legendary weapon"))
        .enchant(Items.enchantment("sharpness"), 5)   // enchantments are registry entries
        .flags(ItemFlag.HIDE_ENCHANTS)
        .unbreakable(true)
        .build();

ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
        .skull(SkullData.ofName("Notch"))
        .build();

String saved = ItemSerialization.toBase64(sword);  // survives every data component
ItemStack back = ItemSerialization.fromBase64(saved);

// A menu icon is a button, and the client does not know that: it writes its own lines under the lore,
// so a leather chestplate used as a button says "Dyed" and "Armor" beneath whatever the menu wrote.
ItemStack button = ItemBuilder.of(Material.LEATHER_CHESTPLATE)
        .name(Text.mini("<white>Cosmetics"))
        .vanillaTooltip(false)          // or hiddenComponents(...) to keep one of them
        .build();
```

`ItemConfig` reads the same spec from HOCON, `hide-vanilla-tooltip = true` included, so an operator writing
a menu file reaches for that rather than for the deprecated `HIDE_ADDITIONAL_TOOLTIP` flag. Leave it off for
an item a player owns: on a kit sword or a vault icon the damage and enchantment lines are the point.

It also reads the six blocks that belong to one kind of item, in the same keys and the same tokens a menu
file's `decor` block already uses:

```hocon
material = POTION
potion {
  type    = strength                # the base potion, a registry id
  color   = "#00AAFF"               # the bottle tint
  effects = ["speed:1:600"]         # effect:amplifier:durationTicks
}
leather-color = "#A1FF33"           # dyed leather: hex, an "r,g,b" triple, or a dye name
firework {
  power   = 2                       # flight duration, 0 to 127
  effects = ["ball_large:#ff0000,#ffff00:#ffffff:flicker,trail"]
}                                   # type:colours:fade-colours:flags; the last two are optional
trim   { material = diamond, pattern = sentry }
banner { patterns = ["stripe_top:red", "border:white"] }   # laid on in the order written
spawner = zombie                    # the mob inside a spawner
```

A value that names nothing the server knows throws and says which value it was. That is on purpose and it is
where this parts company with the menu renderer, which skips a token it cannot resolve: a menu with one wrong
tile still opens, while an item read once at load and then sold must not quietly become a water bottle.

### GUIs

Install the framework once in `onEnable`, then build menus fluently. Clicks are cancelled by default: an
unconfigured menu can never leak items.

```java
Guis.install(plugin, scheduler);   // the Scheduler overload enables animation and auto-refresh

SimpleGui menu = Guis.gui().title(Text.mini("<dark_aqua>Menu")).rows(3).build();
menu.filler().fillBorder(GuiItem.display(pane));        // border / row / column / rect / fill helpers
menu.set(2, 5, GuiItem.button(icon, e -> click()));     // 1-indexed row, col
menu.onClose(event -> persist());
menu.open(player);

// Paginated, scrolling, and non-chest shapes:
PaginatedGui shop = Guis.paginated().title(Text.mini("Shop")).rows(6).build();
products.forEach(p -> shop.addPageItem(GuiItem.button(p.icon(), e -> buy(p))));
shop.open(player);

ScrollingGui list = Guis.scrolling(ScrollType.VERTICAL).rows(4).build();

// A storage menu holds real items (take/place allowed) and keeps them across opens:
StorageGui vault = Guis.storage().rows(3).build();
vault.onClose(e -> save(vault.contents()));

// Per-viewer items: dynamic (computed per player), stateful (first matching state), animated:
menu.set(4, GuiItem.dynamic(ctx -> headOf(ctx.viewer())));
menu.set(5, GuiItem.stateful()
        .display(ctx -> ctx.viewer().hasPermission("vip"), vipIcon)
        .display(ctx -> true, normalIcon)
        .build());
menu.set(6, GuiItem.animated(List.of(frame1, frame2), Duration.ofMillis(250)));

// Multi-screen navigation with a back-stack:
GuiNavigator nav = new GuiNavigator();
nav.open(player, mainMenu);
subMenu.set(8, GuiItem.back(nav, backArrow));

```

A menu written in a file is the [menu engine](#menu-engine)'s work, not this module's. `uxmlib-gui`
draws what code puts in a window; `uxmlib-menu` reads the file that says what goes in it.

The two seams this module keeps for it are the words. `GuiText` is where a consumer answers what a key
stands for and how a written line reads, and `CatalogueWords` is the answer for a consumer whose words
are in a message catalogue: a key is looked up in the language of the viewer, a plain line is painted in
the roles of the theme, and a line that starts with `tile:` is drawn as the whole tooltip through
`MenuTiles`. The library parses no text of its own, so it decides no look and holds no language.

A tile line takes its closing sentence from `<key>.action` under the block it names, and a word written as
`action:@<key>` on that line takes it from the key named there instead. A tile whose click answers a
different thing in each state is then one line and one block of words rather than one whole block per
state, and the sentence stays a catalogue line, so it keeps the colour it is written in. The state may be a
`%token%` inside the key, because the engine fills a token in before the line is read as a tile.

> **Removed in 0.46.0.** `com.uxplima.uxmlib.gui.config` shipped in 49 of this library's 50 tags, so it is
> the largest published surface any release has removed. It is gone: `MenuConfig.load`, `MenuSpec.read`,
> `MenuDraw`, `MenuActions`, `MenuConditions`, `MenuLists`, `MenuFiles`, `MenuAction`,
> `MenuActionRunner`, `MenuSlots`, `IconOptions`, `ConfigEditorGui` and `ConfigValueEditor` were the
> smaller, older menu readers, and `uxmlib-menu` replaces all of them. Two classes of that package
> survive under new names: `gui.config.CatalogueWords` is `gui.CatalogueWords` and now implements
> `GuiText`, and `gui.config.MenuTiles` is `gui.style.MenuTiles`. A consumer on `MenuConfig.load` has no
> automatic upgrade: the mask shape and the engine's shape are different models, not different
> spellings, and an action or a condition named in an old file points at a handler registered in Java
> rather than at anything the engine can resolve. Move the file to the engine's shape and register the
> vocabulary through `MenuBindings`.
>
> One piece of that package was working code rather than an old reader, and it kept its own home:
> `MenuActionRunner` was the only place that resolved the constant spelling of a sound, the
> `BLOCK_NOTE_BLOCK_PLING` an operator copies out of the API. That is `gui.style.SoundNames` now. A
> consumer that registers a `sound` action for the engine resolves the name through it, and `MenuBasics`
> is the registration most consumers want, so a file that writes a constant keeps playing what it always
> did.

### Menu engine

`uxmlib-gui` draws a menu. `uxmlib-menu` reads one. A menu is a file, and the engine is what turns that
file into a window a player clicks through. A consumer writes no layout code at all: it registers the
names its menus may say and do, and everything else lives in HOCON that an operator owns.

```java
// The whole vocabulary a file may reach. A name that is not registered here does not exist.
MenuBindings bindings = new MenuBindings();
bindings.action("shop:buy", ctx -> buy(ctx.viewer(), ctx.subject(Offer.class)));
bindings.condition("shop:can-afford", (ctx, args) -> balance(ctx.viewer()) >= price(ctx));
bindings.placeholder("shop:balance", ctx -> format(balance(ctx.viewer())));
bindings.list("shop:offers", ctx -> offersFor(ctx.viewer()));
bindings.pagedList("shop:archive", (ctx, page) -> archivePage(ctx.viewer(), page));  // asks the store per page

ItemRenderer items = new ItemRenderer(guiText, this::theme, bindings.placeholders());
MenuRenderer renderer = new MenuRenderer(items, bindings.conditions(), bindings.contents());
Menus menus = new Menus(renderer, scheduler, bindings.lists());

// close, open:<menu>, command:<line>, message:<line> and sound:<name> <volume> <pitch>: the five verbs
// that mean the same thing in every menu of every plugin. They are a call and never automatic, so a
// plugin that wants its own keeps the names and registers those instead.
MenuBasics.register(bindings, menus);

// The operator's file when they have one, the bundled copy when they do not.
MenuSpec shop = MenuSpecs.loadOrBundled("menus/shop.conf", dataFolder, 6, log);
menus.registerSpec("shop", shop);

// A file that names an action, a condition, a placeholder, or a list source that nobody registered is
// reported here, with every offending name, rather than on the click that would have reached it.
List<String> problems = bindings.validate(List.of(shop));

menus.open(player, "shop", null);                  // no subject
menus.open(player, "offer", offer, 2);             // a subject and a page
menus.back(player);                                // the back stack the engine keeps per viewer
menus.confirm(player, Text.mini("<red>Sell everything?"), this::sellAll, () -> menus.reopenLast(player));
menus.openEditor(player, editorSpec, offer);       // a property editor over one object
```

The file carries the layout, the words, the conditions, and the actions:

```hocon
title = "@shop.title"
rows = 6
open-requirement = ["shop:unlocked"]
open-actions = ["sound:ITEM_BOOK_PAGE_TURN 0.7 1.2"]

items {
  filler { slots = ["0-53"], material = GRAY_STAINED_GLASS_PANE, name = " ", priority = 0 }

  offers {
    slots = ["10-16", "19-25"], priority = 5
    list {
      source = "shop:offers"
      page-size = 14
      sorts = ["price", "name"]
      template { material = "%offer_icon%", name = "@shop.offer", lore = ["<gray>%offer_price%"] }
    }
  }

  buy {
    slot = 49, material = EMERALD, name = "@shop.buy", priority = 10
    permission = "shop.buy"
    view = ["shop:can-afford"]
    click {
      left  = { do = ["shop:buy", "close"], requirements = ["shop:can-afford"], deny = ["message:@shop.poor"] }
      right = { do = "confirm:shop:buy-all", title = "@shop.confirm-all" }
      shift-left = { do = "input:shop:buy-amount", prompt = "@shop.amount" }
    }
  }

  next { slot = 53, material = ARROW, type = next, priority = 10 }
}

bedrock {
  title = "@shop.title"
  widgets = [{ type = dropdown, name = offer, label = "@shop.offer", options = ["Sword", "Shield"] }]
  on-submit = ["shop:buy"]
}
```

What the engine adds over a plain layout reader:

- **Paged and scrolling lists.** A slot range is filled from a registered source, sorted by the names the
  file asks for, and paged over the range. A paged source is asked for one page rather than for everything.
- **An expression language.** Any rendered line may carry a `{math: ...}` block, evaluated after the
  placeholders in it are substituted: `{math: %price% * %amount%}`, `{math: min(%stock%, 45)}`. Seven
  functions are callable and nothing else (`min`, `max`, `abs`, `floor`, `ceil`, `round`, `sqrt`), and a
  block that cannot be read renders blank rather than leaking its own text at a player.
- **A requirement and an action chain on each of the eight click gestures** (left, right, the two shifted
  forms, middle, drop, control-drop, and double-click), with a `deny` fallback, a `delay`, and a `chance`
  on any single action.
- **Continuations.** An `input:` or a `confirm:` action splits the chain: the rest of it runs when the
  viewer answers, through the same anvil, chat, sign, or dialog input `uxmlib-gui` installs.
- **A property editor.** Text, number, toggle, enum, colour, and list properties over one object, each
  with its own picker window, so a plugin that edits a configuration object writes no menu for it.
- **Icons from other plugins.** An icon spec may name a head, a serialized stack, or an item held by
  ItemsAdder, Oraxen, Nexo, CraftEngine, MMOItems, ExecutableItems, HeadDatabase, or EntryStack. Every
  such lookup sits behind a plugin-present guard and falls back to a plain material when the plugin is not
  there.
- **A Bedrock viewer sees a form.** When the spec carries a `bedrock` block and the viewer is a Bedrock
  player, the engine sends the native form instead of the chest. See [Bedrock forms](#bedrock-forms).
- **The file is written as well as read.** `MenuSpecWriter` turns a `MenuSpec` back into the HOCON the
  loader parses, so a plugin can ship an in-game menu editor without carrying a writer of its own. It is
  model faithful rather than byte faithful: see [Known gaps](#known-gaps).

```java
// The header is the consumer's text, never the library's: it names your commands and your folder.
MenuSpecWriter writer = new MenuSpecWriter(readMyHeaderResource());

String hocon = writer.write(edited);          // render, for a preview or a diff
writer.write(edited, dataFolder.resolve("menus/shop.conf"));   // render and save, temp file then rename

// File IO behind a menu click never runs on the main thread.
scheduler.async(() -> writer.write(edited, file));
```

The model under all of this names no platform type at all: a spec parses, validates, and is asserted on
with no server running, and `ArchitectureTest.theMenuModelTouchesNoPlatform` fails the build on the first
import that would break that.

The engine names no colour and no wording. `@shop.title` is a key in the consumer's own catalogue, and the
colours come from the consumer's `Theme`. The library ships neither.

### Bedrock forms

A Bedrock client cannot see a chest menu the way a Java client does, so a plugin that draws a menu has two
questions: is this viewer a Bedrock player, and how do I send them a form. `uxmlib-bedrock` answers both,
and the menu engine uses it for you.

```java
BedrockDetector bedrock = BedrockDetector.forServer(server);   // NONE on a Java-only server
BedrockScreen screen = BedrockScreen.forServer(server);

if (bedrock.isBedrock(player.getUniqueId())) {
    screen.sendSimpleForm(player, "Shop", "Pick a category", List.of(
                    new BedrockButton("Swords", new BedrockImage(BedrockImage.Kind.PATH, "textures/items/iron_sword")),
                    new BedrockButton("Shields", null)),
            index -> scheduler.entity(player, () -> openCategory(player, index)));

    screen.sendCustomForm(player, "Buy", null, List.of(
                    new BedrockWidget.Dropdown("offer", "Offer", List.of("Sword", "Shield"), 0),
                    new BedrockWidget.Slider("amount", "Amount", 1, 64, 1, 1)),
            answers -> buy(player, answers), () -> {});
}
```

Four form kinds are covered: simple (a list of buttons), modal (two buttons), input (one text field), and
custom (any widget list). `BedrockIcons.forMaterialSpec` turns a material name or a head reference into a
button image, so a menu file that already names an icon needs no second Bedrock-only spelling.

Both interfaces follow one shape. Each has a `NONE` constant that names no SDK type and a `forServer`
factory that returns a backed implementation only when Geyser or Floodgate is enabled. The backed
implementations are package-private, so `org.geysermc` is named in exactly two files and reached only
inside the enabled branch. On a Java-only server the factories answer `NONE`, no Geyser or Floodgate class
is ever loaded, and a caller never has to guard the call itself. A response from Cumulus arrives off the
main thread, so a callback hops back onto the viewer's entity thread before it touches the world.

### Commands

Both styles register through Paper's Brigadier lifecycle. Use the annotation DSL for the common case, or
the `Cmd` facade when you need to hand-build a node tree.

```java
@Command(name = "money", description = "Manage balances")
class MoneyCommand {

    @Subcommand("pay")
    @Permission("money.pay")
    void pay(Sender sender, @Arg("target") String target, @Arg(value = "amount", min = 1) int amount) {
        sender.send(Text.mini("<green>Paid " + amount + " to " + target));
    }
}

AnnotatedCommands.register(plugin, new MoneyCommand());
```

```java
// Or build the tree yourself:
CommandRegistrar.register(plugin,
        Cmd.literal("ping").requires(Cmd.permission("x.ping"))
                .executes(ctx -> {
                    Sender.of(ctx.getSource()).send(Text.mini("pong"));
                    return Cmd.OK;
                }),
        "Replies with pong");
```

The annotation layer also covers `@Range`/`@Length` bounds, `@Cooldown` rate limits, `@PlayerOnly`, flags
and switches, async execution, and paginated help, with SPIs for custom argument resolvers, validators,
and conditions.

Every command and every branch of one is renameable, aliasable and disableable from `commands.conf`. Mark
the handler `@FromConfig` with a key, hand `ConfiguredCommands.replacer()` to the resolvers, and the file
decides the label:

```hocon
commands {
  uxmbackup {
    name    = "backup"
    aliases = ["bak"]
    subcommands {
      restore { enabled = false }   # drops the branch out of the tree: the server says it is unknown
      prune   = false               # keeps the branch and lets your handler say it is turned off
    }
  }
}
```

The two shapes do different things on purpose. The block form is replaced by nothing at registration, so
the branch never enters the command tree. The plain one-line form leaves it in, and the handler reads
`commands.isBranchEnabled(KEY, "prune")` and answers in its own words:

```java
if (!commands.isBranchEnabled(BackupCommand.KEY, "prune")) {
    messages.send(sender.bukkit(), Keys.COMMAND_TURNED_OFF, Text.placeholder("name", "prune"));
    return;
}
```

Which one an operator wants depends on whether they would rather the word be unknown or be refused. The
server builds its command tree once, at enable, and offers no supported way to take one branch out of a
live one, so answering is the only honest alternative to removing.

Everything the command layer says on its own behalf: the refusals, the argument rejections, the whole
generated help page down to the separator between a command and its description, goes through
`CommandMessages`, so a translated plugin answers each player in that player's own language and paints the
answer in its own palette:

```java
ParamResolvers resolvers = ParamResolvers.withDefaults()
        .messages(myMessages)                                   // implement only the lines you care about
        .locales(LocaleSource.ofDefault(Locale.forLanguageTag("tr")));

AnnotatedCommands.register(plugin, new HomeCommand(), resolvers);
```

A plugin that already has a message catalog needs none of that by hand: `CommandMessages.fromCatalogue(messages)`
reads every line from the catalog under the `CommandLine` keys (`command.player-only`, `command.help-header`, and
the rest), so the command layer is translated and painted with everything else, and an operator can re-word any
of it in their language file.

The template each key ships is plain MiniMessage in the vanilla colours, and it is meant to be replaced. A
default has to read correctly for a consumer who wires no style layer at all, and MiniMessage leaves a tag it
does not know as literal text, so a default written in one plugin's own vocabulary would reach that consumer's
players as the characters of a tag. Write your own template at these paths, in your own tokens, in your own
language files: the catalog wins over the default for every key it holds.

Each method receives the sender's locale and the *values*: the bad input, the allowed ones, the time left.
Never a finished English sentence, since no other language puts those words in the same order. Every
method has a default, so a plugin that ignores the seam keeps the English it always had.

The help page is worth overriding even in an English-only plugin: `helpCommand`, `helpSeparator` and
`helpDescription` are the only way to restyle it, because that text is generated inside this jar and no
style pass over a plugin's own resources can see it.

### Configuration

Typed configuration over Configurate (HOCON): config is data with IDE support, not string-keyed lookups.

```java
HoconConfig config = HoconConfig.load(dataFolder.resolve("config.conf"));

int limit = config.getInt("homes.limit", 3);

ConfigProperty<Integer> live = config.intProperty("homes.limit", 3);
live.onChange(value -> rebuildLimits(value));   // fires on reload when the value changes
config.reload();
```

```java
// Or map a whole file onto one @ConfigSerializable record, hot-reloaded as an atomic snapshot:
RecordConfig<Settings> settings =
        new RecordConfig<>(dataFolder.resolve("settings.conf"), Settings.class, Settings::new);

Settings current = settings.current();   // cached snapshot: cheap on the hot path
settings.reload();                        // swaps in the new value, or keeps the prior one on a parse error
```

### Storage

```java
Database db = Database.builder().sqlite(dataFolder.resolve("data.db")).build();  // SQLite default, WAL
Sql sql = new Sql(db);
sql.execute("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, coins INTEGER)");

// Versioned migrations run exactly once each, in order:
new MigrationRunner(db).apply(List.of(
        new Migration(1, "init", "CREATE TABLE warps (name TEXT PRIMARY KEY)")));

// Injection-safe query builder with bound parameters:
Query top = SelectBuilder.from("players")
        .where("coins", ">=", 100)
        .orderByDescending("coins")
        .limit(10)
        .build();
List<String> leaders = sql.query(top, row -> row.getString("uuid"));
```

Switch to a network backend by giving the builder a JDBC URL and credentials (`jdbcUrl(...)`,
`username(...)`, `password(...)`) and adding the matching driver: MariaDB/MySQL, PostgreSQL, and H2 are
opt-in. The URL decides the backend: hand `jdbcUrl(...)` a `jdbc:sqlite:` file and you get the same
single-writer pool, WAL journal and lock timeout `sqlite(...)` gives you, so exposing one `jdbc-url` setting
to your operators is safe whichever backend they point it at. Higher-level helpers add a write-through / write-behind cache, a two-tier player-profile cache, and
cross-server row sync.

### Integrations

Every third-party symbol is touched only past a plugin-present guard, so a server without the soft-dependency
still loads cleanly.

```java
// Economy: resolves Vault or VaultUnlocked, with a no-op dummy so call sites never null-check.
EconomyBridge.orDummy().deposit(player, 100);

// Permissions / ranks via LuckPerms, present-guarded:
LuckPermsHook.find().flatMap(lp -> lp.prefix(player)).ifPresent(prefix -> applyPrefix(prefix));

// PlaceholderAPI: a no-op pass-through without PlaceholderAPI installed:
String text = PlaceholderApi.apply(player, "Hi %player_name%");

// Region queries against WorldGuard or Towny behind one provider-agnostic contract:
RegionHooks regions = new RegionHooks();
WorldGuardRegionService.find().ifPresent(regions::register);   // present-guarded
boolean canBuild = regions.active()
        .map(region -> region.canBuild(player, location))
        .orElse(true);

// A transient toast that leaves no persistent advancement behind:
Toast.builder()
        .icon(Material.DIAMOND)
        .title(Text.mini("<gold>Objective complete!"))
        .show(player);

// A dependency-free Discord webhook (no JDA, no bot token):
new DiscordWebhook(url).sendEmbed(DiscordEmbed.colored("Alert", "Server started", 0x00FF00));
```

The PlaceholderAPI integration also has a write side: register a `PlaceholderProvider` to expose your own
`%uxm_<prefix>_<params>%` placeholders. A provider is asked about an `OfflinePlayer`, because that is what a
leaderboard line, a tab-list entry and a hologram name; narrow it with `instanceof Player` when the answer
needs a player who is here.

### Holograms

Holograms are built on native `Display` entities (Text / Item / Block): no packets, no per-version
NMS, and managed for you so visibility, per-viewer content, and cleanup are handled automatically.

```java
HologramManager holograms = new HologramManager();
holograms.installLifecycleListener(plugin);   // resets per-player state on quit / world change

Hologram spawn = holograms.spawn(
        Holograms.builder()
                .line(Text.mini("<yellow><bold>Spawn"))
                .line(Text.mini("<gray>Welcome to the server"))
                .billboard(Display.Billboard.CENTER)
                .glow(Color.YELLOW),
        location);
```

On top of that base the module adds a distance-driven visibility `HologramPool`, per-player widgets
(paged, switchable, live leaderboard), entity-following holograms, text animation (typewriter / scroll),
a Mojang skin resolver for player-head displays, and holograms defined in HOCON.

A real entity is the right answer when an operator has to walk into the hologram and click it. It is the
wrong answer for decoration: it is in the entity count, it is written to the region file, it can be reached
by anything that reaches entities, and a chunk unload takes it away. `PacketHologram` in `uxmlib-packet` is
the other shape. It is sent and never spawned, so the server holds no entity at all.

```java
HologramPackets packets = new NmsHologramPackets(new PacketSender(new ChannelResolver()));

PacketHologram hologram = PacketHologram.show(
        packets,
        scheduler,
        location,
        HologramAppearance.defaults().withBillboard(Display.Billboard.CENTER),
        viewer -> List.of(Text.mini("<yellow>Market"), Text.mini("<gray>right-click the chest")),
        32.0,                          // how near a viewer must stand
        Duration.ofMillis(500));       // how often the audience and the text are recomputed

hologram.remove();                     // takes it off every client and stops the loop
```

Every frame runs on the anchor's region thread and asks the world who is within the view distance. A player
who arrives is sent the spawn frame, a player who leaves is sent a remove, and a player who stays is sent
fresh text, so a per-viewer line stays current. While nobody is near, no packet is written at all.

### HUD overlays

Adventure-native overlays delivered through Paper's own player API: no packets, no NMS.

```java
// Flicker-free sidebar: only the lines that actually changed are re-sent.
SidebarManager sidebars = new SidebarManager(Bukkit.getScoreboardManager());
Sidebar sb = sidebars.create(player, Text.mini("<gold><bold>Server"));
sb.lines(List.of(
        Text.mini("<gray>Online: <white>42"),
        Text.mini("<gray>Map: <white>spawn")));
sb.show();

new Titles().show(player, Text.mini("<green>Welcome"), Text.mini("<gray>have fun"));
new Tablist().set(player, header, footer);

new ActionBarManager(scheduler, server).show(player, Text.mini("<yellow>Saved!"), Duration.ofSeconds(3));
new BossBarManager(scheduler, server).countdown(player, Text.mini("<red>Event"), Duration.ofMinutes(1));
```

A player may belong to exactly one scoreboard team, so plugins that each create their own teams overwrite
one another's nametags without saying so. `NametagRegistry` owns the team instead, and every plugin hands it
a contribution:

```java
NametagRegistry nametags = new NametagRegistry(
        new ScoreboardNametagSink(Bukkit.getScoreboardManager().getMainScoreboard(), getLogger()),
        getLogger(),
        " ",        // what goes between two contributed parts
        scheduler); // the scoreboard belongs to the global region

nametags.contribute(player, NametagContribution.prefix("uxmTags", priorityFromConfig, Text.mini("<gold>[VIP]")));
nametags.contribute(player, NametagContribution.color("uxmGlow", priorityFromConfig, NamedTextColor.RED));

nametags.withdraw("uxmTags"); // onDisable: take back only your own parts
```

Prefixes and suffixes compose in priority order (smaller is earlier), and priority decides layout. The name
has a single colour, so one claim has to win, and which one is your server's rule rather than the library's:
pass a `ComposedNametag.ColorOwner` as the fifth constructor argument. `newest()` is the default and gives
the colour to whoever asked last, so a glow a player just switched on wins over a rank tag that has sat
leftmost all session. `byPriority()` gives it to the smallest priority instead, which is what an estate that
sells a rank colour wants. Write your own for anything else. Every claimant is named once in the log,
together with the winner.
Read your priority from your own config: which of two plugins comes first is an operator's decision. A team
uxmLib did not create is never touched, so a third-party plugin managing its own teams is left alone.

That settles the fight inside one jar. Every plugin relocates its own copy of uxmLib, so two plugins each
constructing a `NametagRegistry` is two registries and two teams, and the second one still loses. Claim it
through `SharedNametags` instead and one server has one registry, whichever plugin loaded first:

```java
Nametags nametags = SharedNametags.claim(this, () -> new NametagRegistry(
        new ScoreboardNametagSink(board, getLogger()), getLogger(), " ", scheduler));

nametags.contribute(player, NametagContribution.prefix(getName(), priority, prefix));

// onDisable
nametags.withdraw(getName());
SharedNametags.release(this);
```

The first plugin to load builds the registry and offers it on the server's own service manager under
`java.util.function.Function`, which comes from the boot class loader and is therefore the same class in
every jar; the payload is JDK, Bukkit and Adventure types, none of which is ever shaded. The registration is
marked, so a `Function` service registered for somebody else's purpose is left alone. This is the same
technique `BackupParticipants` uses for a save request.

Two consequences worth knowing. The plugin that builds the registry decides the separator and the colour rule
for the whole server, so read both from your own config and expect an operator to set them on whichever
plugin loads first. And when an owner is disabled the server unregisters its service, so the next plugin to
call takes the registry over: names come back as plugins contribute again rather than staying gone until a
restart.

### Conditions & actions

A config-driven gate plus a config-driven action list: both parsed once and run many times.

```java
// Resolve %...% through an injected resolver, compare, and collect a failure message:
ConditionList gate = ConditionList.of(
        PlaceholderCondition.parse("%player_level% >= 30"),
        Text.mini("<red>You need level 30"));
boolean allowed = gate.test(ConditionRequest.forPlayer(player));

// Named actions parsed once into closures and run in order:
ActionList.parse(List.of(
        "[message] <green>Hi %player_name%",
        "[console] heal %player_name%")).run(context);
```

`MoneyCondition` and `[take-money]` read through a `Wallet`, and `com.uxplima.uxmlib.condition.wallet`
ships the backends. Each one is soft: the plugin manager is asked before any type of another plugin is
named, the handle is resolved on first use and kept, and a missing or renamed API reads zero and refuses
rather than throwing. A server with none of these plugins behaves as though the package were not there.

```java
// An economy described rather than compiled against. Vault, VaultUnlocked, PlayerPoints and EcoBits are
// shipped descriptions; a fifth economy is one more EconomyBinding and needs nothing from this library.
Wallet vault = BridgedWallet.ofServer(Economies.vault(), log);
Wallet points = BridgedWallet.ofServer(Economies.playerPoints(), log);

// Treasury answers a subscriber instead of returning, so it is written out with a bound on the wait.
Wallet gems = TreasuryWallet.ofServer(Duration.ofSeconds(2), log);

// The last resort: a balance behind a placeholder, taken by a console line.
Wallet tokens = PlaceholderWallet.ofServer(
        PlaceholderWallet.Pool.of("%tokens_balance%", "tokens take {player} {amount}"));

// The player's own experience, which needs no plugin at all. Points and levels are two currencies:
// a level costs seven points near the start and over a hundred past level thirty one.
Wallet xp = ExperienceWallet.ofPoints();
Wallet levels = ExperienceWallet.ofLevels();
```

`ExperienceWallet` is the one backend with no other plugin behind it, so it has no present-guard and
nothing to log. It reads and writes the player's own experience bar, which means the caller must already
be on the thread that owns the player (the region that owns them on Folia), exactly as `[take-item]`
requires for the inventory. A player who is not online reads zero and pays nothing.

A backend answers what the balance is and whether a whole amount can be taken, and nothing else. It fixes
no price, no cost table and no currency name: those are the game a plugin plays. Which currency name maps
to which backend is the plugin's choice too, so routing between several is the plugin's own `Wallet`.

Every take is all or nothing. Where the economy refuses an overdraft itself, its refusal is read straight
back; where it cannot (EcoBits adjusts a balance and answers nothing, a console line answers nothing at
all), the balance is read first and the take is refused before anything moves.

### Cross-server messaging

`uxmlib-redis` is a lean binary pub/sub primitive for fanning a message across the nodes sharing one Redis,
with no relational dependencies. For cache invalidation tied to the storage stack, `uxmlib-storage` builds
its `DataSynchronizer` on the same idea: a `LocalDataSynchronizer` single-node default that a
`RedisDataSynchronizer` bridges across nodes when Redis is configured.

```java
bus.subscribe("party-updates", frame -> applyUpdate(frame));   // RedisBus: opaque byte[] frames
bus.publish("party-updates", encode(update));                  // fail-degraded, auto-reconnecting
```

### Update checker

Notify-only: it logs to the console and shows a permission-gated message on join. It never self-downloads.

It decides when to speak and never what is said. The sentence is an `UpdateAnnouncement` you supply, and it
is required rather than defaulted: a version notice is one of the few lines a plugin sends that has no entry
in its own message file, so a shipped one would put English, in colours nobody chose, into a plugin that
translates everything else.

```java
UpdateChecker checker = new UpdateChecker(
        scheduler, new GitHubReleaseProvider("you", "your-plugin"), UxmLibVersion.VERSION);

UpdateAnnouncement announcement = (name, current, release) -> messages.render(
                MyKeys.UPDATE_AVAILABLE,
                Text.placeholder("name", name),
                Text.placeholder("current", current),
                Text.placeholder("latest", release.version()))
        .clickEvent(ClickEvent.openUrl(release.url()));

new UpdateNotifier(plugin, scheduler, checker, "yourplugin.update.notify", announcement)
        .start(Duration.ofSeconds(40), Duration.ofHours(6));
```

### Backup participation

A plugin that keeps state in memory has, at any moment, work that a file copy would miss. Register once, and
whatever is about to copy the server's files asks you to save first.

```java
// In your plugin, on enable.
BackupParticipants.register(plugin, () -> profiles.saveAll());

// In a backup tool, before it reads anything. The executor decides which thread a save runs on.
List<String> late = BackupParticipants.prepareAll(Duration.ofSeconds(20), mainThreadExecutor);
```

The contract is a plain `Runnable` on purpose. Every plugin relocates its shaded copy of uxmLib, so an
interface of ours would be a different class in each jar. `Runnable` comes from the boot class loader, so it
is the same type everywhere. Registrations are marked, and an unmarked `Runnable` service is never run.

### Experimental: packet layer

`uxmlib-pipeline`, `uxmlib-packet`, and `uxmlib-nametags` are an in-progress, **clean-room** packet foundation
for the things the public API cannot do per viewer: different nametag colours, tab-list rows, or holograms
for different players. PacketEvents (the off-the-shelf choice) is GPL, so none of it is borrowed; the Netty
plumbing is re-implemented for Paper 26.2+ and the unavoidable NMS is quarantined to single, named classes
behind pure ports built against the Mojang-mapped dev bundle.

Those classes are compiled against the Mojang-mapped server they run on, so every internal they touch is a
checked fact rather than a reflected guess. There is no adapter layer and no per-line seam: one server line
means the compiler covers the whole surface, and a server internal that moves fails the build rather than
someone's server.

One of them is worth calling out on its own, because every plugin that writes something onto an item wants
it. `com.uxplima.uxmlib.packet.item` rewrites the item a *client* is shown and never the item the server
holds. A plugin implements `ItemView`, hands it to `ItemViews`, and the five clientbound packets that carry a
tooltip (the container slot, the container contents, the cursor item, the player-inventory slot, the trade
list) go through it on the way out. The stack in the chest is untouched, so no save can catch a decorated
copy and no second plugin can read one back. The package holds the mechanism only: what the view writes,
lore or a name or nothing at all, is the plugin's.

These modules have **unstable APIs** and parts are still landing. Treat them as a preview; the stable
toolkit above does not depend on them.

## Architecture & quality

- **Package root** `com.uxplima.uxmlib`, one sub-package per concern, every package `@NullMarked`.
- **No upward dependencies.** `common` depends on nothing internal; everything may depend on `common`;
  `gui` depends on `item`; `all` aggregates. ArchUnit tests enforce that there are no cycles.
- **Constructor injection, no static mutable state.** The only `JavaPlugin` is the thin shell in
  `uxmlib-all`; library types are plain objects you construct and inject.
- **Input validation at every public entry**, small methods and classes, no SQL string concatenation, no
  empty catches, no `printStackTrace`.
- **Static analysis as errors.** Error Prone + NullAway (`onlyNullMarked`, JSpecify), Spotless with
  Palantir Java Format, all under `-Werror`.
- **Tests.** JUnit 5, AssertJ, Mockito, MockBukkit, jqwik property tests, and ArchUnit.

## Building from source

Requires a JDK 21 toolchain (Gradle provisions it via the Foojay resolver if needed).

```bash
./gradlew build                   # compile, format check, static analysis, tests
./gradlew spotlessApply           # auto-format before checking
./gradlew :uxmlib-all:shadowJar   # the standalone plugin jar (-standalone classifier)
./gradlew publishToMavenLocal     # install every module to ~/.m2 to try locally
```

## Known gaps

One thing this library does and does not do fully, written here rather than found again:

- **A saved menu keeps its model and loses its file.** `MenuSpecWriter` writes every shape
  `MenuSpecLoader` reads, and its round trip test proves it key by key. What it cannot keep is the
  file the operator typed: a comment is dropped, key order is the writer's own, and a shorthand
  (`fill-item`, `update-interval`, a `pattern` template) comes back as the canonical form of the
  model it produced. A plugin that offers an in-game editor tells its operators to keep their own
  notes somewhere the editor does not rewrite.

## Versioning & stability

The library follows semantic versioning. Public API modules aim for stable names and documented seams; the
**experimental** modules (`uxmlib-pipeline`, `uxmlib-packet`, `uxmlib-nametags`) may change without notice until
they graduate. Pre-1.0 (`0.x`) releases may still adjust APIs between minor versions as the surface settles.

## Contributing

Issues and pull requests are welcome. The workflow is test-first: add a failing test, write the minimum
implementation, run `./gradlew spotlessApply` then `./gradlew build` green before committing. Keep to the
existing conventions: `@NullMarked` packages, constructor injection, no upward module dependencies, and
Adventure/MiniMessage for all text.

## License

[MIT](LICENSE): © UXPLIMA. Use it anywhere, including in closed-source plugins.

## Acknowledgements

uxmLib is an independent, clean-room implementation. Where a permissively licensed project informed an
approach: Triumph GUI and AnvilGUI (menu and anvil-input patterns), Item-NBT-API and Rtag (item data),
Lamp (command annotations), HamsterAPI (the pipeline-injection technique), and FancyHolograms (the
per-viewer text-override approach), it is acknowledged here. No code is copied from any GPL/AGPL or
proprietary source.
