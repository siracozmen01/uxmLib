package com.uxplima.uxmlib.condition.action;

import java.util.Objects;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import com.uxplima.uxmlib.condition.ItemStore;
import com.uxplima.uxmlib.condition.Wallet;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * Factory for the built-in {@link Action} closures. Each method captures the static payload of a config action
 * (a MiniMessage template, a command line, a parsed sound spec) and returns a closure that, at run time,
 * resolves placeholders against the {@link ActionContext} and performs the native delivery: Adventure {@code
 * Audience} for text and sound, the context's {@link CommandSink}s for commands, the subject player for a
 * close. Splitting closure construction out of {@link ActionParser} keeps both types small.
 *
 * <p>Text actions are flagged {@code async()} true: rendering MiniMessage and calling {@code sendMessage}/
 * {@code sendActionBar} only touches an {@code Audience} and is thread-agnostic. Command, close and take actions are
 * sync (the default) because dispatching a command, closing an inventory and editing one must run on the
 * thread that owns the player.
 */
public final class Actions {

    private Actions() {}

    /** {@code [message] <template>}: render the template and send it to the target audience. */
    public static Action message(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(context -> context.target().sendMessage(render(context, template)));
    }

    /** {@code [broadcast] <template>}: render the template and send it to the broadcast audience. */
    public static Action broadcast(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(context -> context.broadcast().sendMessage(render(context, template)));
    }

    /** {@code [actionbar] <template>}: render the template into the target's action bar. */
    public static Action actionBar(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(context -> context.target().sendActionBar(render(context, template)));
    }

    /** {@code [title] <template>}: show the template as a title to the target (empty subtitle). */
    public static Action title(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(
                context -> context.target().showTitle(Title.title(render(context, template), Component.empty())));
    }

    /** {@code [console] <command>}: dispatch the resolved command through the console sink. */
    public static Action console(String commandTemplate) {
        Objects.requireNonNull(commandTemplate, "commandTemplate");
        return context -> context.consoleSink().dispatch(stripSlash(context.resolve(commandTemplate)));
    }

    /** {@code [player] <command>}: dispatch the resolved command through the player sink. */
    public static Action playerCommand(String commandTemplate) {
        Objects.requireNonNull(commandTemplate, "commandTemplate");
        return context -> context.playerSink().dispatch(stripSlash(context.resolve(commandTemplate)));
    }

    /** {@code [close]}: close the subject player's inventory, or do nothing when there is no player. */
    public static Action close() {
        return context -> context.player().ifPresent(player -> player.closeInventory());
    }

    /**
     * {@code [sound] <key> [volume] [pitch]}: play the parsed sound to the target. The key is resolved at run
     * time from a placeholder template, so it can be malformed (an uppercase letter, an empty or garbage
     * resolution). {@link Action} must not throw on delivery, so an unparseable key is skipped rather than
     * letting {@link Key#key(String)} raise {@link net.kyori.adventure.key.InvalidKeyException} and abort the
     * remaining actions in the list.
     */
    public static Action sound(SoundSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return asyncText(context -> {
            String resolved = context.resolve(spec.keyTemplate());
            if (!Key.parseable(resolved)) {
                return;
            }
            context.target()
                    .playSound(Sound.sound(Key.key(resolved), Sound.Source.MASTER, spec.volume(), spec.pitch()));
        });
    }

    /**
     * {@code [take-money] [currency] <amount>}: take the amount from the context's {@link Wallet}. The take is
     * all or nothing: the wallet either pays the whole amount or the action throws {@link ActionCostException}
     * having spent nothing.
     */
    public static CostAction takeMoney(MoneyCost cost) {
        Objects.requireNonNull(cost, "cost");
        return new TakeMoneyAction(cost);
    }

    /**
     * {@code [take-item] <item> [amount]}: take the amount from the context's {@link ItemStore}. The take is
     * all or nothing: the store either consumes the whole amount or the action throws {@link
     * ActionCostException} having consumed nothing.
     */
    public static CostAction takeItem(ItemCost cost) {
        Objects.requireNonNull(cost, "cost");
        return new TakeItemAction(cost);
    }

    private static Component render(ActionContext context, String template) {
        return Text.mini(context.resolve(template));
    }

    private static String stripSlash(String commandLine) {
        String stripped = commandLine.strip();
        return stripped.startsWith("/") ? stripped.substring(1) : stripped;
    }

    private static Action asyncText(Action delegate) {
        return new Action() {
            @Override
            public void run(ActionContext context) {
                delegate.run(context);
            }

            @Override
            public boolean async() {
                return true;
            }
        };
    }

    /**
     * The static structure of a {@code [take-money]} payload: the currency template and the amount template.
     * An empty currency names the wallet's own default currency.
     */
    public record MoneyCost(String currencyTemplate, String amountTemplate) {

        /** Canonical constructor null-checks both templates; neither is resolved until run time. */
        public MoneyCost {
            Objects.requireNonNull(currencyTemplate, "currencyTemplate");
            Objects.requireNonNull(amountTemplate, "amountTemplate");
        }
    }

    /** The static structure of a {@code [take-item]} payload: the item template and the amount template. */
    public record ItemCost(String itemTemplate, String amountTemplate) {

        /** Canonical constructor null-checks both templates; neither is resolved until run time. */
        public ItemCost {
            Objects.requireNonNull(itemTemplate, "itemTemplate");
            Objects.requireNonNull(amountTemplate, "amountTemplate");
        }
    }

    // Sync, like every other action that changes server state: an economy call may block and an inventory edit
    // belongs to the thread that owns the player, so the driver picks the lane rather than the closure.
    private record TakeMoneyAction(MoneyCost cost) implements CostAction {

        @Override
        public void run(ActionContext context) {
            double amount = amount(context);
            String currency = currency(context);
            if (amount <= 0 || !context.wallet().withdraw(context.player().orElse(null), currency, amount)) {
                throw new ActionCostException("cannot take " + describe(context));
            }
        }

        @Override
        public boolean affordable(ActionContext context) {
            double amount = amount(context);
            return amount > 0 && context.wallet().balance(context.player().orElse(null), currency(context)) >= amount;
        }

        @Override
        public String describe(ActionContext context) {
            String currency = currency(context);
            String rendered = context.resolve(cost.amountTemplate()).strip();
            return currency.isEmpty() ? rendered : rendered + " " + currency;
        }

        private String currency(ActionContext context) {
            return context.resolve(cost.currencyTemplate()).strip();
        }

        // A template that resolves to something that is not a number cannot name a price, so it reads as
        // unaffordable rather than as free: run() then refuses loudly instead of taking an accidental zero.
        private double amount(ActionContext context) {
            Double parsed = number(context.resolve(cost.amountTemplate()));
            return parsed == null ? -1 : parsed;
        }
    }

    private record TakeItemAction(ItemCost cost) implements CostAction {

        @Override
        public void run(ActionContext context) {
            int amount = amount(context);
            String item = item(context);
            if (amount <= 0 || !context.itemStore().take(context.player().orElse(null), item, amount)) {
                throw new ActionCostException("cannot take " + describe(context));
            }
        }

        @Override
        public boolean affordable(ActionContext context) {
            int amount = amount(context);
            return amount > 0 && context.itemStore().count(context.player().orElse(null), item(context)) >= amount;
        }

        @Override
        public String describe(ActionContext context) {
            return amountText(context) + " " + item(context);
        }

        private String item(ActionContext context) {
            return context.resolve(cost.itemTemplate()).strip();
        }

        private String amountText(ActionContext context) {
            return context.resolve(cost.amountTemplate()).strip();
        }

        private int amount(ActionContext context) {
            Double parsed = number(context.resolve(cost.amountTemplate()));
            if (parsed == null || parsed < 1 || parsed > Integer.MAX_VALUE) {
                return -1;
            }
            return (int) Math.floor(parsed);
        }
    }

    private static @Nullable Double number(String raw) {
        try {
            double parsed = Double.parseDouble(raw.strip());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** The static structure of a {@code [sound]} payload: the key template plus a volume and pitch. */
    public record SoundSpec(String keyTemplate, float volume, float pitch) {

        /** Canonical constructor null-checks the key template; volume and pitch are plain floats. */
        public SoundSpec {
            Objects.requireNonNull(keyTemplate, "keyTemplate");
        }
    }
}
