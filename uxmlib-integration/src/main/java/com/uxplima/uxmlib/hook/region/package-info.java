/**
 * Region / protection-hook integrations. {@link com.uxplima.uxmlib.hook.region.RegionService} is the
 * provider-agnostic contract a plugin queries ("may this player build here?", "what regions cover this
 * point?"); {@link com.uxplima.uxmlib.hook.region.RegionHooks} picks the first present provider so a
 * caller never names WorldGuard or Towny directly. The
 * {@link com.uxplima.uxmlib.hook.region.WorldGuardRegionService} and
 * {@link com.uxplima.uxmlib.hook.region.TownyRegionService} adapters touch their third-party classes only
 * past a presence guard, so a server without the plugin still loads.
 */
@NullMarked
package com.uxplima.uxmlib.hook.region;

import org.jspecify.annotations.NullMarked;
