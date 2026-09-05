package com.uxplima.uxmlib.menu;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.jspecify.annotations.NullMarked;

/**
 * Reports, once per layout, that a list did not fit the slots an operator gave it.
 *
 * <p>Four places in the engine draw a code-sized list into an operator-sized set of slots: an editor's properties, a
 * list property's entries, an enum property's options, and a colour property's palette. Each stops at the shorter of
 * the two, which is the only thing it can do, because a slot that does not exist cannot be painted. What none of them
 * did was say so, and the operator's side is always the shorter one, so the whole class of miss looked to them like
 * nothing happening.
 *
 * <p>Once per layout rather than once per draw, and keyed by the layout's <em>value</em>. An editor repaints on every
 * click and a picker reopens on every change, and the property objects themselves are rebuilt per draw, so neither a
 * per-draw report nor a per-object one would do: the first floods, the second never suppresses. The layout is what an
 * operator would edit to fix it, and it is a record, so two windows sharing a layout report once between them and two
 * different layouts truncating the same list report separately. That also keeps this testable: a test names its own
 * layout and is unaffected by what any other test reported.
 *
 * <p>This is a mechanism and not a look. It writes to the engine's own logger and decides nothing a viewer sees: the
 * drawing code still draws what fits.
 */
@NullMarked
public final class SlotFit {

    private static final Logger LOG = Logger.getLogger(SlotFit.class.getName());

    /** What has already been reported, so a window that redraws every tick reports once rather than every tick. */
    private static final Set<Reported> REPORTED = ConcurrentHashMap.newKeySet();

    /** One report's identity: which list, in which layout. Value equality, so it survives the objects being rebuilt. */
    private record Reported(String what, Object layout) {}

    private SlotFit() {}

    /**
     * How many items fit, warning once per layout when some do not. Callers loop to this count rather than taking the
     * smaller of the two sizes themselves, so a site that keeps drawing cannot quietly stop reporting.
     *
     * @param items how many things there are to draw
     * @param slots how many slots the operator's layout offers
     * @param what what is being drawn, named as an operator would recognise it, for example "editor properties"
     * @param layout the operator-written layout the slots came from: the thing they would edit to fix it
     */
    public static int fit(int items, int slots, String what, Object layout) {
        if (items <= slots) {
            return items;
        }
        if (REPORTED.add(new Reported(what, layout))) {
            LOG.warning(what + ": " + items + " to draw but the layout offers " + slots + " slots, so "
                    + (items - slots) + " are not shown. Give the layout more slots to see them.");
        }
        return slots;
    }
}
