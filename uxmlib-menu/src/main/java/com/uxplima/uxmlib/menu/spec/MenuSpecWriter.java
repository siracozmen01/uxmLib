package com.uxplima.uxmlib.menu.spec;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Turns a {@link MenuSpec} back into the HOCON a {@link MenuSpecLoader} reads, so a plugin can ship an in-game menu
 * editor without carrying a writer of its own. It is the loader's grammar run backwards, and the promise it makes is
 * that a spec written here and loaded again is the same spec: every shape the loader can read, this writes.
 *
 * <p><strong>Model faithful, not byte faithful.</strong> What comes back is the menu, not the file the operator
 * wrote. Comments are lost, key order is the writer's own, and a shorthand the operator used is re-emitted in the
 * canonical form of the model it produced: a {@code fill-item} block comes back as an ordinary item under its
 * {@code __fill__} id with the slots it actually claimed, an {@code update-interval} comes back as the
 * {@code refresh {}} block it means, and an operator's {@code pattern}/{@code vars} template is written out already
 * expanded. A consumer that offers an editor tells its operators to keep their own notes elsewhere.
 *
 * <p><strong>The header is the consumer's, never the library's.</strong> A menu file that teaches its own grammar in
 * a comment block at the top is a good idea, and the words in it belong to the plugin that owns the file: they name
 * its commands, its folder, and its own conventions. So the header arrives through the constructor as verbatim text
 * and this class decides nothing about it beyond refusing text that would not parse as a comment. A writer built with
 * no header writes no header.
 *
 * <p><strong>Fail fast rather than lose something quietly.</strong> A handful of {@link MenuSpec} shapes are
 * constructible in Java and have no spelling in the file grammar: a {@link Ref} carrying an argument other than
 * {@code value}, a per-gesture entry in {@link ClickSpec#conditions()}, and a {@code bottom-inventory} menu that is
 * not six rows or that also names an inventory type. Writing those would drop something on the floor, so each is a
 * {@link MenuSpecException} naming what could not be written. Nothing else here can lose a field.
 *
 * <p>Pure like the rest of this package: no Bukkit, no server type, and no path except the one the caller hands over.
 */
public final class MenuSpecWriter {

    /** The spelling each gesture is written under, one per {@link ClickKind}, all in the kebab form the loader reads. */
    private static final Map<ClickKind, String> GESTURE_KEYS = gestureKeys();

    /** The default a written {@code chance} is measured against: a ref at full chance carries no modifier at all. */
    private static final double FULL_CHANCE = 100.0;

    /** The header text put above the first key, already terminated, or empty when the consumer named none. */
    private final String header;

    /** A writer that emits no header: the menu and nothing else. */
    public MenuSpecWriter() {
        this("");
    }

    /**
     * A writer that opens every file it writes with {@code header}.
     *
     * <p>The text is used verbatim, so the consumer owns every word of it, and it is checked rather than escaped:
     * every non-blank line must already begin with {@code #} or {@code //}, HOCON's two comment markers. A library
     * that silently commented out a caller's text would be deciding what the file says, and one that passed it
     * through unchecked would write a file that no longer loads. A blank or empty header means no header.
     *
     * @param header the comment block to put at the top of each written file, never {@code null}
     * @throws IllegalArgumentException when a non-blank line of {@code header} is not a comment
     */
    public MenuSpecWriter(String header) {
        Objects.requireNonNull(header, "header");
        checkIsComment(header);
        this.header = header.isBlank() ? "" : header.stripTrailing() + "\n\n";
    }

    /**
     * Render {@code spec} as the HOCON a {@link MenuSpecLoader} parses back into an equal spec.
     *
     * @param spec the menu to write, never {@code null}
     * @return the whole file, header included, ready to hand to a writer or to parse again
     * @throws MenuSpecException when the spec holds a shape the file grammar cannot express
     */
    public String write(MenuSpec spec) {
        Objects.requireNonNull(spec, "spec");
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        try {
            writeMenu(root, spec);
        } catch (SerializationException failure) {
            throw new MenuSpecException("failed to build the menu spec node", failure);
        }
        return header + render(root);
    }

    /**
     * Write {@code spec} to {@code file}, the caller's own path and the only one this class touches beyond the
     * sibling {@code .tmp} it renames from and the parent directory it creates.
     *
     * <p>The render lands in that sibling first and is then moved into place, atomically where the filesystem allows
     * it, so a process killed mid-write leaves the operator's previous menu intact rather than half a file. The call
     * blocks on disk IO: a consumer driving it from a menu click runs it off the main thread through its scheduler.
     *
     * @param spec the menu to write, never {@code null}
     * @param file the file to write it to, never {@code null}
     * @throws MenuSpecException when the spec cannot be expressed, or the write fails
     */
    public void write(MenuSpec spec, Path file) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(file, "file");
        String rendered = write(spec);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temp, rendered, StandardCharsets.UTF_8);
            moveIntoPlace(temp, file);
        } catch (IOException failure) {
            deleteQuietly(temp);
            throw new MenuSpecException("failed to write menu spec " + file, failure);
        }
    }

    // The menu

    private void writeMenu(CommentedConfigurationNode root, MenuSpec spec) throws SerializationException {
        checkWindowIsWritable(spec);
        setIfNotBlank(root.node("title"), spec.title());
        root.node("rows").set(spec.rows());
        if (spec.inventoryType().isPresent()) {
            root.node("inventory-type").set(spec.inventoryType().get());
        }
        setIfTrue(root.node("bottom-inventory"), spec.bottomInventory());
        setIfTrue(root.node("chest-only"), spec.chestOnly());
        if (spec.clickCooldownMs() != 0L) {
            root.node("click-cooldown").set(spec.clickCooldownMs());
        }
        writeRefresh(root, spec.refresh());
        writeRefsIfAny(root.node("open-requirement"), spec.openRequirement());
        writeRefsIfAny(root.node("open-actions"), spec.openActions());
        writeRefsIfAny(root.node("close-actions"), spec.closeActions());
        writeStringMap(root.node("placeholders"), spec.placeholders());
        writeContent(root.node("content"), spec.contents());
        if (spec.bedrock().isPresent()) {
            writeBedrock(root.node("bedrock"), spec.bedrock().get());
        }
        writeItems(root.node("items"), spec.items());
    }

    /**
     * Refuse the two window shapes a file cannot hold. The loader pins a {@code bottom-inventory} menu at six rows
     * and drops any inventory type it also names, because the raw-slot geometry that paints into the viewer's own
     * inventory only lines up for a full double chest. A spec built in Java can say otherwise, and writing it would
     * hand back a different menu on the next load, so it is refused here instead.
     */
    private static void checkWindowIsWritable(MenuSpec spec) {
        if (!spec.bottomInventory()) {
            return;
        }
        if (spec.rows() != 6) {
            throw new MenuSpecException(
                    "a bottom-inventory menu is always six rows, so " + spec.rows() + " cannot be written");
        }
        if (spec.inventoryType().isPresent()) {
            throw new MenuSpecException("a bottom-inventory menu is chest-only, so it cannot carry an inventory-type");
        }
    }

    /** The refresh policy, written as its canonical block; a menu that never refreshes writes no block at all. */
    private void writeRefresh(CommentedConfigurationNode root, RefreshSpec refresh) throws SerializationException {
        if (!refresh.enabled() && refresh.intervalTicks() == 0) {
            return;
        }
        root.node("refresh", "enabled").set(refresh.enabled());
        root.node("refresh", "interval-ticks").set(refresh.intervalTicks());
    }

    /** The {@code content {}} regions, sorted by id so two writes of one menu produce the same bytes. */
    private void writeContent(CommentedConfigurationNode node, Map<String, ContentRegionSpec> contents)
            throws SerializationException {
        for (Map.Entry<String, ContentRegionSpec> entry : sorted(contents).entrySet()) {
            CommentedConfigurationNode region = node.node(entry.getKey());
            region.node("slots")
                    .setList(String.class, slotTokens(entry.getValue().slots()));
            setIfTrue(region.node("editable"), entry.getValue().editable());
        }
    }

    /** The items, sorted by id. The fill item is written as the ordinary item it became, see the class javadoc. */
    private void writeItems(CommentedConfigurationNode node, Map<String, MenuItemSpec> items)
            throws SerializationException {
        for (Map.Entry<String, MenuItemSpec> entry : sorted(items).entrySet()) {
            writeItem(node.node(entry.getKey()), entry.getValue());
        }
    }

    // The item

    private void writeItem(CommentedConfigurationNode node, MenuItemSpec item) throws SerializationException {
        // Always written, never omitted: an item with no `slots` key takes the `slot` default of zero, so a
        // slotless item that skipped the key would come back holding slot 0.
        node.node("slots").setList(String.class, slotTokens(item.slots()));
        if (item.priority() != 0) {
            node.node("priority").set(item.priority());
        }
        node.node("material").set(item.material());
        setIfNotBlank(node.node("name"), item.name());
        writeStringsIfAny(node.node("lore"), item.lore());
        if (item.loreMode() != LoreMode.REPLACE) {
            node.node("lore-mode").set(token(item.loreMode().name()));
        }
        if (item.type() != ItemType.NONE) {
            node.node("type").set(token(item.type().name()));
        }
        setIfTrue(node.node("update"), item.update());
        writeDecor(node.node("decor"), item.decor());
        writeView(node, item.view());
        writeClick(node.node("click"), item.click());
        if (item.list().isPresent()) {
            writeList(node.node("list"), item.list().get());
        }
        if (item.itemDrag().isPresent()) {
            writeItemDrag(node.node("item-drag"), item.itemDrag().get());
        }
    }

    /**
     * The item's slots as the compact tokens the loader parses, one index per token. Ranges are not collapsed on
     * purpose: {@link SlotSet} keeps its first-seen order and an author's order decides the fill sequence of a list,
     * so writing {@code "0-8"} for a set the operator wrote as {@code ["8", "0-7"]} would silently reorder it.
     */
    private static List<String> slotTokens(SlotSet slots) {
        return slots.slots().stream().map(String::valueOf).toList();
    }

    // The decoration

    private void writeDecor(CommentedConfigurationNode node, ItemDecor decor) throws SerializationException {
        RichMeta meta = decor.meta();
        writeAmount(node, decor, meta);
        writeModelData(node, decor, meta);
        writeGlow(node, decor, meta);
        writeStringsIfAny(node.node("flags"), decor.flagTokens());
        setIfTrue(node.node("unbreakable"), meta.unbreakable());
        writeStringsIfAny(node.node("enchantments"), meta.enchantments());
        writeStringsIfAny(node.node("stored-enchantments"), meta.storedEnchantments());
        setIfPresent(node.node("leather-color"), meta.leatherColor());
        writePotion(node.node("potion"), meta.potion());
        writeStringsIfAny(node.node("banner", "patterns"), meta.bannerPatterns());
        if (meta.trim().isPresent()) {
            node.node("trim", "material").set(meta.trim().get().material());
            node.node("trim", "pattern").set(meta.trim().get().pattern());
        }
        setIfPresent(node.node("damage"), meta.damage());
        setIfPresent(node.node("item-model"), meta.itemModel());
        writeComponents(node, meta.components());
    }

    /**
     * The stack size. A dynamic {@code %token%} wins, because the loader that read one pinned the static amount to
     * one and kept the token as the value the renderer resolves per draw; otherwise the static amount is written,
     * and only when it is not the default of one.
     */
    private void writeAmount(CommentedConfigurationNode node, ItemDecor decor, RichMeta meta)
            throws SerializationException {
        if (meta.dynamicAmount().isPresent()) {
            node.node("amount").set(meta.dynamicAmount().get());
        } else if (decor.amount() != 1) {
            node.node("amount").set(decor.amount());
        }
    }

    /** The custom model data, the dynamic token winning over the static value for the same reason as the amount. */
    private void writeModelData(CommentedConfigurationNode node, ItemDecor decor, RichMeta meta)
            throws SerializationException {
        if (meta.dynamicModelData().isPresent()) {
            node.node("model-data").set(meta.dynamicModelData().get());
        } else if (decor.modelData().isPresent()) {
            node.node("model-data").set(decor.modelData().get());
        }
    }

    /** The enchantment glow, the dynamic token winning: reading one leaves the static flag false by definition. */
    private void writeGlow(CommentedConfigurationNode node, ItemDecor decor, RichMeta meta)
            throws SerializationException {
        if (meta.dynamicGlow().isPresent()) {
            node.node("glow").set(meta.dynamicGlow().get());
        } else {
            setIfTrue(node.node("glow"), decor.glow());
        }
    }

    private void writePotion(CommentedConfigurationNode node, RichMeta.PotionSpec potion)
            throws SerializationException {
        if (potion.equals(RichMeta.PotionSpec.NONE)) {
            return;
        }
        setIfPresent(node.node("type"), potion.type());
        setIfPresent(node.node("color"), potion.color());
        writeStringsIfAny(node.node("effects"), potion.effects());
    }

    /**
     * The native data components. They are children of {@code decor} itself rather than of a block of their own,
     * because that is where the loader reads them from.
     */
    private void writeComponents(CommentedConfigurationNode node, DataComponents components)
            throws SerializationException {
        setIfPresent(node.node("rarity"), components.rarity());
        setIfPresent(node.node("tooltip-style"), components.tooltipStyle());
        setIfPresent(node.node("hide-tooltip"), components.hideTooltip());
        setIfPresent(node.node("hide-vanilla-tooltip"), components.hideVanillaTooltip());
        writeStringsIfAny(node.node("hidden-components"), components.hiddenComponents());
        setIfPresent(node.node("enchant-glint"), components.enchantGlint());
        setIfPresent(node.node("enchantable"), components.enchantable());
        writeStringsIfAny(node.node("attribute-modifiers"), components.attributeModifiers());
        if (components.food().isPresent()) {
            DataComponents.FoodSpec food = components.food().get();
            CommentedConfigurationNode block = node.node("food");
            setIfPresent(block.node("nutrition"), food.nutrition());
            setIfPresent(block.node("saturation"), food.saturation());
            setIfPresent(block.node("can-always-eat"), food.canAlwaysEat());
            forceMap(block, "nutrition");
        }
        if (components.tool().isPresent()) {
            DataComponents.ToolSpec tool = components.tool().get();
            CommentedConfigurationNode block = node.node("tool");
            setIfPresent(block.node("default-mining-speed"), tool.defaultMiningSpeed());
            setIfPresent(block.node("damage-per-block"), tool.damagePerBlock());
            forceMap(block, "damage-per-block");
        }
    }

    // The view gate

    /**
     * The item's visibility gate. The {@code pages} shorthand is written back as the shorthand rather than as a view
     * entry, and that is a correctness point rather than a nicety: the loader folds {@code pages = "1-3"} into a ref
     * whose id is {@code on-page} and whose value is the ranges, and {@code on-page} is not one of the generic verbs
     * {@link Ref#parse} splits at a colon, so the same ref written as the token {@code on-page:1-3} would come back
     * as one whole id and the gate would name a condition nothing has registered. The shorthand is appended by the
     * loader after the view block is read, so writing it as a sibling key restores it in the same position.
     *
     * <p>The {@code permission} shorthand needs no such treatment: {@code perm} is a generic verb, so its ref
     * survives the round trip inside the view list and is written there.
     */
    private void writeView(CommentedConfigurationNode itemNode, RequirementSpec view) throws SerializationException {
        if (view.equals(RequirementSpec.NONE)) {
            return;
        }
        List<Requirement> requirements = view.requirements();
        int end = requirements.size();
        if (end > 0 && isPagesShorthand(requirements.get(end - 1))) {
            itemNode.node("pages").set(requirements.get(end - 1).condition().value());
            end--;
        }
        List<Requirement> remaining = requirements.subList(0, end);
        CommentedConfigurationNode block = itemNode.node("view");
        writeRequirementsIfAny(block.node("requirements"), remaining);
        if (view.minimum() != 0) {
            block.node("minimum").set(view.minimum());
        }
    }

    /** Whether a view requirement is exactly what the {@code pages} shorthand builds, down to its empty branches. */
    private static boolean isPagesShorthand(Requirement requirement) {
        return !requirement.inverted()
                && !requirement.optional()
                && requirement.success().isEmpty()
                && requirement.deny().isEmpty()
                && requirement.condition().id().equals("on-page")
                && requirement.condition().args().keySet().equals(java.util.Set.of("value"));
    }

    // The click block

    private void writeClick(CommentedConfigurationNode node, ClickSpec click) throws SerializationException {
        checkNoGestureConditions(click);
        for (ClickKind kind : ClickKind.values()) {
            @Nullable List<Ref> actions = click.actions().get(kind);
            @Nullable RequirementSpec requirement = click.requirements().get(kind);
            @Nullable ClickBranch orElse = click.orElse().get(kind);
            if (actions == null && requirement == null && orElse == null) {
                continue;
            }
            writeGesture(node.node(GESTURE_KEYS.get(kind)), actions == null ? List.of() : actions, requirement, orElse);
        }
    }

    /**
     * Refuse a per-gesture condition list. {@link ClickSpec} carries the map, but {@link MenuSpecLoader} builds every
     * click block with it empty and no key in the grammar fills it: visibility is gated by the item's {@code view}
     * instead. Writing a spec that holds one would drop it, so it is named here rather than lost.
     */
    private static void checkNoGestureConditions(ClickSpec click) {
        for (Map.Entry<ClickKind, List<Ref>> entry : click.conditions().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                throw new MenuSpecException("a menu file has no key for a per-gesture condition list, so the "
                        + token(entry.getKey().name()) + " condition on this item cannot be written");
            }
        }
    }

    /**
     * One gesture. With no requirement block and no else-chain it is the bare action list the historic grammar uses,
     * written even when empty so a gesture that binds nothing keeps its key; otherwise it is the map form, whose
     * actions live under {@code actions} rather than under a bare {@code do}, because a map whose {@code do} names an
     * {@code input:} or {@code confirm:} step is read as a continuation instead of as a requirement block.
     */
    private void writeGesture(
            CommentedConfigurationNode node,
            List<Ref> actions,
            @Nullable RequirementSpec requirement,
            @Nullable ClickBranch orElse)
            throws SerializationException {
        if (requirement == null && orElse == null) {
            writeRefs(node, actions);
            return;
        }
        writeRefsIfAny(node.node("actions"), actions);
        if (requirement != null) {
            writeRequirementBlock(node, requirement);
        }
        if (orElse != null) {
            writeElse(node.node("else"), orElse);
        }
    }

    /** One rung of the else-ladder, recursing into its own fallback so a chain of any depth is written whole. */
    private void writeElse(CommentedConfigurationNode node, ClickBranch branch) throws SerializationException {
        writeRequirementBlock(node, branch.requirement());
        writeRefsIfAny(node.node("actions"), branch.actions());
        if (branch.orElse().isPresent()) {
            writeElse(node.node("else"), branch.orElse().get());
        }
        // A terminal else that gates on nothing and runs nothing is still a branch, and it is only a branch to the
        // loader while the node is a map, so it is given the empty action list it means.
        forceMap(node, "actions");
    }

    /** The {@code requirements}/{@code minimum}/{@code deny}/{@code stop-at-success} keys shared by every gate. */
    private void writeRequirementBlock(CommentedConfigurationNode node, RequirementSpec spec)
            throws SerializationException {
        writeRequirementsIfAny(node.node("requirements"), spec.requirements());
        if (spec.minimum() != 0) {
            node.node("minimum").set(spec.minimum());
        }
        writeRefsIfAny(node.node("deny"), spec.deny());
        setIfTrue(node.node("stop-at-success"), spec.stopAtSuccess());
    }

    private void writeRequirementsIfAny(CommentedConfigurationNode node, List<Requirement> requirements)
            throws SerializationException {
        for (Requirement requirement : requirements) {
            writeRequirement(node.appendListNode(), requirement);
        }
    }

    /**
     * One requirement: the compact token when it carries nothing but its condition, and the map form as soon as it
     * is optional or brings its own success or deny list. A leading {@code !} negates it in both forms.
     */
    private void writeRequirement(CommentedConfigurationNode entry, Requirement requirement)
            throws SerializationException {
        String token = (requirement.inverted() ? "!" : "") + refToken(requirement.condition());
        if (!requirement.optional()
                && requirement.success().isEmpty()
                && requirement.deny().isEmpty()) {
            entry.set(token);
            return;
        }
        entry.node("require").set(token);
        setIfTrue(entry.node("optional"), requirement.optional());
        writeRefsIfAny(entry.node("success"), requirement.success());
        writeRefsIfAny(entry.node("deny"), requirement.deny());
    }

    // The list, the drag target and the Bedrock form

    private void writeList(CommentedConfigurationNode node, ListSpec list) throws SerializationException {
        node.node("source").set(refToken(list.source()));
        if (list.pageSize() != 0) {
            node.node("page-size").set(list.pageSize());
        }
        writeStringsIfAny(node.node("sorts"), list.sorts());
        writeItem(node.node("template"), list.template());
    }

    private void writeItemDrag(CommentedConfigurationNode node, ItemDragSpec drag) throws SerializationException {
        ItemRuleSpec rules = drag.rules();
        writeStringsIfAny(node.node("rules", "materials"), rules.materials());
        if (rules.minAmount() != 1) {
            node.node("rules", "min-amount").set(rules.minAmount());
        }
        setIfNotBlank(node.node("rules", "name-contains"), rules.nameContains());
        setIfTrue(node.node("consume"), drag.consume());
        writeRefsIfAny(node.node("actions"), drag.actions());
        // An item-drag block that names no rule and runs no action still turns the item into a drag target, and the
        // loader only sees that from the block being there at all.
        forceMap(node, "consume");
    }

    private void writeBedrock(CommentedConfigurationNode node, BedrockFormSpec bedrock) throws SerializationException {
        // Written even when empty: the block's presence is what tells the loader this menu overrides the automatic
        // Bedrock degradation, and `title` is the one key it always reads.
        node.node("title").set(bedrock.title());
        setIfPresent(node.node("content"), bedrock.content());
        for (BedrockWidget widget : bedrock.widgets()) {
            writeWidget(node.node("widgets").appendListNode(), widget);
        }
        writeRefsIfAny(node.node("on-submit"), bedrock.onSubmit());
    }

    private void writeWidget(CommentedConfigurationNode node, BedrockWidget widget) throws SerializationException {
        switch (widget) {
            case BedrockWidget.Label label -> {
                node.node("type").set("label");
                node.node("text").set(label.text());
            }
            case BedrockWidget.Input input -> {
                node.node("type").set("input");
                node.node("name").set(input.name());
                node.node("label").set(input.label());
                node.node("placeholder").set(input.placeholder());
                node.node("default").set(input.defaultText());
            }
            case BedrockWidget.Dropdown dropdown -> {
                node.node("type").set("dropdown");
                node.node("name").set(dropdown.name());
                node.node("label").set(dropdown.label());
                node.node("options").setList(String.class, dropdown.options());
                node.node("default").set(dropdown.defaultIndex());
            }
            case BedrockWidget.Slider slider -> {
                node.node("type").set("slider");
                node.node("name").set(slider.name());
                node.node("label").set(slider.label());
                node.node("min").set(slider.min());
                node.node("max").set(slider.max());
                node.node("step").set(slider.step());
                node.node("default").set(slider.defaultValue());
            }
            case BedrockWidget.Toggle toggle -> {
                node.node("type").set("toggle");
                node.node("name").set(toggle.name());
                node.node("label").set(toggle.label());
                node.node("default").set(toggle.defaultValue());
            }
        }
    }

    // Refs

    private void writeRefsIfAny(CommentedConfigurationNode node, List<Ref> refs) throws SerializationException {
        if (refs.isEmpty()) {
            return;
        }
        writeRefs(node, refs);
    }

    private void writeRefs(CommentedConfigurationNode node, List<Ref> refs) throws SerializationException {
        node.setList(String.class, List.of());
        for (Ref ref : refs) {
            writeRef(node.appendListNode(), ref);
        }
    }

    /**
     * One action. A ref that carries no modifier and no continuation is the bare token the grammar started with; a
     * delay, a chance or a deny fallback promotes it to the map form; an {@code input:} or {@code confirm:} step
     * writes the keys its continuation carries instead, which is where the modifiers would have gone and is why the
     * loader ignores them there.
     */
    private void writeRef(CommentedConfigurationNode entry, Ref ref) throws SerializationException {
        String token = refToken(ref);
        if (ref.continuation().isPresent()) {
            entry.node("do").set(token);
            writeContinuation(entry, ref.continuation().get());
            return;
        }
        if (ref.delayTicks() == 0 && ref.chance() == FULL_CHANCE && ref.deny().isEmpty()) {
            entry.set(token);
            return;
        }
        entry.node("do").set(token);
        if (ref.delayTicks() != 0) {
            entry.node("delay").set(ref.delayTicks());
        }
        if (ref.chance() != FULL_CHANCE) {
            entry.node("chance").set(ref.chance());
        }
        if (ref.deny().isPresent()) {
            entry.node("deny").set(refToken(ref.deny().get()));
        }
    }

    private void writeContinuation(CommentedConfigurationNode entry, Continuation continuation)
            throws SerializationException {
        switch (continuation) {
            case Continuation.Input input -> {
                setIfNotBlank(entry.node("prompt"), input.prompt());
                setIfNotBlank(entry.node("default"), input.defaultText());
                writeRefsIfAny(entry.node("deny"), input.onCancel());
            }
            case Continuation.Confirm confirm -> {
                setIfNotBlank(entry.node("title"), confirm.title());
                writeRefsIfAny(entry.node("yes"), confirm.onYes());
                writeRefsIfAny(entry.node("no"), confirm.onNo());
            }
        }
    }

    /**
     * The compact token a ref is written as: its id alone, or {@code id:value} when it carries a value. A namespaced
     * verb puts its value behind a colon exactly like a generic one does, so {@code auction:sort} carrying
     * {@code newest} is written {@code auction:sort:newest}. {@link Ref#parse} is registry blind and reads that back
     * as one whole id, and {@link Ref#resolve} is what splits it again once a registry can say which head is a
     * verb, so the value survives the round trip through the file and arrives whole at the runtime.
     *
     * @throws MenuSpecException when the ref carries an argument the grammar has no place for
     */
    static String refToken(Ref ref) {
        Map<String, String> args = ref.args();
        if (args.isEmpty()) {
            return ref.id();
        }
        for (String key : args.keySet()) {
            if (!key.equals("value")) {
                throw new MenuSpecException("a menu file carries one value per ref, so the argument '" + key
                        + "' of the ref '" + ref.id() + "' cannot be written");
            }
        }
        return ref.id() + ":" + args.get("value");
    }

    // Small helpers

    /** Render the built node to HOCON text. */
    private static String render(ConfigurationNode root) {
        StringWriter text = new StringWriter();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .sink(() -> new BufferedWriter(text))
                .build();
        try {
            loader.save(root);
        } catch (ConfigurateException failure) {
            throw new MenuSpecException("failed to render the menu spec", failure);
        }
        return text.toString();
    }

    /** Every non-blank line of a header has to be a comment, or the file it opens would not load. */
    private static void checkIsComment(String header) {
        for (String line : header.split("\n", -1)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("//")) {
                throw new IllegalArgumentException(
                        "a menu header is a comment block, so every line starts with # or //: " + line);
            }
        }
    }

    /** Give a node that would otherwise be empty an empty list at {@code key}, so it renders as a map and reloads. */
    private static void forceMap(CommentedConfigurationNode node, String key) throws SerializationException {
        if (node.empty()) {
            node.node(key).setList(String.class, List.of());
        }
    }

    private static void setIfTrue(CommentedConfigurationNode node, boolean value) throws SerializationException {
        if (value) {
            node.set(true);
        }
    }

    private static void setIfNotBlank(CommentedConfigurationNode node, String value) throws SerializationException {
        if (!value.isEmpty()) {
            node.set(value);
        }
    }

    private static void setIfPresent(CommentedConfigurationNode node, Optional<?> value) throws SerializationException {
        if (value.isPresent()) {
            node.set(value.get());
        }
    }

    private static void setIfPresent(CommentedConfigurationNode node, @Nullable String value)
            throws SerializationException {
        if (value != null) {
            node.set(value);
        }
    }

    private static void writeStringsIfAny(CommentedConfigurationNode node, List<String> values)
            throws SerializationException {
        if (!values.isEmpty()) {
            node.setList(String.class, values);
        }
    }

    private void writeStringMap(CommentedConfigurationNode node, Map<String, String> values)
            throws SerializationException {
        for (Map.Entry<String, String> entry : sorted(values).entrySet()) {
            node.node(entry.getKey()).set(entry.getValue());
        }
    }

    /** A menu's maps come out of {@code Map.copyOf}, whose order is not stable, so the file's order is chosen here. */
    private static <T> Map<String, T> sorted(Map<String, T> values) {
        return new TreeMap<>(values);
    }

    /** The lower-case spelling of an enum constant, which is how the loader's tokens are written. */
    private static String token(String constant) {
        return constant.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException noAtomic) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupFailure) {
            // The write already failed and is being reported; a leftover temp file is not worth a second failure.
        }
    }

    private static Map<ClickKind, String> gestureKeys() {
        Map<ClickKind, String> keys = new EnumMap<>(ClickKind.class);
        keys.put(ClickKind.LEFT, "left");
        keys.put(ClickKind.RIGHT, "right");
        keys.put(ClickKind.SHIFT_LEFT, "shift-left");
        keys.put(ClickKind.SHIFT_RIGHT, "shift-right");
        keys.put(ClickKind.MIDDLE, "middle");
        keys.put(ClickKind.DROP, "drop");
        keys.put(ClickKind.CONTROL_DROP, "control-drop");
        keys.put(ClickKind.DOUBLE_CLICK, "double-click");
        keys.put(ClickKind.ANY, "any");
        return Map.copyOf(keys);
    }
}
