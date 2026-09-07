package com.uxplima.uxmlib.menu.render;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.gui.style.Tiles;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.Tooltips;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.eval.ExpressionException;
import com.uxplima.uxmlib.menu.eval.Expressions;
import com.uxplima.uxmlib.menu.providers.IconProviders;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.DataComponents;
import com.uxplima.uxmlib.menu.spec.ItemDecor;
import com.uxplima.uxmlib.menu.spec.LoreMode;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.RichMeta;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Resolves one {@link MenuItemSpec} into the {@link ItemStack} a viewer sees. The spec carries only raw text (a {@code
 * @key}, an inline literal, or a {@code %placeholder%}) so this is where those forms turn into a concrete material, an
 * Adventure name and lore, and the cosmetic decor. Material placeholders expand to a material name; name/lore
 * placeholders expand inline before the text is rendered or looked up in the catalog. Unknown materials fall back to
 * {@link Material#STONE} rather than failing a render, so one bad spec line never blanks a whole menu.
 */
@NullMarked
public final class ItemRenderer {

    private static final Logger LOG = Logger.getLogger(ItemRenderer.class.getName());

    /**
     * The placeholder ids whose handler has already been reported as throwing. An item with {@code update = true}
     * re-renders every tick, so one broken handler would otherwise write a line per tick, and the thousandth says
     * nothing the first did not. Held per renderer, which is per plugin, and bounded by the number of distinct
     * tokens that fail rather than by the number of draws.
     */
    private final Set<String> reportedBadHandlers = ConcurrentHashMap.newKeySet();

    /**
     * The operator tokens already named on the console because nothing could be read out of them, keyed by the kind
     * they were being read as. Same reason and same bound as {@link #reportedBadHandlers}: an item with {@code update
     * = true} redraws every tick, so a misspelling is named once per renderer rather than once per draw.
     */
    private final Set<String> reportedUnreadableTokens = ConcurrentHashMap.newKeySet();

    /** A single {@code %token%} placeholder. {@code group(1)} is the bare token name. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\w+)%");

    /**
     * A {@code {math: <expr>}} block evaluated after the {@code %token%} pass; {@code group(1)} is the inner
     * expression. The {@code math:} head and the brace-free body distinguish it from a catalog {@code {token}}
     * argument, which carries neither a colon nor whitespace, so the two brace grammars never collide.
     */
    private static final Pattern MATH = Pattern.compile("\\{math:\\s*([^{}]*)\\}");

    /** Prefix marking a token as a typed command argument resolved from the open context rather than the registry. */
    private static final String ARGUMENT_PREFIX = "argument_";

    /** The deepest a menu-local placeholder template may nest before expansion stops; a cycle simply hits this. */
    private static final int MAX_LOCAL_DEPTH = 8;

    /**
     * Per-render recursion depth for menu-local placeholder expansion. A render is synchronous on one region/entity
     * thread, so a thread-local counter isolates concurrent renders on different threads and needs no lock. It is
     * incremented on each {@link #substituteLocal} entry and decremented in a {@code finally}, so a template that
     * throws still unwinds the count. This mirrors the global {@code CustomPlaceholders} guard.
     */
    private static final ThreadLocal<int[]> LOCAL_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    /** Default custom-effect duration in ticks when a {@code effect:amplifier} token omits the duration. */
    private static final int DEFAULT_EFFECT_TICKS = 600;

    private final GuiText guiText;

    /**
     * Where the look comes from, asked for on every render rather than held. A theme is a file an operator edits, so
     * a renderer that captured one at construction would paint the old colours until the server restarted.
     */
    private final Supplier<Theme> theme;

    private final PlaceholderRegistry placeholders;
    private final IconProviders iconProviders;

    /**
     * The short form. It renders with the {@link IconProviders#defaults() default} icon chain, which is skull
     * sources and viewer equipment, so a menu gains those for free. A HeadDatabase icon needs a hook the caller
     * supplies, so it is only reachable through the long form.
     */
    public ItemRenderer(GuiText guiText, Supplier<Theme> theme, PlaceholderRegistry placeholders) {
        this(guiText, theme, placeholders, IconProviders.defaults());
    }

    /**
     * The long form: the same renderer plus an explicit {@link IconProviders} chain, which a caller builds with
     * HeadDatabase wired in so {@code hdb:<id>} resolves, and which degrades to a plain head when HeadDatabase is
     * absent.
     */
    public ItemRenderer(
            GuiText guiText, Supplier<Theme> theme, PlaceholderRegistry placeholders, IconProviders iconProviders) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
        this.iconProviders = Objects.requireNonNull(iconProviders, "iconProviders");
    }

    /**
     * Resolve a menu's raw title string for {@code ctx} the same way an item name resolves: every {@code %token%}
     * is substituted, then a {@code @key} title is looked up in the viewer's catalog with its {@code {token}}
     * arguments filled from the same placeholders. This lets a subject-driven title (a home's label, a warp's name)
     * render through the open context rather than being frozen to a static key. A blank title yields an empty
     * component so a spec may still open titleless.
     */
    public Component title(String rawTitle, MenuContext ctx) {
        Objects.requireNonNull(rawTitle, "rawTitle");
        Objects.requireNonNull(ctx, "ctx");
        return resolveText(rawTitle, ctx);
    }

    /**
     * The item's resolved display name followed by its lore, as one flat label: what the hybrid form renderer shows on
     * the button that stands in for this item. A Bedrock SimpleForm button carries a single {@code \n}-separated string
     * rather than a rich icon, so the name and each spec lore line are put through the same {@code %token%}/{@code
     * @key} resolution {@link #render} uses, flattened of formatting, and joined one per line; a Bedrock viewer then
     * reads the same name and lore a Java viewer sees on the icon. An item with no lore yields just the name, and a
     * blank spec lore line stays a blank line so the operator's spacing carries over. The spec lore ({@link
     * MenuItemSpec#lore}) is used deliberately (the operator's written lore, not any lore a {@code b64:} or provider
     * icon embeds) since that is what belongs on a form button.
     */
    public String buttonText(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        StringBuilder label = new StringBuilder(plainLine(item.name(), ctx));
        for (String line : item.lore()) {
            label.append('\n').append(plainLine(line, ctx));
        }
        return label.toString();
    }

    /**
     * The resolved icon material spec for {@code item}: the very string the icon providers and the material fallback
     * read, with any {@code %token%} expanded ({@code %head%} → {@code skull:Notch}, or a literal {@code DIAMOND}). The
     * hybrid Bedrock renderer reads this to source a form button's image, so a per-entry {@code skull:%player%} icon
     * resolves against the entry's own context exactly as it would on a chest.
     */
    public String materialSpec(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        return resolveMaterialSpec(item.material(), ctx);
    }

    /**
     * A raw spec string resolved through the same {@code %token%}/{@code @key} path an item name takes, then flattened
     * to plain text. A Bedrock CustomForm's title, intro content and widget labels/options are flat strings, so the
     * hybrid form renderer resolves each operator-written string here (carrying a per-entry or per-open token through)
     * exactly as a button label or a menu title would.
     */
    public String plainText(String raw, MenuContext ctx) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(ctx, "ctx");
        return plainLine(raw, ctx);
    }

    /**
     * One text line resolved through the same {@code %token%}/{@code @key} path an icon name or lore line takes, then
     * flattened to plain text: a Bedrock form label is a flat string, so a button's name and lore reach it stripped of
     * all formatting.
     */
    private String plainLine(String line, MenuContext ctx) {
        return PlainTextComponentSerializer.plainText().serialize(resolveText(line, ctx));
    }

    /**
     * A shared {@link String} resolved for {@code viewer} and flattened to plain text: the label a Bedrock form needs
     * where the chest paints a wordless icon (a confirm window's yes/no wool). Goes through the same {@link GuiText}
     * catalog path an item name takes, so the label honours the viewer's locale, then drops all formatting to a flat
     * string.
     */
    public String plainMessage(Player viewer, String key) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        return PlainTextComponentSerializer.plainText().serialize(guiText.text(viewer, key));
    }

    public ItemStack render(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        String materialSpec = resolveMaterialSpec(item.material(), ctx);
        Optional<ItemStack> base = iconProviders.resolve(materialSpec, ctx);
        Component name = resolveText(item.name(), ctx);
        List<Component> lore = combineLore(item, base, ctx);
        // The canon writes a tile's title on the first lore line under a blank name; a lore-less button keeps
        // the one-line name it was written with.
        boolean untitled = Tiles.isUntitled(name, lore);
        List<Component> titled = Tiles.titled(theme.get(), name, lore);
        Component shown = untitled ? name : Tiles.blankName();
        ItemStack built = applyDecor(builderFor(base, materialSpec).name(shown).lore(titled), item.decor(), ctx)
                .build();
        // Tag every tile the engine renders (including an equipment/self-inventory icon, which is already a clone of
        // the viewer's real item) so any display copy that escapes into a real inventory is strippable. The mark rides
        // on the display copy only; the player's own items are never touched here (MenuItemMark, MenuAntiDupeListener).
        ItemStack marked = MenuItemMark.mark(built);
        // Last, and on the finished stack: the whole-tooltip flag and the hidden set live on one component, so
        // whichever is written second decides, and a spec that asked for no tooltip must not get one back.
        hideVanillaLines(marked, item.decor().meta());
        return marked;
    }

    /**
     * Combine the base icon's own lore with the spec lore per {@code item}'s {@link LoreMode}. REPLACE keeps only the
     * spec lore (the historic behaviour); APPEND puts the base lore first, then the spec lore beneath it; PREPEND puts
     * the spec lore first, then the base lore. The base lore comes from the resolved icon (a serialized {@code b64:}
     * stack or a provider item may carry its own) so a plain material, which has no lore of its own, renders
     * identically under any mode.
     */
    private List<Component> combineLore(MenuItemSpec item, Optional<ItemStack> base, MenuContext ctx) {
        List<Component> specLore = lore(item, ctx);
        if (item.loreMode() == LoreMode.REPLACE) {
            return specLore;
        }
        List<Component> baseLore =
                base.flatMap(icon -> Optional.ofNullable(icon.lore())).orElse(List.of());
        if (baseLore.isEmpty()) {
            return specLore;
        }
        List<Component> combined = new ArrayList<>(baseLore.size() + specLore.size());
        if (item.loreMode() == LoreMode.APPEND) {
            combined.addAll(baseLore);
            combined.addAll(specLore);
        } else {
            combined.addAll(specLore);
            combined.addAll(baseLore);
        }
        return combined;
    }

    /**
     * Build the lore components for {@code item}. Each spec line maps to one component as before, except an
     * inline/placeholder literal whose resolved value carries newlines, which expands into one component per {@code
     * \n}-separated segment. This lets a per-entry icon (the kit/warp browse rows) emit a variable number of lore lines
     * from a single {@code %placeholder%} (a {@code ✔/✘} per requirement, plus the conditional cooldown/cost/claimable
     * lines) rather than being capped at the spec's fixed line count.
     */
    public List<Component> lore(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        List<Component> out = new ArrayList<>(item.lore().size());
        for (String line : item.lore()) {
            appendLore(line, ctx, out);
        }
        return out;
    }

    /**
     * Append the component(s) for one lore spec line to {@code out}. A blank spec stays one blank line, and a {@code
     * @key} catalog line stays a single component (the catalog owns its own layout, so its output is never split here).
     * Any other line is an inline/placeholder literal: any {@code {math:}} block whose operand is missing is dropped
     * off the written line first, then its {@code %token%}s are substituted, then the result is split on {@code \n} so
     * a multi-line placeholder value becomes one lore component per segment. The {@code -1} split limit keeps trailing
     * empty segments, and a value with no newline yields exactly one component: identical to a plain literal line.
     *
     * <p>The missing-operand guard runs here and not only in {@link #resolveText}, because this is the path a price is
     * written on. Without it a lore {@code {math: %missing% + 1}} left {@code " + 1"} after the token pass, plus parsed
     * as a unary prefix, and the player read the number 1 on the line that says what a thing costs.
     */
    private void appendLore(String spec, MenuContext ctx, List<Component> out) {
        if (spec.isEmpty()) {
            out.add(Component.empty());
            return;
        }
        if (spec.startsWith("@")) {
            out.add(resolveText(spec, ctx));
            return;
        }
        String substituted = applyMath(substitutePlaceholders(dropMathWithMissingOperand(spec, ctx), ctx));
        for (String segment : substituted.split("\n", -1)) {
            out.add(guiText.render(segment));
        }
    }

    /**
     * The resolved material spec string: every {@code %token%} expands through its placeholder and the rest of the
     * line is kept. This is the string the icon providers and the material fallback both read, so {@code %head%}
     * naming a whole spec reaches the skull provider as {@code skull:Notch}, {@code skull:%player%} reaches it as
     * that entry's head, and {@code DIAMOND} reaches the material lookup unchanged.
     *
     * <p>It substitutes per token rather than replacing the whole value with the first token's expansion, which is
     * what every other operator-written string in a spec does. The whole-value form silently dropped the part of the
     * line around the token, so {@code skull:%player%} resolved to a bare player name, no provider claimed it, and a
     * list of heads rendered as a list of stone. A spec whose material is one bare token is unaffected: substituting
     * it is the same string.
     */
    private String resolveMaterialSpec(String raw, MenuContext ctx) {
        return substitutePlaceholders(raw, ctx);
    }

    /**
     * The {@link ItemBuilder} for a resolved {@code base}: from an icon provider's stack (a skull source, the viewer's
     * equipment, a serialized {@code b64:} stack, an HDB head) when one claimed the spec, else a plain item of the
     * named material. A provider's stack still has the item's name, lore and decor layered on top by the caller,
     * exactly as a material item does. It is split out from lore-combining so the resolved base stack (whose own lore
     * an append/prepend mode reads) stays visible to {@link #render}.
     */
    private ItemBuilder builderFor(Optional<ItemStack> base, String spec) {
        return base.map(ItemBuilder::from).orElseGet(() -> ItemBuilder.of(materialOrStone(spec)));
    }

    /**
     * The material named by {@code name}, falling back to {@link Material#STONE} for a blank or unknown name so a
     * typo (or a provider-shaped spec no provider claimed) never aborts the render.
     */
    private Material materialOrStone(String name) {
        if (name.isBlank()) {
            return Material.STONE;
        }
        Material material = Material.matchMaterial(name);
        return material != null ? material : Material.STONE;
    }

    /**
     * Resolve one text line. Every {@code %token%} is substituted with its placeholder value first; then a line
     * that originally began with {@code @} is looked up in the locale catalog (the rest is the message key),
     * while any other line is rendered as an inline MiniMessage literal. An empty spec yields an empty component
     * so the item simply has no name/lore line rather than a stray blank.
     *
     * <p>Both branches are handed the same {@link LinePlaceholders}, which resolves a token when somebody asks for
     * it rather than resolving every registered handler first. A literal line carries its own words, so what it
     * spells is the whole of what it wants. A {@code @key} line does not carry its words at all, and nothing here
     * can read which {@code {token}} arguments the catalog entry under that key spells; the answer is not to guess
     * them all in advance but to let the catalog ask for the ones it wants.
     */
    private Component resolveText(String s, MenuContext ctx) {
        if (s.isEmpty()) {
            return Component.empty();
        }
        String substituted = substitutePlaceholders(dropMathWithMissingOperand(s, ctx), ctx);
        Map<String, String> arguments = namedPlaceholders(s, ctx);
        if (s.startsWith("@")) {
            String key = substituted.substring(1);
            // The catalog entry may carry {token} arguments (e.g. {sound}, {warp}); it fills them from the same
            // placeholders a %token% would use, so a per-entry list item shows that entry's value. It asks for the
            // ones it wants by name and they resolve then, rather than every handler running first in case.
            return guiText.text(ctx.viewer(), key, arguments);
        }
        // Not a key, so the words are in the line. The viewer goes with it anyway: a consumer whose files
        // carry a per-viewer shorthand answers here, and one that does not is handed straight to render.
        return guiText.renderFor(ctx.viewer(), applyMath(substituted), arguments);
    }

    /**
     * The placeholder values one written line asks for, keyed by token id: only the {@code %token%}s the line
     * actually spells, and among those only the ones a resolver answers.
     *
     * <p>This used to be {@code resolveAll}, which runs every registered handler. A handler is a consumer's code and
     * it does something as well as answering: a PlaceholderAPI bridge parses, a permissions lookup reads a context, a
     * player-data reader touches a store, an economy handler asks a provider. Running all of them for a line that
     * names none of them fires work nobody asked for, on every lore line of every item of every render, and the
     * default {@link GuiText#renderFor} then throws the answer away. That is a correctness problem before it is a
     * cost: a handler with a side effect ran because a menu drew, not because anything named it.
     *
     * <p>A line spelling no token at all resolves nothing and allocates nothing, which is most lines. Each distinct
     * id is resolved once however many times the line spells it, and a handler that throws costs its own token and
     * is named on the console rather than escaping into the render.
     */
    private Map<String, String> namedPlaceholders(String line, MenuContext ctx) {
        return new LinePlaceholders(line, ctx);
    }

    /** The distinct {@code %token%} names one written line spells, in the order it spells them. */
    private static Set<String> namesIn(String line) {
        if (line.indexOf('%') < 0) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(line);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * The placeholder arguments for one written line: the {@code %token%}s the line spells are what the map holds,
     * and any other id is resolved the moment somebody asks for it by name.
     *
     * <p>The two halves answer the two shapes of caller, and both are needed. A literal line carries its own words,
     * so what it spells is the whole of what it wants, and iterating this map yields exactly that. A {@code @key}
     * line does not carry its words at all: the entry behind the key lives in the consumer's catalogue and may name
     * a {@code {coins}} argument the line never mentions. Resolving every registered handler in advance on the
     * chance the catalogue wanted one of them is what this replaces, and it is wrong twice over: a handler is a
     * consumer's code that does something as well as answering, so a PlaceholderAPI parse, a permissions lookup, a
     * player-data read and an economy call all fired because a menu drew rather than because anything asked; and on
     * the commonest path the answer was thrown away unread by {@link GuiText#renderFor}'s default.
     *
     * <p>So nothing is resolved until it is wanted. A catalogue that asks for {@code coins} by name gets it, and one
     * that asks for nothing costs nothing. Each id runs its handler at most once per line however often it is asked
     * for, including an id that resolved to nothing, and a handler that throws costs its own token and is named on
     * the console rather than escaping into the render.
     *
     * <p>Iteration deliberately yields only the spelled tokens rather than the whole registry. A caller that copies
     * this map, or walks it to substitute, therefore sees what the line names and not what it might have wanted:
     * that is the same rule the {@code get} half follows, and offering the registry to a walk would put the eager
     * resolution straight back.
     *
     * <p>Valid for the duration of the call it is passed into, like the context it reads. It is not thread safe and
     * does not need to be: a render is synchronous on the viewer's entity thread.
     */
    private final class LinePlaceholders extends AbstractMap<String, String> {

        private final MenuContext ctx;

        /** The token names the line spells, which are the ids this map holds when it is iterated. */
        private final Set<String> spelled;

        /**
         * The ids already put to the registry on this line, so one handler runs at most once however often the
         * line or the catalog entry names it. An id here but absent from {@link #values} resolved to nothing, which
         * is what keeps a token nobody registered from being asked for twice.
         */
        private final Set<String> asked = new HashSet<>();

        /** What each asked-for id resolved to. Absent means nothing answered, which is not the same as empty text. */
        private final Map<String, String> values = new LinkedHashMap<>();

        LinePlaceholders(String line, MenuContext ctx) {
            this.ctx = ctx;
            this.spelled = namesIn(line);
        }

        @Override
        public @Nullable String get(Object key) {
            return key instanceof String id ? resolve(id) : null;
        }

        @Override
        public boolean containsKey(Object key) {
            return key instanceof String id && resolve(id) != null;
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            Map<String, String> held = new LinkedHashMap<>();
            for (String id : spelled) {
                String value = resolve(id);
                if (value != null) {
                    held.put(id, value);
                }
            }
            return Collections.unmodifiableMap(held).entrySet();
        }

        private @Nullable String resolve(String id) {
            if (asked.add(id)) {
                placeholders.resolveOrReport(id, ctx).ifPresent(value -> values.put(id, value));
            }
            return values.get(id);
        }
    }

    /**
     * Evaluate every {@code {math: <expr>}} block in an already-token-substituted line, replacing each with the
     * evaluator's formatted numeric result. Running after the {@code %token%} pass means the inner expression is
     * already literal, so {@code {math: %coins% * 2}} evaluates the resolved number rather than the token. A line
     * with no math block returns unchanged (the {@code indexOf} fast-path avoids allocating a matcher for the common
     * case); a block the sandbox rejects becomes empty rather than aborting the render, the same fail-soft stance the
     * rest of the renderer takes. Material specs never flow through here, so a {@code %token%} material is untouched.
     */
    private String applyMath(String text) {
        if (text.indexOf("{math:") < 0) {
            return text;
        }
        Matcher matcher = MATH.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(evaluateMath(matcher.group(1))));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Remove every {@code {math: …}} block whose expression names a token that resolves to nothing, before the token
     * pass runs. Afterwards the hole is anonymous: a token that resolved to nothing leaves {@code "+ 1"}, and plus is
     * also a unary prefix, so the expression parses and the player is shown the number 1 as though the menu meant it.
     * Multiplication and division cannot absorb a missing operand, so those already showed a gap; this makes the two
     * operators that can behave like the two that cannot.
     *
     * <p>Run here rather than by reordering the two passes, which would cost the thing the order buys: a token whose
     * value carries a {@code {math:}} block of its own is still evaluated. The price is that a token inside a math
     * block is resolved twice, once to ask whether it is there and once to substitute it. Only tokens inside a math
     * block pay it.
     *
     * <p>What this does not cover, stated rather than left to be discovered: a block assembled inside a registry
     * placeholder's own return value is not visible in the written line, so it is not checked here.
     */
    private String dropMathWithMissingOperand(String raw, MenuContext ctx) {
        if (raw.indexOf("{math:") < 0) {
            return raw;
        }
        Matcher matcher = MATH.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String kept = hasMissingOperand(matcher.group(1), ctx) ? "" : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(kept));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Whether any {@code %token%} in {@code expression} resolves to nothing, which is not a number in any position.
     * Blank rather than empty: a handler reading a padded column, a config value trimmed to nothing, or a formatter
     * given no value answers a space, and a space is exactly as absent from an expression as the empty string is.
     */
    private boolean hasMissingOperand(String expression, MenuContext ctx) {
        Matcher matcher = PLACEHOLDER.matcher(expression);
        while (matcher.find()) {
            if (resolveToken(matcher.group(1), ctx).isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** Evaluate one substituted expression to the evaluator's formatted number, or empty when it cannot be parsed. */
    private static String evaluateMath(String expression) {
        try {
            return Expressions.format(Expressions.evaluateNumber(expression));
        } catch (ExpressionException | RuntimeException rejected) {
            // A malformed or non-numeric math block renders blank rather than leaking the raw expression or aborting.
            return "";
        }
    }

    /** Replace every {@code %token%} in {@code source} with its resolved value (argument or registry, or empty). */
    private String substitutePlaceholders(String source, MenuContext ctx) {
        Matcher matcher = PLACEHOLDER.matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = resolveToken(matcher.group(1), ctx);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Resolve one bare {@code %token%} name to its text, most-specific source first. An {@code argument_<name>} token
     * is a typed command argument the menu was opened with, so it reads from {@link MenuContext#arguments()} (an
     * unknown name yields empty). Otherwise the menu's own {@code placeholders {}} block is consulted <em>before</em>
     * the shared registry, so a menu-scoped {@code %name%} overrides a built-in, data reader or global custom for this
     * menu alone; a local template is expanded through {@link #substituteLocal}. Any token the local block does not
     * define resolves through the placeholder registry exactly as before.
     */
    private String resolveToken(String token, MenuContext ctx) {
        if (token.startsWith(ARGUMENT_PREFIX)) {
            return ctx.arguments().getOrDefault(token.substring(ARGUMENT_PREFIX.length()), "");
        }
        String local = ctx.localPlaceholders().get(token);
        if (local != null) {
            return substituteLocal(local, ctx);
        }
        return resolveRegistered(token, ctx);
    }

    /**
     * Resolve one token through the placeholder registry, treating a handler that throws as a token that could not be
     * filled rather than as a failed render. A handler is code a consumer wrote, and it may be asked for a token on a
     * menu whose context does not carry what it needs: without this, one such handler costs the whole window, while
     * every bad value written by an operator costs one icon. {@code PlaceholderRegistry.resolveAll} already takes this
     * stance and says so in its own javadoc; this is the same stance on the seam the renderer actually substitutes
     * through.
     *
     * <p>The token is named on the console, because a handler that throws is a defect in the plugin that registered
     * it, and a silently blank token gives its author nothing to look for. Once per token rather than once per draw:
     * an item with {@code update = true} re-renders every tick, and a line per tick would bury the first one.
     */
    private String resolveRegistered(String token, MenuContext ctx) {
        try {
            return placeholders.resolve(token, ctx).orElse("");
        } catch (RuntimeException handlerFailed) {
            if (reportedBadHandlers.add(token)) {
                LOG.warning("placeholder '" + token + "' could not be resolved: " + handlerFailed);
            }
            return "";
        }
    }

    /**
     * Expand a menu-local placeholder template, resolving every inner {@code %token%} through {@link #resolveToken}
     * so a nested local resolves local-first while a built-in/data/global inner token resolves via the registry. A
     * {@code {math: …}} block and MiniMessage {@code <tags>} are left verbatim for the outer line passes that own them,
     * so a local {@code "{math: %coins% * 2}"} becomes {@code "{math: 50 * 2}"} here and the outer {@code applyMath}
     * evaluates it. The depth guard bounds recursion: at {@link #MAX_LOCAL_DEPTH} the template is returned unexpanded,
     * so a cycle ({@code a=%b%}, {@code b=%a%}) terminates instead of overflowing the stack.
     */
    private String substituteLocal(String template, MenuContext ctx) {
        int[] depth = LOCAL_DEPTH.get();
        if (depth[0] >= MAX_LOCAL_DEPTH) {
            return template;
        }
        depth[0]++;
        try {
            Matcher matcher = PLACEHOLDER.matcher(dropMathWithMissingOperand(template, ctx));
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(resolveToken(matcher.group(1), ctx)));
            }
            matcher.appendTail(out);
            return out.toString();
        } finally {
            depth[0]--;
        }
    }

    /** Layer the spec's amount, glow, model data, item flags, and native rich meta onto the in-progress item. */
    private ItemBuilder applyDecor(ItemBuilder builder, ItemDecor decor, MenuContext ctx) {
        applyAmount(builder, decor, ctx);
        builder.glow(glowing(decor, ctx));
        applyModelData(builder, decor, ctx);
        ItemFlag[] flags = resolveFlags(decor.flagTokens());
        if (flags.length > 0) {
            builder.flags(flags);
        }
        applyRichMeta(builder, decor.meta());
        return builder;
    }

    /**
     * Whether the item glints: a {@code %token%} glow resolves through the placeholder path and counts as on for
     * {@code true}/{@code yes}/{@code 1}, otherwise the static spec value stands. This is what lets one list
     * template glint only the entry a screen considers selected.
     */
    private boolean glowing(ItemDecor decor, MenuContext ctx) {
        Optional<String> token = decor.meta().dynamicGlow();
        if (token.isEmpty()) {
            return decor.glow();
        }
        String resolved = substitutePlaceholders(token.get(), ctx).strip();
        return resolved.equalsIgnoreCase("true") || resolved.equalsIgnoreCase("yes") || resolved.equals("1");
    }

    /**
     * Set the stack amount, preferring a {@code dynamicAmount} {@code %token%} resolved to a number over the static
     * spec value and clamping the floor to one. A value above the material's max stack size keeps a single item
     * rather than aborting the render, matching the fail-soft stance the rest of the decor takes.
     */
    private void applyAmount(ItemBuilder builder, ItemDecor decor, MenuContext ctx) {
        int amount = Math.max(1, dynamicInt(decor.meta().dynamicAmount(), ctx).orElse(decor.amount()));
        try {
            builder.amount(amount);
        } catch (IllegalArgumentException aboveMax) {
            // A dynamic amount above the material's max stack size keeps a single item rather than aborting.
            builder.amount(1);
        }
    }

    /** Apply the custom model data, preferring a {@code dynamicModelData} {@code %token%} over the static value. */
    private void applyModelData(ItemBuilder builder, ItemDecor decor, MenuContext ctx) {
        dynamicInt(decor.meta().dynamicModelData(), ctx)
                .or(decor::modelData)
                .ifPresent(data -> safely(() -> builder.customModelData(data)));
    }

    /** Resolve a {@code %token%} string through the placeholder path and parse it to an int, when both succeed. */
    private Optional<Integer> dynamicInt(Optional<String> token, MenuContext ctx) {
        return token.map(raw -> substitutePlaceholders(raw, ctx)).flatMap(ItemRenderer::parseInt);
    }

    /**
     * Apply every native rich-meta value the operator declared, resolving each token against the live registries.
     * Anything that does not resolve (an unknown enchant, an unsupported colour, a banner/trim registry the runtime
     * cannot model) is skipped rather than aborting the render, the same way an unknown flag is skipped.
     */
    private void applyRichMeta(ItemBuilder builder, RichMeta meta) {
        if (meta.unbreakable()) {
            safely(() -> builder.unbreakable(true));
        }
        meta.enchantments().forEach(token -> applyEnchant(builder, token, false));
        meta.storedEnchantments().forEach(token -> applyEnchant(builder, token, true));
        meta.leatherColor().flatMap(this::parseColor).ifPresent(color -> safely(() -> builder.leatherColor(color)));
        applyPotion(builder, meta.potion());
        meta.bannerPatterns().forEach(token -> applyBannerPattern(builder, token));
        meta.trim().ifPresent(trim -> applyTrim(builder, trim));
        meta.damage().ifPresent(damage -> applyDamage(builder, damage));
        meta.itemModel().ifPresent(model -> applyItemModel(builder, model));
        applyDataComponents(builder, meta.components());
    }

    /**
     * Apply the native data-components the operator declared. Each one resolves and applies independently and
     * fail-soft: an unknown rarity, a malformed tooltip-style key, an unresolved attribute/operation/slot, or a
     * component a runtime cannot model is skipped rather than aborting the render, the same as an unknown flag.
     */
    private void applyDataComponents(ItemBuilder builder, DataComponents components) {
        components.rarity().flatMap(this::parseRarity).ifPresent(rarity -> safely(() -> builder.rarity(rarity)));
        components.tooltipStyle().ifPresent(raw -> applyTooltipStyle(builder, raw));
        components
                .enchantGlint()
                .ifPresent(glint -> safely(() -> builder.editMeta(meta -> meta.setEnchantmentGlintOverride(glint))));
        components.enchantable().ifPresent(value -> safely(() -> builder.enchantable(value)));
        applyAttributeModifiers(builder, components.attributeModifiers());
        components.food().ifPresent(food -> applyFood(builder, food));
        components.tool().ifPresent(tool -> applyTool(builder, tool));
    }

    /**
     * Decide which components the client may write a tooltip line for, and hide the rest.
     *
     * <p>A menu icon is a button and the client does not know that: left alone it writes the mining speed under an
     * IRON_PICKAXE, "Dyed" under a tinted chestplate and the flight duration under a firework, beneath a lore that
     * already said what the button does. So the default silences {@link Tooltips#VANILLA_COMPONENTS}, minus
     * whatever the {@code decor} block asked for: an operator who wrote {@code enchantments} or {@code trim} meant
     * that line to be read, and the line the client added by itself is the one nobody chose. {@code
     * hide-vanilla-tooltip=false} gives every line back, and {@code hidden-components} names an exact set and wins
     * over both.
     *
     * <p>This is about drawing and not about behaviour: hiding {@code EQUIPPABLE} removes the line, not the equip.
     * A menu stops the equip by cancelling the click, which is where that belongs.
     */
    private static void hideVanillaLines(ItemStack item, RichMeta meta) {
        Set<DataComponentType> hidden = hiddenFor(meta);
        safely(() -> Tooltips.hide(item, hidden));
        // After, not before: both live on the same component, and hiding the client's lines must not hand back a
        // tooltip the spec asked to take away entirely.
        meta.components()
                .hideTooltip()
                .ifPresent(whole -> safely(() -> item.editMeta(edited -> edited.setHideTooltip(whole))));
    }

    /** The components to silence for {@code meta}: the decision this class owns, split out so a test can read it. */
    static Set<DataComponentType> hiddenFor(RichMeta meta) {
        DataComponents components = meta.components();
        if (!components.hiddenComponents().isEmpty()) {
            return components.hiddenComponents().stream()
                    .map(token -> fromRegistry(RegistryKey.DATA_COMPONENT_TYPE, keyOf(token)))
                    .flatMap(Optional::stream)
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (!components.hideVanillaTooltip().orElse(true)) {
            return Set.of();
        }
        Set<DataComponentType> hidden = new HashSet<>(Tooltips.VANILLA_COMPONENTS);
        hidden.removeAll(declaredByTheOperator(meta));
        return Set.copyOf(hidden);
    }

    /**
     * The components the {@code decor} block filled in itself, whose tooltip lines are therefore content rather
     * than noise. Everything else in {@link Tooltips#VANILLA_COMPONENTS} comes from the material alone.
     */
    private static Set<DataComponentType> declaredByTheOperator(RichMeta meta) {
        Set<DataComponentType> declared = new HashSet<>();
        if (meta.unbreakable()) {
            declared.add(DataComponentTypes.UNBREAKABLE);
        }
        if (!meta.enchantments().isEmpty()) {
            declared.add(DataComponentTypes.ENCHANTMENTS);
        }
        if (!meta.storedEnchantments().isEmpty()) {
            declared.add(DataComponentTypes.STORED_ENCHANTMENTS);
        }
        if (meta.leatherColor().isPresent()) {
            declared.add(DataComponentTypes.DYED_COLOR);
        }
        if (!meta.potion().equals(RichMeta.PotionSpec.NONE)) {
            declared.add(DataComponentTypes.POTION_CONTENTS);
        }
        if (!meta.bannerPatterns().isEmpty()) {
            declared.add(DataComponentTypes.BANNER_PATTERNS);
        }
        if (meta.trim().isPresent()) {
            declared.add(DataComponentTypes.TRIM);
        }
        if (!meta.components().attributeModifiers().isEmpty()) {
            declared.add(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        }
        if (meta.components().tool().isPresent()) {
            declared.add(DataComponentTypes.TOOL);
        }
        return declared;
    }

    /** Override the tooltip-style with a resource-pack key, skipping a malformed or unsupported key. */
    private void applyTooltipStyle(ItemBuilder builder, String raw) {
        NamespacedKey key = keyOf(raw);
        if (key == null) {
            return;
        }
        safely(() -> builder.tooltipStyle(key));
    }

    /** Apply each {@code attribute:amount:operation:slot} modifier, deriving a unique key per entry by its index. */
    private void applyAttributeModifiers(ItemBuilder builder, List<String> tokens) {
        for (int index = 0; index < tokens.size(); index++) {
            applyAttributeModifier(builder, tokens.get(index), index);
        }
    }

    /**
     * Apply one {@code attribute:amount:operation:slot} modifier. The operation defaults to {@code add_number} and
     * the slot to {@code any} when omitted; an unresolved attribute, amount, operation, or slot skips the modifier.
     */
    private void applyAttributeModifier(ItemBuilder builder, String token, int index) {
        String[] parts = token.split(":", 4);
        if (parts.length < 2) {
            return;
        }
        Optional<Attribute> attribute = fromRegistry(RegistryKey.ATTRIBUTE, attributeKey(parts[0]));
        Optional<Double> amount = parseDouble(parts[1]);
        Optional<AttributeModifier.Operation> operation = parseOperation(parts.length > 2 ? parts[2] : "add_number");
        Optional<EquipmentSlotGroup> slot = parseSlotGroup(parts.length > 3 ? parts[3] : "");
        if (attribute.isEmpty() || amount.isEmpty() || operation.isEmpty() || slot.isEmpty()) {
            return;
        }
        NamespacedKey key =
                NamespacedKey.fromString("uxmlib:" + attribute.get().getKey().value() + "_" + index);
        if (key == null) {
            return;
        }
        AttributeModifier modifier = new AttributeModifier(key, amount.get(), operation.get(), slot.get());
        safely(() -> builder.attribute(attribute.get(), modifier));
    }

    /** Build the food component from the declared overrides, leaving any unset field at the component's default. */
    private void applyFood(ItemBuilder builder, DataComponents.FoodSpec spec) {
        safely(() -> builder.editMeta(meta -> {
            FoodComponent food = meta.getFood();
            spec.nutrition().ifPresent(food::setNutrition);
            spec.saturation().ifPresent(saturation -> food.setSaturation(saturation.floatValue()));
            spec.canAlwaysEat().ifPresent(food::setCanAlwaysEat);
            meta.setFood(food);
        }));
    }

    /** Build the tool component from the declared overrides, leaving any unset field at the component's default. */
    private void applyTool(ItemBuilder builder, DataComponents.ToolSpec spec) {
        safely(() -> builder.editMeta(meta -> {
            ToolComponent tool = meta.getTool();
            spec.defaultMiningSpeed().ifPresent(speed -> tool.setDefaultMiningSpeed(speed.floatValue()));
            spec.damagePerBlock().ifPresent(tool::setDamagePerBlock);
            meta.setTool(tool);
        }));
    }

    /**
     * Map the spec's raw flag tokens to Bukkit {@link ItemFlag}s, skipping any token that is not a flag name. The
     * token goes through {@link #enumToken} first, because every other enum-valued name in this block (a dye, a
     * rarity, an attribute operation, a slot group) is read the same way, and a flag has no reason to be the one
     * that is not. A token that is still not a flag after the fold is named on the console rather than dropped in
     * silence.
     */
    private ItemFlag[] resolveFlags(List<String> tokens) {
        List<ItemFlag> flags = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            try {
                flags.add(ItemFlag.valueOf(enumToken(token)));
            } catch (IllegalArgumentException unknownFlag) {
                // A spec naming a flag that does not exist on this server should not abort the render, but a
                // dropped flag looks exactly like a server default, so it is said out loud instead of swallowed.
                reportUnreadableToken("item-flag", token);
            }
        }
        return flags.toArray(ItemFlag[]::new);
    }

    /**
     * An operator's token folded to the spelling an enum constant carries: trimmed, hyphens read as the underscores
     * they stand for, then upper-cased.
     *
     * <p>Every key in a menu file is hyphenated ({@code model-data}, {@code leather-color}, {@code
     * attribute-modifiers}), so an operator writes a value the same way and reaches for {@code hide-dye} or {@code
     * light-blue}. Without the fold that flag was skipped, and a skipped flag looks exactly like a server default, so
     * nothing on the screen said the line had been ignored.
     *
     * <p>The fold is lossless. A Java constant name holds only letters, digits, underscores and currency symbols, and
     * a hyphen cannot appear in one at all, so no constant means one thing spelled with a hyphen and another spelled
     * with an underscore. Reading the two as the same name can therefore never pick the wrong constant.
     */
    private static String enumToken(String raw) {
        return raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    /**
     * Name on the console one operator token nothing could be read out of, once per distinct token per renderer.
     *
     * <p>Silence is the expensive part of a fail-soft parser. A dropped flag looks like a server default and a
     * dropped colour looks like the material's own, so an operator who mistyped one sees a menu that renders and has
     * nothing at all to look for. The token and the kind it was being read as are what a search needs.
     *
     * <p>The file it was written in is not said, and that is a gap rather than a choice: {@link MenuItemSpec} is a
     * plain value with no source path on it, and nothing on the render path is told which menu file a spec was parsed
     * from. Carrying an origin down to here means a new component on the spec model and on every constructor of it,
     * which is a change of its own rather than a line in this one.
     */
    private void reportUnreadableToken(String kind, String token) {
        if (reportedUnreadableTokens.add(kind + '=' + token)) {
            LOG.warning("event=menu_token_unreadable kind=" + kind + " token='" + token + "'");
        }
    }

    /**
     * Apply one {@code name:level} enchant token to the item or its stored-enchant book. The level defaults to one
     * when absent or unparseable, and an id that does not resolve in the enchantment registry is skipped.
     */
    private void applyEnchant(ItemBuilder builder, String token, boolean stored) {
        int colon = token.lastIndexOf(':');
        Optional<Integer> parsed = colon < 0 ? Optional.empty() : parseInt(token.substring(colon + 1));
        String name = colon < 0 || parsed.isEmpty() ? token : token.substring(0, colon);
        fromRegistry(RegistryKey.ENCHANTMENT, keyOf(name)).ifPresent(enchant -> {
            int level = Math.max(1, parsed.orElse(1));
            safely(() -> {
                if (stored) {
                    builder.storedEnchant(enchant, level);
                } else {
                    builder.enchant(enchant, level);
                }
            });
        });
    }

    /** Apply a potion's base type, display colour, and any {@code effect:amplifier:durationTicks} custom effects. */
    private void applyPotion(ItemBuilder builder, RichMeta.PotionSpec potion) {
        potion.type()
                .flatMap(name -> fromRegistry(Registry.POTION, keyOf(name)))
                .ifPresent(base ->
                        safely(() -> builder.editTypedMeta(PotionMeta.class, meta -> meta.setBasePotionType(base))));
        potion.color().flatMap(this::parseColor).ifPresent(color -> safely(() -> builder.potionColor(color)));
        potion.effects()
                .forEach(token -> parsePotionEffect(token).ifPresent(e -> safely(() -> builder.potionEffect(e))));
    }

    /** Append one {@code pattern:dyecolor} banner pattern, skipping an unknown pattern id or dye name. */
    private void applyBannerPattern(ItemBuilder builder, String token) {
        int colon = token.lastIndexOf(':');
        if (colon <= 0) {
            return;
        }
        Optional<PatternType> type = fromRegistry(RegistryKey.BANNER_PATTERN, keyOf(token.substring(0, colon)));
        Optional<DyeColor> color = parseDye(token.substring(colon + 1));
        if (type.isPresent() && color.isPresent()) {
            safely(() -> builder.bannerPattern(color.get(), type.get()));
        }
    }

    /** Set the armour trim from a trim-material id and trim-pattern id, skipping either that does not resolve. */
    private void applyTrim(ItemBuilder builder, RichMeta.TrimSpec trim) {
        Optional<TrimMaterial> material = fromRegistry(RegistryKey.TRIM_MATERIAL, keyOf(trim.material()));
        Optional<TrimPattern> pattern = fromRegistry(RegistryKey.TRIM_PATTERN, keyOf(trim.pattern()));
        if (material.isPresent() && pattern.isPresent()) {
            safely(() -> builder.editTypedMeta(
                    ArmorMeta.class, meta -> meta.setTrim(new ArmorTrim(material.get(), pattern.get()))));
        }
    }

    /** Set the durability damage, a no-op on a non-damageable item; a negative value is ignored. */
    private void applyDamage(ItemBuilder builder, int damage) {
        if (damage >= 0) {
            safely(() -> builder.damage(damage));
        }
    }

    /** Override the item model with a resource-pack key, skipping a malformed or unsupported key. */
    private void applyItemModel(ItemBuilder builder, String raw) {
        NamespacedKey key = keyOf(raw);
        if (key == null) {
            return;
        }
        safely(() -> builder.itemModel(key));
    }

    /** Parse a {@code effect:amplifier:durationTicks} token into a {@link PotionEffect}, when its type resolves. */
    private static Optional<PotionEffect> parsePotionEffect(String token) {
        String[] parts = token.split(":", 3);
        Optional<PotionEffectType> type = fromRegistry(Registry.EFFECT, keyOf(parts[0]));
        if (type.isEmpty()) {
            return Optional.empty();
        }
        int amplifier = parts.length > 1 ? Math.max(0, parseInt(parts[1]).orElse(0)) : 0;
        int duration = parts.length > 2 ? parseInt(parts[2]).orElse(DEFAULT_EFFECT_TICKS) : DEFAULT_EFFECT_TICKS;
        return Optional.of(new PotionEffect(type.get(), duration, amplifier));
    }

    /**
     * A colour as a {@code #RRGGBB} hex, an {@code r,g,b} triple, or a named dye colour; empty when unparseable, and
     * named on the console when so, because a colour that did not apply looks like the material's own.
     */
    private Optional<Color> parseColor(String raw) {
        Optional<Color> color = readColor(raw.trim());
        if (color.isEmpty()) {
            reportUnreadableToken("colour", raw);
        }
        return color;
    }

    /** The colour {@code value} names in whichever of the three forms it is written; empty when it names none. */
    private static Optional<Color> readColor(String value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (value.startsWith("#")) {
                return Optional.of(Color.fromRGB(Integer.parseInt(value.substring(1), 16)));
            }
            if (value.contains(",")) {
                return rgbTriple(value);
            }
            return Optional.of(DyeColor.valueOf(enumToken(value)).getColor());
        } catch (IllegalArgumentException malformed) {
            // A bad hex, an out-of-range channel, or an unknown colour name is skipped, like an unknown flag.
            return Optional.empty();
        }
    }

    /** Parse an {@code r,g,b} triple into a colour; empty when it is not exactly three channels. */
    private static Optional<Color> rgbTriple(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        return Optional.of(Color.fromRGB(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())));
    }

    /** A {@link DyeColor} by folded name, present only when it matches a known dye; named on the console when not. */
    private Optional<DyeColor> parseDye(String name) {
        try {
            return Optional.of(DyeColor.valueOf(enumToken(name)));
        } catch (IllegalArgumentException unknown) {
            reportUnreadableToken("dye-colour", name);
            return Optional.empty();
        }
    }

    /** An {@link ItemRarity} by folded name (COMMON/UNCOMMON/RARE/EPIC), empty when it is not a rarity. */
    private Optional<ItemRarity> parseRarity(String name) {
        try {
            return Optional.of(ItemRarity.valueOf(enumToken(name)));
        } catch (IllegalArgumentException unknown) {
            reportUnreadableToken("rarity", name);
            return Optional.empty();
        }
    }

    /** An {@link AttributeModifier.Operation} by name ({@code add_number}/{@code add_scalar}/{@code multiply_scalar_1}). */
    private Optional<AttributeModifier.Operation> parseOperation(String name) {
        try {
            return Optional.of(AttributeModifier.Operation.valueOf(enumToken(name)));
        } catch (IllegalArgumentException unknown) {
            reportUnreadableToken("attribute-operation", name);
            return Optional.empty();
        }
    }

    /** An {@link EquipmentSlotGroup} by name; a blank token is the whole-item {@code any}, an unknown one is named. */
    private Optional<EquipmentSlotGroup> parseSlotGroup(String name) {
        return switch (enumToken(name)) {
            case "", "ANY" -> Optional.of(EquipmentSlotGroup.ANY);
            case "HAND", "MAINHAND", "MAIN_HAND" -> Optional.of(EquipmentSlotGroup.MAINHAND);
            case "OFF_HAND", "OFFHAND" -> Optional.of(EquipmentSlotGroup.OFFHAND);
            case "FEET" -> Optional.of(EquipmentSlotGroup.FEET);
            case "LEGS" -> Optional.of(EquipmentSlotGroup.LEGS);
            case "CHEST" -> Optional.of(EquipmentSlotGroup.CHEST);
            case "HEAD" -> Optional.of(EquipmentSlotGroup.HEAD);
            case "ARMOR" -> Optional.of(EquipmentSlotGroup.ARMOR);
            case "BODY" -> Optional.of(EquipmentSlotGroup.BODY);
            default -> {
                reportUnreadableToken("equipment-slot", name);
                yield Optional.empty();
            }
        };
    }

    /**
     * A registry key for an attribute name. A legacy category prefix ({@code generic.}, {@code player.}, …) is
     * stripped so the old {@code generic.attack_damage} spelling still resolves to the modern flat {@code attack_damage}
     * key; null when the remainder is not a valid key.
     */
    private static @Nullable NamespacedKey attributeKey(String raw) {
        String name = raw.trim();
        int dot = name.lastIndexOf('.');
        return keyOf(dot >= 0 ? name.substring(dot + 1) : name);
    }

    /** The registry entry for {@code key}, empty when the key is null or the data-driven registry cannot model it. */
    private static <T extends Keyed> Optional<T> fromRegistry(RegistryKey<T> registry, @Nullable NamespacedKey key) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(
                    RegistryAccess.registryAccess().getRegistry(registry).get(key));
        } catch (RuntimeException registryUnavailable) {
            return Optional.empty();
        }
    }

    /** The entry for {@code key} in a directly-held {@link Registry}, empty when null or the runtime cannot model it. */
    private static <T extends Keyed> Optional<T> fromRegistry(Registry<T> registry, @Nullable NamespacedKey key) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(registry.get(key));
        } catch (RuntimeException registryUnavailable) {
            return Optional.empty();
        }
    }

    /** A registry key from a raw id, lower-cased so an UPPER_SNAKE config value still resolves; null when malformed. */
    private static @Nullable NamespacedKey keyOf(String raw) {
        return NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
    }

    /** Parse a trimmed integer, empty when the value is not a number. */
    private static Optional<Integer> parseInt(String raw) {
        try {
            return Optional.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** Parse a trimmed double, empty when the value is not a number. */
    private static Optional<Double> parseDouble(String raw) {
        try {
            return Optional.of(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /**
     * Run one meta application, swallowing any runtime failure so a single unsupported value never aborts the whole
     * render. A meta type the test runtime cannot model (an unimplemented registry or setter) still ships working on
     * real Paper; here it simply degrades to a no-op, the same fail-soft contract as an unknown flag token.
     */
    private static void safely(Runnable application) {
        try {
            application.run();
        } catch (RuntimeException unsupported) {
            // This meta type is not modelled by the runtime (or its value did not resolve); skip, never abort.
        }
    }
}
