package com.uxplima.uxmlib.hook.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects the region provider to use among the candidates registered with it. A caller adds the adapters it
 * supports in priority order; {@link #active()} returns the first whose backing plugin is present (its
 * {@link RegionService#isAvailable()} is {@code true}), so a server with WorldGuard answers through
 * WorldGuard, one with only Towny through Towny, and one with neither degrades to empty. An instance, not
 * static state, so each plugin owns its own selection.
 */
public final class RegionHooks {

    private final List<RegionService> candidates = new ArrayList<>();

    /** Register {@code service} as a candidate provider; earlier registrations win. Returns this. */
    public RegionHooks register(RegionService service) {
        candidates.add(Objects.requireNonNull(service, "service"));
        return this;
    }

    /** The first present provider in registration order, or empty when none of the candidates is available. */
    public Optional<RegionService> active() {
        for (RegionService service : candidates) {
            if (service.isAvailable()) {
                return Optional.of(service);
            }
        }
        return Optional.empty();
    }

    /** Whether any registered provider is currently present. */
    public boolean hasProvider() {
        return active().isPresent();
    }
}
