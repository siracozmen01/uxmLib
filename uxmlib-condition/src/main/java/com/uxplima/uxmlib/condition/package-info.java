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
 * no economy plugin and fixes no price. The module depends only on {@code uxmlib-common} (its {@code Text}
 * seam for rendering messages).
 */
@NullMarked
package com.uxplima.uxmlib.condition;

import org.jspecify.annotations.NullMarked;
