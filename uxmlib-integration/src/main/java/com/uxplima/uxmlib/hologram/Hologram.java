package com.uxplima.uxmlib.hologram;

/**
 * A spawned hologram backed by a native Display entity. Lifecycle and per-line content land with the
 * hologram module's first feature pass; this contract names the removal seam.
 */
public interface Hologram {

    /** Despawn the backing Display entity and release the hologram. */
    void remove();
}
