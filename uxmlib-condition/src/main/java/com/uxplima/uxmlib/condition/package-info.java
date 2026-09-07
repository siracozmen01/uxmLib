/**
 * A declarative condition engine. The reusable core is {@link com.uxplima.uxmlib.condition.Comparison}: a
 * compact operator (==, !=, &gt;=, &gt;, &lt;=, &lt;) over two operand strings that compares numerically when
 * both operands parse as numbers and falls back to string equality otherwise. {@link
 * com.uxplima.uxmlib.condition.Condition} is the SPI ({@code boolean test(ConditionRequest)}); {@link
 * com.uxplima.uxmlib.condition.PlaceholderCondition} resolves two operand templates through an injected
 * {@link com.uxplima.uxmlib.condition.OperandResolver} seam and applies a {@code Comparison}: the resolver
 * is a plain function, never a dependency on the integration/PAPI module. {@link
 * com.uxplima.uxmlib.condition.ConditionList} AND-combines conditions, honours each condition's {@link
 * com.uxplima.uxmlib.condition.FailurePolicy}, and flushes collected failure messages to the request's error
 * sink. {@link com.uxplima.uxmlib.condition.MoneyCondition} and {@link
 * com.uxplima.uxmlib.condition.ItemCondition} ask the same shape of question about money and items, each
 * through its own injected seam ({@link com.uxplima.uxmlib.condition.Wallet}, {@link
 * com.uxplima.uxmlib.condition.ItemStore}) for the same reason the operand resolver is one: the engine names
 * no economy plugin and fixes no price. {@link com.uxplima.uxmlib.condition.wallet} then ships the backends
 * that fill the wallet seam. The ones that talk to another plugin are soft behind a plugin-present guard, so
 * a consumer wires an economy without writing the reflection for it and a server without that economy loads
 * none of its code; the one that counts the player's own experience needs no plugin and so no guard, and
 * says so.
 *
 * <p>The module depends only on {@code uxmlib-common} (its {@code Text} seam for rendering messages).
 * Treasury and PlaceholderAPI are {@code compileOnly}: they reach neither the published POM nor the jar,
 * and nothing outside a guarded method names either of them.
 */
@NullMarked
package com.uxplima.uxmlib.condition;

import org.jspecify.annotations.NullMarked;
