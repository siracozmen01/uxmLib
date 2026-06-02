/**
 * A notify-only release update-checker. An {@link com.uxplima.uxmlib.update.UpdateProvider} SPI yields the
 * latest published {@link com.uxplima.uxmlib.update.Release} from a source (GitHub, Modrinth, …) over the
 * shared {@link java.net.http.HttpClient}, run off-thread via the library {@code Scheduler}. The checker
 * compares it against the build-time {@link com.uxplima.uxmlib.update.UxmLibVersion} with a semantic-version
 * ladder, announces once, and surfaces a permission-gated clickable join message. It never self-downloads.
 */
@NullMarked
package com.uxplima.uxmlib.update;

import org.jspecify.annotations.NullMarked;
