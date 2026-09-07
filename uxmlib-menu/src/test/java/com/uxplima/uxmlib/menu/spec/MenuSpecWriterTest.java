package com.uxplima.uxmlib.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The writer's contract is the loader's grammar read backwards, so almost every test here is the same shape: take a
 * menu file, load it, write the model back out, load that, and assert the two models are equal. Driving each case
 * from a file rather than from a hand-built record is deliberate: it proves the case is one the loader can actually
 * produce, so a green test is evidence about real menus rather than about a record a file could never hold.
 */
class MenuSpecWriterTest {

    private final MenuSpecLoader loader = new MenuSpecLoader();

    private final MenuSpecWriter writer = new MenuSpecWriter();

    /**
     * Load {@code hocon}, write it back, load that, and assert nothing changed on the way round. The written form is
     * then written once more and compared to itself: a model that survives the trip while the file keeps changing
     * would mean the writer normalises something on the second pass, which is a defect the model check cannot see.
     */
    private MenuSpec assertRoundTrips(String hocon) {
        MenuSpec original = loader.parse(hocon);
        String written = writer.write(original);
        MenuSpec reloaded = loader.parse(written);
        assertThat(reloaded).as("round trip through%n%s", written).isEqualTo(original);
        assertThat(writer.write(reloaded))
                .as("the written file is a fixed point")
                .isEqualTo(written);
        return original;
    }

    /** The written form of {@code hocon}, for the few assertions that are about the file text rather than the model. */
    private String writeOf(String hocon) {
        return writer.write(loader.parse(hocon));
    }

    /** The item {@code id} names, which the menu is asserted to hold rather than checked for null at every use. */
    private static MenuItemSpec item(MenuSpec spec, String id) {
        MenuItemSpec found = spec.items().get(id);
        assertThat(found).as("item '%s'", id).isNotNull();
        return java.util.Objects.requireNonNull(found);
    }

    @Test
    void roundTripsTheSmallestMenuThereIs() {
        assertRoundTrips("rows = 1\nitems {}");
    }

    @Test
    void roundTripsTitleRowsAndRefresh() {
        assertRoundTrips("""
                title = "<gold>Shop"
                rows = 4
                refresh { enabled = true, interval-ticks = 20 }
                items { a { slot = 0, material = STONE } }
                """);
    }

    @Test
    void roundTripsARefreshThatIsOffButStillCarriesACadence() {
        MenuSpec spec = assertRoundTrips("rows = 1\nrefresh { enabled = false, interval-ticks = 40 }\nitems {}");

        assertThat(spec.refresh()).isEqualTo(new RefreshSpec(false, 40));
    }

    @Test
    void roundTripsTheUpdateIntervalShorthandThroughItsCanonicalBlock() {
        MenuSpec spec = assertRoundTrips("rows = 1\nupdate-interval = 15\nitems {}");

        assertThat(spec.refresh()).isEqualTo(new RefreshSpec(true, 15));
    }

    @Test
    void roundTripsTheMenuLevelActionLists() {
        assertRoundTrips("""
                rows = 1
                open-requirement = [ "perm:menu.open", "!has-money:100" ]
                open-actions = [ "sound:BLOCK_NOTE_BLOCK_PLING", "message:hello" ]
                close-actions = [ "command:spawn" ]
                items {}
                """);
    }

    @Test
    void roundTripsAnInventoryTypeAndTheChestOnlyFlag() {
        MenuSpec spec = assertRoundTrips(
                "inventory-type = HOPPER\nrows = 1\nchest-only = true\nitems { a { slot = 0, material = STONE } }");

        assertThat(spec.inventoryType()).contains("HOPPER");
        assertThat(spec.chestOnly()).isTrue();
    }

    @Test
    void roundTripsABottomInventoryMenu() {
        MenuSpec spec = assertRoundTrips("bottom-inventory = true\nitems { a { slot = 60, material = STONE } }");

        assertThat(spec.bottomInventory()).isTrue();
        assertThat(spec.rows()).isEqualTo(6);
    }

    @Test
    void roundTripsThePlaceholderBlockAndTheClickCooldown() {
        MenuSpec spec = assertRoundTrips("""
                rows = 1
                click-cooldown = 250
                placeholders { greeting = "hello %player%", tier = "gold" }
                items {}
                """);

        assertThat(spec.placeholders())
                .containsEntry("greeting", "hello %player%")
                .containsEntry("tier", "gold");
        assertThat(spec.clickCooldownMs()).isEqualTo(250L);
    }

    @Test
    void roundTripsAnItemWithEveryPlainField() {
        assertRoundTrips("""
                rows = 2
                items {
                  a { slots = ["0-3", "8"], priority = 7, material = DIAMOND_SWORD, name = "<red>Blade",
                      lore = ["one", "two"], lore-mode = append, update = true }
                }
                """);
    }

    @Test
    void roundTripsAnItemThatClaimsNoSlotAtAll() {
        MenuSpec spec = assertRoundTrips("rows = 1\nitems { a { slots = [], material = STONE } }");

        assertThat(item(spec, "a")).isNotNull();
        assertThat(item(spec, "a").slots().slots()).isEmpty();
    }

    @Test
    void roundTripsEveryLoreMode() {
        for (String token : List.of("replace", "append", "prepend")) {
            assertRoundTrips("rows = 1\nitems { a { slot = 0, material = STONE, lore-mode = " + token + " } }");
        }
    }

    @Test
    void roundTripsEveryItemType() {
        for (String token : List.of("none", "next", "previous", "jump")) {
            assertRoundTrips("rows = 1\nitems { a { slot = 0, material = STONE, type = " + token + " } }");
        }
    }

    @Test
    void roundTripsTheDecorBasics() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, decor {
                  amount = 16, model-data = 4001, glow = true, flags = ["HIDE_ATTRIBUTES", "HIDE_ENCHANTS"] } } }
                """);
    }

    @Test
    void roundTripsTheDynamicDecorTokens() {
        MenuSpec spec = assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, decor {
                  amount = "%stock%", model-data = "%model%", glow = "%is_new%" } } }
                """);

        RichMeta meta = item(spec, "a").decor().meta();
        assertThat(meta.dynamicAmount()).contains("%stock%");
        assertThat(meta.dynamicModelData()).contains("%model%");
        assertThat(meta.dynamicGlow()).contains("%is_new%");
    }

    @Test
    void roundTripsTheRichMeta() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = LEATHER_CHESTPLATE, decor {
                  unbreakable = true
                  enchantments = ["sharpness:5"]
                  stored-enchantments = ["mending:1"]
                  leather-color = "#A1FF33"
                  damage = 100
                  item-model = "minecraft:diamond_sword"
                  potion { type = STRENGTH, color = "#00AAFF", effects = ["speed:1:600"] }
                  banner { patterns = ["stripe_top:red"] }
                  trim { material = diamond, pattern = sentry }
                } } }
                """);
    }

    @Test
    void roundTripsAPotionThatOnlyCarriesEffects() {
        assertRoundTrips(
                "rows = 1\nitems { a { slot = 0, material = POTION, decor { potion { effects = [\"speed:1:600\"] } } } }");
    }

    @Test
    void roundTripsTheDataComponents() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, decor {
                  rarity = EPIC
                  tooltip-style = "minecraft:fancy"
                  hide-tooltip = true
                  hide-vanilla-tooltip = false
                  hidden-components = ["dyed_color", "equippable"]
                  enchant-glint = true
                  enchantable = 10
                  attribute-modifiers = ["generic.attack_damage:5:add_number:hand"]
                  food { nutrition = 4, saturation = 2.5, can-always-eat = true }
                  tool { default-mining-speed = 1.5, damage-per-block = 2 }
                } } }
                """);
    }

    @Test
    void roundTripsAFlatViewList() {
        assertRoundTrips(
                "rows = 1\nitems { a { slot = 0, material = STONE, view = [\"perm:vip\", \"!has-money:100\"] } }");
    }

    @Test
    void roundTripsAViewBlockWithAMinimum() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE,
                  view { requirements = ["has-money:100", "has-money:200"], minimum = 1 } } }
                """);
    }

    @Test
    void roundTripsThePermissionShorthand() {
        assertRoundTrips("rows = 1\nitems { a { slot = 0, material = STONE, permission = \"menu.see\" } }");
    }

    @Test
    void roundTripsThePagesShorthand() {
        MenuSpec spec = assertRoundTrips("rows = 1\nitems { a { slot = 0, material = STONE, pages = \"1-3,5\" } }");

        Requirement only = item(spec, "a").view().requirements().get(0);
        assertThat(only.condition().id()).isEqualTo("on-page");
        assertThat(only.condition().value()).isEqualTo("1-3,5");
    }

    @Test
    void roundTripsBothShorthandsTogetherWithAView() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, view = ["has-money:1"],
                            permission = "menu.see", pages = "2" } }
                """);
    }

    @Test
    void roundTripsEveryClickGesture() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click {
                  left = ["close"]
                  right = ["message:right"]
                  shift-left = ["message:sl"]
                  shift-right = ["message:sr"]
                  middle = ["message:m"]
                  drop = ["message:d"]
                  control-drop = ["message:cd"]
                  double-click = ["message:dc"]
                  any = ["message:any"]
                } } }
                """);
    }

    @Test
    void roundTripsAGestureThatBindsNoActionAtAll() {
        MenuSpec spec = assertRoundTrips("rows = 1\nitems { a { slot = 0, material = STONE, click { left = [] } } }");

        assertThat(item(spec, "a").click().actions()).containsKey(ClickKind.LEFT);
        assertThat(item(spec, "a").click().actionsFor(ClickKind.LEFT)).isEmpty();
    }

    @Test
    void roundTripsAGestureRequirementBlock() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click { left = {
                  actions = ["message:ok"]
                  requirements = ["has-money:100", "!has-empty-slots:1"]
                  minimum = 1
                  deny = ["message:no", "sound:BLOCK_ANVIL_LAND"]
                  stop-at-success = true
                } } } }
                """);
    }

    @Test
    void roundTripsAGestureThatCarriesADenyAndNoRequirements() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click { left = {
                  actions = ["message:ok"], deny = ["message:no"] } } } }
                """);
    }

    @Test
    void roundTripsAMapFormRequirement() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click { left = {
                  actions = ["message:ok"]
                  requirements = [ { require = "!has-money:100", optional = true,
                                     success = ["message:yes"], deny = ["message:no"] } ]
                } } } }
                """);
    }

    @Test
    void roundTripsAnElseLadder() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click { left = {
                  requirements = ["has-money:1000"]
                  actions = ["message:rich"]
                  else = {
                    requirements = ["has-money:100"]
                    actions = ["message:ok"]
                    else = { actions = ["message:broke"] }
                  }
                } } } }
                """);
    }

    @Test
    void roundTripsATerminalElseThatCarriesNothing() {
        MenuSpec spec = assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click { left = {
                  requirements = ["has-money:1"], actions = ["message:ok"], else = {} } } } }
                """);

        assertThat(item(spec, "a").click().elseFor(ClickKind.LEFT)).isPresent();
    }

    @Test
    void roundTripsTheActionModifiers() {
        MenuSpec spec = assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click { left = [
                  { do = "message:soon", delay = 40 },
                  { do = "give-money:100", chance = 25.5, deny = "message:unlucky" }
                ] } } }
                """);

        List<Ref> left = item(spec, "a").click().actionsFor(ClickKind.LEFT);
        assertThat(left.get(0).delayTicks()).isEqualTo(40);
        assertThat(left.get(1).chance()).isEqualTo(25.5);
        assertThat(left.get(1).deny()).isPresent();
    }

    @Test
    void roundTripsAnInputContinuation() {
        MenuSpec spec = assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click { left = [
                  { do = "input:pwarp.rename", prompt = "New name", default = "home",
                    deny = ["message:cancelled", "close"] },
                  "message:renamed to %input%"
                ] } } }
                """);

        Ref first = item(spec, "a").click().actionsFor(ClickKind.LEFT).get(0);
        assertThat(first.continuation()).containsInstanceOf(Continuation.Input.class);
    }

    @Test
    void roundTripsAConfirmContinuation() {
        MenuSpec spec = assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click { left = [
                  { do = "confirm:delete", title = "Are you sure?",
                    yes = ["command:delete", "close"], no = ["message:kept"] }
                ] } } }
                """);

        Ref only = item(spec, "a").click().actionsFor(ClickKind.LEFT).get(0);
        assertThat(only.continuation()).containsInstanceOf(Continuation.Confirm.class);
    }

    @Test
    void roundTripsAContinuationWrittenAsAWholeGesture() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click {
                  right = { do = "confirm:delete", title = "Sure?", yes = ["close"], no = [] } } } }
                """);
    }

    @Test
    void roundTripsAList() {
        assertRoundTrips("""
                rows = 3
                items { a { slots = ["0-8"], material = AIR, list {
                  source = "shop:entries"
                  page-size = 8
                  sorts = ["price", "name"]
                  template { material = "%entry_material%", name = "%entry_name%", lore = ["%entry_price%"],
                             click { left = ["shop:buy"] } }
                } } }
                """);
    }

    @Test
    void roundTripsAListWithNeitherPageSizeNorSorts() {
        assertRoundTrips("""
                rows = 2
                items { a { slots = ["0-8"], material = AIR,
                  list { source = "shop:entries", template { material = STONE } } } }
                """);
    }

    @Test
    void roundTripsTheListControlActions() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click {
                  left = ["list-sort:entries:asc"]
                  right = ["list-filter:entries:tier:gold"]
                  middle = ["list-search:entries:name"]
                } } }
                """);
    }

    @Test
    void roundTripsAnItemDragBlock() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = HOPPER, item-drag {
                  rules { materials = ["DIAMOND", "EMERALD"], min-amount = 4, name-contains = "rare" }
                  consume = true
                  actions = ["message:taken"]
                } } }
                """);
    }

    @Test
    void roundTripsAnItemDragBlockThatCarriesNothingButItsPresence() {
        MenuSpec spec = assertRoundTrips("rows = 1\nitems { a { slot = 0, material = HOPPER, item-drag {} } }");

        assertThat(item(spec, "a").itemDrag()).isPresent();
    }

    @Test
    void roundTripsABedrockFormWithEveryWidget() {
        MenuSpec spec = assertRoundTrips("""
                rows = 1
                items {}
                bedrock {
                  title = "Sell"
                  content = "Pick what to sell."
                  widgets = [
                    { type = label, text = "Your stock" }
                    { type = input, name = amount, label = "How many", placeholder = "0", default = "1" }
                    { type = dropdown, name = tier, label = "Tier", options = ["bronze", "gold"], default = 1 }
                    { type = slider, name = price, label = "Price", min = 1, max = 500, step = 5, default = 50 }
                    { type = toggle, name = notify, label = "Tell me", default = true }
                  ]
                  on-submit = ["shop:sell", "close"]
                }
                """);

        assertThat(spec.bedrock()).isPresent();
        assertThat(spec.bedrock().get().widgets())
                .hasExactlyElementsOfTypes(
                        BedrockWidget.Label.class,
                        BedrockWidget.Input.class,
                        BedrockWidget.Dropdown.class,
                        BedrockWidget.Slider.class,
                        BedrockWidget.Toggle.class);
    }

    @Test
    void roundTripsABedrockFormThatCarriesNothingButItsPresence() {
        MenuSpec spec = assertRoundTrips("rows = 1\nitems {}\nbedrock {}");

        assertThat(spec.bedrock()).isPresent();
        assertThat(spec.bedrock().get().title()).isEmpty();
        assertThat(spec.bedrock().get().content()).isNull();
    }

    @Test
    void roundTripsTheContentRegions() {
        assertRoundTrips("""
                rows = 3
                items {}
                content {
                  grid { slots = ["0-8", "17"], editable = true }
                  preview { slots = ["26"] }
                }
                """);
    }

    @Test
    void roundTripsTheFillItem() {
        MenuSpec spec = assertRoundTrips("""
                rows = 2
                items { a { slot = 4, material = DIAMOND } }
                fill-item { material = GRAY_STAINED_GLASS_PANE, name = " " }
                """);

        assertThat(spec.items()).containsKey("__fill__");
        assertThat(item(spec, "__fill__").slots().slots()).hasSize(17).doesNotContain(4);
    }

    @Test
    void roundTripsAMenuThatUsesEveryShapeAtOnce() {
        assertRoundTrips("""
                title = "<gradient:#ff0000:#00ff00>Everything"
                rows = 6
                click-cooldown = 100
                chest-only = true
                update-interval = 40
                open-requirement = ["perm:everything"]
                open-actions = [ { do = "sound:UI_BUTTON_CLICK", delay = 2, chance = 90.0, deny = "message:quiet" } ]
                close-actions = ["message:bye"]
                placeholders { who = "%player%" }
                content { grid { slots = ["45-53"], editable = true } }
                bedrock { title = "Everything", widgets = [ { type = toggle, name = t, label = "On" } ],
                          on-submit = ["close"] }
                items {
                  header { slots = ["0-8"], material = BLACK_STAINED_GLASS_PANE, name = " ", priority = 1 }
                  buy { slot = 22, material = EMERALD, name = "<green>Buy", lore = ["<gray>%price%"],
                        lore-mode = prepend, permission = "shop.buy", pages = "1-2", update = true,
                        decor { amount = "%stock%", glow = true, unbreakable = true,
                                enchantments = ["unbreaking:3"], rarity = RARE,
                                food { nutrition = 1 }, tool { damage-per-block = 3 } },
                        click {
                          left = { requirements = [ { require = "has-money:100", optional = false,
                                                      deny = ["message:poor"] } ]
                                   minimum = 1, stop-at-success = true
                                   actions = ["shop:buy", { do = "close", delay = 5 }]
                                   deny = ["sound:BLOCK_ANVIL_LAND"]
                                   else = { actions = ["message:nope"] } }
                          right = [ { do = "input:shop.amount", prompt = "How many", default = "1",
                                      deny = ["message:cancelled"] } ]
                          shift-left = []
                        },
                        item-drag { rules { materials = ["DIRT"], min-amount = 2 }, consume = true,
                                    actions = ["message:dropped"] } }
                  page { slot = 53, material = ARROW, type = next }
                  entries { slots = ["9-17"], material = AIR,
                            list { source = "shop:entries", page-size = 9, sorts = ["price"],
                                   template { material = PAPER, name = "%entry_name%" } } }
                }
                fill-item { material = GRAY_STAINED_GLASS_PANE }
                """);
    }

    @Test
    void roundTripsAGestureThatGatesButRunsNothing() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click { left = {
                  requirements = ["has-money:100"], deny = ["message:no"] } } } }
                """);
    }

    @Test
    void roundTripsAnElseThatOnlyCarriesANestedElse() {
        assertRoundTrips("""
                rows = 1
                items { a { slot = 0, material = STONE, click { left = {
                  requirements = ["has-money:1"]
                  actions = ["message:ok"]
                  else = { else = { actions = ["message:last"] } }
                } } } }
                """);
    }

    @Test
    void keepsTheAuthorsSlotOrderRatherThanSortingIt() {
        MenuSpec spec = assertRoundTrips("rows = 1\nitems { a { slots = [\"8\", \"0-2\"], material = STONE } }");

        assertThat(item(spec, "a").slots().slots()).containsExactly(8, 0, 1, 2);
    }

    @Test
    void roundTripsAnItemDragThatNamesOnlySomeOfItsRules() {
        assertRoundTrips(
                "rows = 1\nitems { a { slot = 0, material = HOPPER, item-drag { rules { min-amount = 3 } } } }");
    }

    @Test
    void roundTripsAListWhoseTemplateIsItselfRich() {
        assertRoundTrips("""
                rows = 2
                items { a { slots = ["0-8"], material = AIR, list {
                  source = "shop:entries"
                  template { material = PAPER, name = "%n%", lore = ["%l%"], lore-mode = append,
                             decor { amount = 2, glow = true, rarity = RARE },
                             view = ["perm:see"],
                             click { left = { actions = ["shop:buy"], requirements = ["has-money:1"] } } }
                } } }
                """);
    }

    @Test
    void roundTripsTheMenuTheModuleShips() throws Exception {
        Path bundled = Path.of(
                java.util.Objects.requireNonNull(MenuSpecWriterTest.class.getResource("/menus/bundled-test.conf"))
                        .toURI());

        MenuSpec original = loader.load(bundled);
        assertThat(loader.parse(writer.write(original))).isEqualTo(original);
    }

    @Test
    void writesARefWithNoValueAsABareToken() {
        String written = writeOf("rows = 1\nitems { a { slot = 0, material = STONE, click { left = [\"close\"] } } }");

        assertThat(written).contains("close").doesNotContain("close:");
    }

    @Test
    void writesARefsValueBehindAColon() {
        String written =
                writeOf("rows = 1\nitems { a { slot = 0, material = STONE, click { left = [\"message:hi\"] } } }");

        assertThat(written).contains("message:hi");
    }

    @Test
    void writesANamespacedVerbsValueBehindAColonToo() {
        Ref namespaced = Ref.of("auction:sort", Map.of("value", "newest"));

        assertThat(MenuSpecWriter.refToken(namespaced)).isEqualTo("auction:sort:newest");
    }

    @Test
    void writesANamespacedVerbThatCarriesNoValueWhole() {
        assertThat(MenuSpecWriter.refToken(Ref.parse("warp:teleport"))).isEqualTo("warp:teleport");
    }

    @Test
    void putsTheConsumersHeaderAtTheTopOfTheFile() {
        String header = "# my plugin wrote this\n# do not hand edit\n";
        String written = new MenuSpecWriter(header).write(loader.parse("rows = 1\nitems {}"));

        assertThat(written).startsWith("# my plugin wrote this\n# do not hand edit\n\n");
    }

    @Test
    void stillLoadsAFileThatCarriesAHeader() {
        MenuSpec original = loader.parse("rows = 2\nitems { a { slot = 0, material = STONE } }");
        String written = new MenuSpecWriter("# a header\n").write(original);

        assertThat(loader.parse(written)).isEqualTo(original);
    }

    @Test
    void writesNoHeaderWhenTheConsumerNamesNone() {
        assertThat(writeOf("rows = 1\nitems {}")).doesNotStartWith("#").doesNotStartWith("\n");
    }

    @Test
    void treatsABlankHeaderAsNoHeader() {
        assertThat(new MenuSpecWriter("   \n  \n").write(loader.parse("rows = 1\nitems {}")))
                .doesNotStartWith("\n");
    }

    @Test
    void refusesAHeaderThatIsNotWrittenAsComments() {
        assertThatThrownBy(() -> new MenuSpecWriter("rows = 99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comment");
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert each requireNonNull guard fires
    void refusesANullHeaderSpecOrFile() {
        MenuSpec spec = loader.parse("rows = 1\nitems {}");

        assertThatNullPointerException().isThrownBy(() -> new MenuSpecWriter(null));
        assertThatNullPointerException().isThrownBy(() -> writer.write(null));
        assertThatNullPointerException().isThrownBy(() -> writer.write(spec, null));
    }

    @Test
    void writesToTheFileTheConsumerNamesAndTheLoaderReadsItBack(@TempDir Path folder) {
        MenuSpec original =
                loader.parse("rows = 2\nitems { a { slot = 0, material = STONE, click { left = [\"close\"] } } }");
        Path file = folder.resolve("menus").resolve("shop.conf");

        writer.write(original, file);

        assertThat(file).exists();
        assertThat(loader.load(file)).isEqualTo(original);
    }

    @Test
    void leavesNoTemporaryFileBehind(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("shop.conf");

        writer.write(loader.parse("rows = 1\nitems {}"), file);

        try (var entries = Files.list(folder)) {
            assertThat(entries.map(Path::getFileName).map(Path::toString)).containsExactly("shop.conf");
        }
    }

    @Test
    void writesTheHeaderIntoTheFileToo(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("shop.conf");

        new MenuSpecWriter("# header").write(loader.parse("rows = 1\nitems {}"), file);

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).startsWith("# header");
    }

    @Test
    void refusesARefWhoseArgumentsTheGrammarCannotHold() {
        MenuSpec spec = new MenuSpec(
                "",
                1,
                new RefreshSpec(false, 0),
                List.of(),
                List.of(Ref.of("give", Map.of("item", "DIAMOND"))),
                List.of(),
                Map.of());

        assertThatThrownBy(() -> writer.write(spec))
                .isInstanceOf(MenuSpecException.class)
                .hasMessageContaining("item");
    }

    @Test
    void refusesAPerGestureConditionBecauseNoFileCanHoldOne() {
        ClickSpec click = new ClickSpec(
                Map.of(ClickKind.LEFT, List.of(Ref.parse("close"))),
                Map.of(ClickKind.LEFT, List.of(Ref.parse("perm:x"))));
        MenuItemSpec item = new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                "STONE",
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                LoreMode.REPLACE,
                RequirementSpec.NONE,
                click,
                false,
                Optional.empty(),
                ItemType.NONE);
        MenuSpec spec =
                new MenuSpec("", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of("a", item));

        assertThatThrownBy(() -> writer.write(spec))
                .isInstanceOf(MenuSpecException.class)
                .hasMessageContaining("condition");
    }

    @Test
    void refusesABottomInventoryMenuThatIsNotSixRows() {
        MenuSpec spec = new MenuSpec(
                "",
                3,
                new RefreshSpec(false, 0),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Optional.empty(),
                Map.of(),
                0L,
                true,
                false,
                Optional.empty(),
                Map.of());

        assertThatThrownBy(() -> writer.write(spec))
                .isInstanceOf(MenuSpecException.class)
                .hasMessageContaining("bottom-inventory");
    }

    @Test
    void refusesABottomInventoryMenuThatAlsoNamesAnInventoryType() {
        MenuSpec spec = new MenuSpec(
                "",
                6,
                new RefreshSpec(false, 0),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Optional.of("HOPPER"),
                Map.of(),
                0L,
                true,
                false,
                Optional.empty(),
                Map.of());

        assertThatThrownBy(() -> writer.write(spec))
                .isInstanceOf(MenuSpecException.class)
                .hasMessageContaining("inventory-type");
    }

    @Test
    void writesTheSameBytesEveryTimeForTheSameMenu() {
        String hocon = """
                rows = 2
                items {
                  zebra { slot = 1, material = STONE }
                  apple { slot = 0, material = DIRT }
                }
                placeholders { b = "2", a = "1" }
                """;
        MenuSpec spec = loader.parse(hocon);

        assertThat(writer.write(spec)).isEqualTo(writer.write(spec));
    }
}
