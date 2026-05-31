/**
 * Storage plumbing: a HikariCP-backed connection source with a Caffeine cache seam and small SQL
 * helpers, so a consuming plugin gets pooled, cached persistence without re-deriving the boilerplate.
 */
@NullMarked
package com.uxplima.uxmlib.storage;

import org.jspecify.annotations.NullMarked;
