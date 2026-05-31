package com.uxplima.uxmlib.hook;

/**
 * A soft integration with another plugin. An implementation resolves its target lazily and reports
 * whether it is currently available, so callers degrade gracefully when the plugin is absent.
 */
public interface PluginHook {

    /** The plugin name this hook binds to (as it appears in {@code paper-plugin.yml}). */
    String pluginName();

    /** Whether the target plugin is present and this hook is live. */
    boolean isAvailable();
}
