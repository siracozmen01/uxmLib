/**
 * Permission and rank integrations: {@link com.uxplima.uxmlib.hook.permission.VaultPermission} reads and
 * edits permissions through Vault provider-agnostically, and
 * {@link com.uxplima.uxmlib.hook.permission.LuckPermsHook} reads a player's prefix/suffix/group/meta from
 * LuckPerms. Both are present-guarded, so a server without the plugin still loads.
 */
@NullMarked
package com.uxplima.uxmlib.hook.permission;

import org.jspecify.annotations.NullMarked;
