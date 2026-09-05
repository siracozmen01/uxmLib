package com.uxplima.uxmlib.menu;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.jspecify.annotations.NullMarked;

/**
 * Reports, once per layout, that a list did not fit the slots it had to be drawn in.
 *
 * <p>Five places in the engine do that: an editor's properties, a list property's entries, an enum property's
 * options, a colour property's palette, and the stacks a content provider hands back for its region. Each stops at
 * the shorter of the two, which is the only thing it can do, because a slot that does not exist cannot be painted.
 * What none of them did was say so.
 *
 * <p>The message names no culprit, deliberately. In four of the five the short side is the operator's layout and the
 * fix is to add slots. In the fifth the long side is a {@code ContentProvider}'s return value, which reaches the
 * engine through the developer API, so the same overflow can equally mean a plugin returned more stacks than the
 * region has room for. What is true in every case is that there are more things than places, so that is what it says,
 * and it names both fixes rather than choosing between them.
 *
 * <p>Fewer things than slots is silent and always will be. A spare slot is not a mistake, and a report that cannot
 * tell a mistake from a margin is one operators learn to skip.
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
            LOG.warning(what + ": " + items + " to draw but only " + slots + " slots to draw them in, so "
                    + (items - slots) + " are not shown. Either the list is longer than intended, or the layout"
                    + " needs more slots.");
        }
        return slots;
    }
}
