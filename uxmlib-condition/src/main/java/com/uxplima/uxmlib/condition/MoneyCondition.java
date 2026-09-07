package com.uxplima.uxmlib.condition;

import java.util.Objects;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * "Does this player hold at least N of currency X". The balance comes from the request's injected {@link
 * Wallet}, exactly as {@link PlaceholderCondition} takes its operands from the request's {@link
 * OperandResolver}: the condition names no economy plugin and this module gains no dependency on one.
 *
 * <p>Both sides are templates resolved through the request's {@link OperandResolver} before the comparison
 * runs, so the currency and the amount may each be a placeholder. The resolved balance is then compared with
 * the resolved amount under the ordinary {@link Comparison} rules, which makes every operator available and
 * not only "at least": {@code < 100} reads "cannot afford" and {@code == 0} reads "broke".
 *
 * <p>An empty currency means the wallet's own default currency, which is what a single-currency economy has.
 * {@code parse(">= 100")} is therefore the plain form, and {@code parse("coins >= 100")} names a pool.
 */
public final class MoneyCondition implements Condition {

    private final String currencyTemplate;
    private final Comparison comparison;
    private final String amountTemplate;

    private MoneyCondition(String currencyTemplate, Comparison comparison, String amountTemplate) {
        this.currencyTemplate = currencyTemplate;
        this.comparison = comparison;
        this.amountTemplate = amountTemplate;
    }

    /** A condition comparing the balance in {@code currencyTemplate} against {@code amountTemplate}. */
    public static MoneyCondition of(String currencyTemplate, Operator operator, String amountTemplate) {
        Objects.requireNonNull(currencyTemplate, "currencyTemplate");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(amountTemplate, "amountTemplate");
        return new MoneyCondition(currencyTemplate, Comparison.of(operator), amountTemplate);
    }

    /** The common case: the balance must be at least {@code amountTemplate}. */
    public static MoneyCondition atLeast(String currencyTemplate, String amountTemplate) {
        return of(currencyTemplate, Operator.GREATER_OR_EQUAL, amountTemplate);
    }

    /**
     * Parse a whole {@code <currency> <op> <amount>} expression, such as {@code "coins >= 100"}. The left
     * side is the currency and may be empty ({@code ">= 100"}), which names the wallet's default currency.
     * Throws {@link IllegalArgumentException} if no known operator appears.
     */
    public static MoneyCondition parse(String expression) {
        Objects.requireNonNull(expression, "expression");
        Comparison.ParsedComparison parsed = Comparison.parse(expression);
        return new MoneyCondition(parsed.left(), parsed.comparison(), parsed.right());
    }

    /** The unresolved currency template; empty names the wallet's default currency. */
    public String currencyTemplate() {
        return currencyTemplate;
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
        String currency = resolver.resolve(player, currencyTemplate).strip();
        String amount = resolver.resolve(player, amountTemplate);
        double balance = request.wallet().balance(player, currency);
        return comparison.test(Double.toString(balance), amount);
    }
}
