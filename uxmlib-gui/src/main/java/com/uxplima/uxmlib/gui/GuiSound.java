package com.uxplima.uxmlib.gui;

import org.bukkit.entity.HumanEntity;

import net.kyori.adventure.sound.Sound;

import org.jspecify.annotations.Nullable;

/**
 * The optional click and open feedback sounds for a menu. Either may be {@code null} to play nothing.
 * Holding both in one record keeps {@code AbstractGui} to a single field and the play logic in one place.
 */
record GuiSound(@Nullable Sound onClick, @Nullable Sound onOpen) {

    static final GuiSound NONE = new GuiSound(null, null);

    /** Play the click sound to {@code viewer}, if one is set. */
    void playClick(HumanEntity viewer) {
        if (onClick != null) {
            viewer.playSound(onClick);
        }
    }

    /** Play the open sound to {@code viewer}, if one is set. */
    void playOpen(HumanEntity viewer) {
        if (onOpen != null) {
            viewer.playSound(onOpen);
        }
    }
}
