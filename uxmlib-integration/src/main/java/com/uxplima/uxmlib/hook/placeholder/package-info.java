/**
 * PlaceholderAPI expansion registration. A consumer adds a
 * {@link com.uxplima.uxmlib.hook.placeholder.PlaceholderProvider} to a
 * {@link com.uxplima.uxmlib.hook.placeholder.PlaceholderRegistry} to expose {@code %uxm_<prefix>_<params>%}
 * placeholders; {@link com.uxplima.uxmlib.hook.placeholder.PlaceholderExpansions} registers one shared
 * internal expansion that routes requests by longest-prefix match, but only once PlaceholderAPI is present.
 * The {@code me.clip} classes are reached only past that presence guard, so a server without PlaceholderAPI
 * still loads. This is the write side of the PAPI integration; the read side lives in
 * {@link com.uxplima.uxmlib.hook.PlaceholderApi}.
 */
@NullMarked
package com.uxplima.uxmlib.hook.placeholder;

import org.jspecify.annotations.NullMarked;
