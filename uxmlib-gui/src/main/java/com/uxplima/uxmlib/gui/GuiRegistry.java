package com.uxplima.uxmlib.gui;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;

/**
 * Drives the animation/auto-refresh clock for every open menu that needs one. A single repeating task —
 * registered lazily on the first menu that ticks and cancelled when the last one stops — advances each
 * registered menu once per tick, rather than one task per menu. Menus register themselves on open (when
 * they have animated content or auto-refresh) and unregister on close, so an idle server runs no task.
 *
 * <p>Folia-safe: the shared timer runs on the global region, but each menu's actual re-render is routed
 * to its viewer's region thread by {@link AbstractGui#tick()} via the same {@link Scheduler}.
 */
final class GuiRegistry {

    private final Scheduler scheduler;
    private final Set<AbstractGui> ticking = ConcurrentHashMap.newKeySet();
    private @Nullable TaskHandle task;

    GuiRegistry(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** The scheduler backing this registry, so click handling can defer a slot action to the next tick. */
    Scheduler scheduler() {
        return scheduler;
    }

    /** Register {@code gui} with the installed registry if it needs ticking; a no-op if none is installed. */
    static void onOpen(AbstractGui gui) {
        GuiRegistry registry = Guis.registry();
        if (registry != null && gui.needsTicking()) {
            registry.register(gui);
        }
    }

    /** Unregister {@code gui} from the installed registry; a no-op if none is installed. */
    static void onClose(AbstractGui gui) {
        GuiRegistry registry = Guis.registry();
        if (registry != null) {
            registry.unregister(gui);
        }
    }

    /** Begin ticking {@code gui}; starts the shared timer if it was not already running. */
    synchronized void register(AbstractGui gui) {
        ticking.add(gui);
        if (task == null) {
            task = scheduler.globalTimer(Duration.ZERO, Duration.ofMillis(50L), handle -> tickAll());
        }
    }

    /** Stop ticking {@code gui}; cancels the shared timer once nothing is left to tick. */
    synchronized void unregister(AbstractGui gui) {
        ticking.remove(gui);
        if (ticking.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickAll() {
        for (AbstractGui gui : ticking) {
            gui.tick();
        }
    }
}
