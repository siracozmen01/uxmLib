package com.uxplima.uxmlib.condition;

import java.util.Objects;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * "Does this player hold at least N of item Y". The count comes from the request's injected {@link
 * ItemStore}, exactly as {@link PlaceholderCondition} takes its operands from the request's {@link
 * OperandResolver}: the condition names no inventory API of its own and this module gains no dependency it
 * did not already have.
 *
 * <p>Both sides are templates resolved through the request's {@link OperandResolver} before the comparison
 * runs, so the item and the amount may each be a placeholder. The resolved count is then compared with the
 * resolved amount under the ordinary {@link Comparison} rules, so {@code == 0} reads "holds none" just as
 * readably as {@code >= 3} reads "holds three".
 */
public final class ItemCondition implements Condition {

    private final String itemTemplate;
    private final Comparison comparison;
    private final String amountTemplate;

    private ItemCondition(String itemTemplate, Comparison comparison, String amountTemplate) {
        this.itemTemplate = itemTemplate;
        this.comparison = comparison;
        this.amountTemplate = amountTemplate;
    }

    /** A condition comparing the held count of {@code itemTemplate} against {@code amountTemplate}. */
    public static ItemCondition of(String itemTemplate, Operator operator, String amountTemplate) {
        Objects.requireNonNull(itemTemplate, "itemTemplate");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(amountTemplate, "amountTemplate");
        return new ItemCondition(itemTemplate, Comparison.of(operator), amountTemplate);
    }

    /** The common case: the player must hold at least {@code amountTemplate} of the item. */
    public static ItemCondition atLeast(String itemTemplate, String amountTemplate) {
        return of(itemTemplate, Operator.GREATER_OR_EQUAL, amountTemplate);
    }

    /**
     * Parse a whole {@code <item> <op> <amount>} expression, such as {@code "diamond >= 3"}. The left side is
     * handed to the {@link ItemStore} untouched, so whatever vocabulary that store speaks is what an operator
     * writes here. Throws {@link IllegalArgumentException} if no known operator appears.
     */
    public static ItemCondition parse(String expression) {
        Objects.requireNonNull(expression, "expression");
        Comparison.ParsedComparison parsed = Comparison.parse(expression);
        return new ItemCondition(parsed.left(), parsed.comparison(), parsed.right());
    }

    /** The unresolved item template. */
    public String itemTemplate() {
        return itemTemplate;
    }

    /** The unresolved amount template. */
    public String amountTemplate() {
        return amountTemplate;
    }

    /** The operator this condition compares under. */
    public Operator operator() {
        return comparison.operator();
    }

    @Override
    public boolean test(ConditionRequest request) {
        Objects.requireNonNull(request, "request");
        @Nullable Player player = request.player().orElse(null);
        OperandResolver resolver = request.resolver();
        String item = resolver.resolve(player, itemTemplate).strip();
        String amount = resolver.resolve(player, amountTemplate);
        int held = request.itemStore().count(player, item);
        return comparison.test(Integer.toString(held), amount);
    }
}
