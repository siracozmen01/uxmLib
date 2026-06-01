/**
 * Soft-dependency hooks. {@link com.uxplima.uxmlib.hook.Hooks} answers whether a plugin is present and
 * {@link com.uxplima.uxmlib.hook.HookRegistry} binds hooks lazily (even ones whose plugin enables after
 * us); {@link com.uxplima.uxmlib.hook.Placeholders} bridges PlaceholderAPI, and
 * {@link com.uxplima.uxmlib.hook.PluginHook} is the shared contract. The economy and permission
 * integrations live in the {@code economy} and {@code permission} sub-packages. Each third-party symbol
 * is reached only past its presence guard, so a server without the plugin still loads.
 */
@NullMarked
package com.uxplima.uxmlib.hook;

import org.jspecify.annotations.NullMarked;
