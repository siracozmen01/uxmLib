/**
 * "Which claim covers this block, and does this player own it." One question, nineteen claim plugins, one
 * answer.
 *
 * <p>{@link com.uxplima.uxmlib.claim.ClaimProvider} is the port a consumer depends on, and it names no claim
 * plugin. {@link com.uxplima.uxmlib.claim.ClaimProviders} discovers which of the nineteen are installed and
 * folds every one the operator left enabled into a {@code CompositeClaimProvider}, so a server running Lands
 * and WorldGuard together has both consulted and their answers combined per
 * {@link com.uxplima.uxmlib.claim.ClaimProvidersConfig.CombineMode}. With none installed the discoverer
 * returns an inactive provider and every gate built on it opens.
 *
 * <p>Two integration styles coexist. <b>Typed</b> providers, Lands, GriefPrevention, SimpleClaimSystem and
 * RClaim, depend on a {@code compileOnly} API jar, so their SDK types are absent from the runtime and test
 * classpaths; each reaches its SDK only past a plugin-present guard, and the discoverer constructs a typed
 * provider only once that plugin is known to be loaded, so a server (or test) without the plugin never loads
 * the SDK classes. <b>Reflective</b> providers, the remaining fifteen, have no compile dependency at all and
 * reach their API purely by reflection: GriefDefender publishes only a GC-prone commit-pinned JitPack build,
 * ExcellentClaims is premium with no public coordinate, XClaim's JitPack build fails, Homestead ships only to
 * authenticated GitHub Packages, and the rest are bound through a stable static entry point ({@code TownyAPI},
 * {@code BentoBox.getInstance()}, {@code WorldGuard.getInstance()}) rather than a pinned jar, so a pinned
 * verifiable {@code compileOnly} dependency is either not available or not warranted for any of them.
 *
 * <p>Three rules bind every provider here, and they are what makes the set safe to ship in a library:
 *
 * <ul>
 *   <li>every typed third-party reference sits inside a method behind an {@code active()} guard that only
 *       consults the plugin manager, so constructing a provider and asking whether it is active loads nothing;
 *   <li>the SDK handle resolves lazily on first use, again past the guard, and is cached;
 *   <li>any {@link java.lang.Throwable} from a missing or incompatible API degrades to inactive or unclaimed
 *       rather than propagating. A claim gate that crashes is worse than one that opens.
 * </ul>
 *
 * <p>Every lookup is pure-datastore: claims are resolved from in-memory state by world plus block or chunk
 * coordinate without loading or reading a chunk, so a proximity scan stays safe on the region thread and on
 * Folia.
 */
@NullMarked
package com.uxplima.uxmlib.claim;

import org.jspecify.annotations.NullMarked;
