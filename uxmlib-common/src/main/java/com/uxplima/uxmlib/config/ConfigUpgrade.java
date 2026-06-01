package com.uxplima.uxmlib.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The upgrade operations on a live config tree — schema migration and defaults merge — kept out of
 * {@code HoconConfig} so that class stays within its size cap. Each takes the live root and a save
 * callback; the caller (HoconConfig) holds the lock and supplies them.
 */
final class ConfigUpgrade {

    private ConfigUpgrade() {}

    /** Replay only newer migration steps, save once if the version changed, return the resulting version. */
    static int migrate(CommentedConfigurationNode live, ConfigMigration migration, Runnable save) {
        int from = migration.versionOf(live);
        int to = migration.apply(live);
        if (to != from) {
            save.run();
        }
        return to;
    }

    /** Additively merge {@code defaults} into {@code live}, save once if anything was added; return whether. */
    static boolean mergeDefaults(CommentedConfigurationNode live, ConfigurationNode defaults, Runnable save) {
        int before = ConfigDefaults.nodeCount(live);
        live.mergeFrom(defaults);
        if (ConfigDefaults.nodeCount(live) != before) {
            save.run();
            return true;
        }
        return false;
    }
}
