package com.uxplima.uxmlib.condition.action;

/**
 * An {@link Action} that takes something away from the subject rather than giving it: {@code [take-money]}
 * and {@code [take-item]}. It exists so an {@link ActionList} can ask the whole list what it will cost before
 * it runs any of it.
 *
 * <p>Two rules bind an implementation, and both are about never leaving a player half-charged:
 *
 * <ul>
 *   <li>{@link #affordable(ActionContext)} reads only. It answers whether the cost can be met right now and
 *       must take nothing, so a caller may ask it as often as it likes.
 *   <li>{@link #run(ActionContext)} is all or nothing. It either takes the whole cost or takes none of it and
 *       throws {@link ActionCostException}. A partial take followed by a failure is the defect this contract
 *       exists to forbid.
 * </ul>
 */
public interface CostAction extends Action {

    /** Whether the context's subject can meet this cost right now. Takes nothing. */
    boolean affordable(ActionContext context);

    /** This cost as an operator would read it, resolved against the context, for a failure message. */
    String describe(ActionContext context);
}
