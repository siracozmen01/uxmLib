package com.uxplima.uxmlib.condition.wallet;

import java.util.Optional;

/**
 * Where the object behind an {@link EconomyBinding} comes from.
 *
 * <p>It is a seam so that a test can hand the reader an object of its own shaped like the real one. The
 * shipped implementation asks the server, and asks the plugin manager first.
 */
public interface EconomyProviders {

    /** The object that answers for {@code binding}, when its plugin is here and it can be reached. */
    Optional<Object> provider(EconomyBinding binding);

    /**
     * The service one plugin registered under one class, when that plugin is on the server.
     *
     * <p>It is here for the economy no description reaches, such as Treasury. The class is named as text,
     * so a server without that plugin never loads it and the wallet simply answers with nothing.
     */
    Optional<Object> service(String pluginName, String className);
}
