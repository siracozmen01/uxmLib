package com.uxplima.uxmlib.hologram;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

/**
 * A {@link Hologram} backed by a live {@link TextDisplay}. Text and position changes go straight to the
 * native entity; per-viewer visibility uses Paper's {@code show/hideEntity} over a tracked allowed-viewer
 * set, so a hologram can be shown to some players and not others without any packets.
 */
final class DisplayHologram implements Hologram {

    private final TextDisplay display;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    DisplayHologram(TextDisplay display) {
        this.display = Objects.requireNonNull(display, "display");
    }

    @Override
    public void setText(Component text) {
        display.text(Objects.requireNonNull(text, "text"));
    }

    @Override
    public void moveTo(Location to, int interpolationTicks) {
        Objects.requireNonNull(to, "to");
        if (interpolationTicks < 0) {
            throw new IllegalArgumentException("interpolationTicks must be >= 0");
        }
        display.setTeleportDuration(interpolationTicks);
        display.teleport(to);
    }

    @Override
    public void restrictToViewers() {
        display.setVisibleByDefault(false);
    }

    @Override
    public void show(Plugin plugin, Player viewer) {
        Objects.requireNonNull(plugin, "plugin");
        viewers.add(viewer.getUniqueId());
        viewer.showEntity(plugin, display);
    }

    @Override
    public void hide(Plugin plugin, Player viewer) {
        Objects.requireNonNull(plugin, "plugin");
        viewers.remove(viewer.getUniqueId());
        viewer.hideEntity(plugin, display);
    }

    @Override
    public boolean isVisibleTo(Player viewer) {
        return viewers.contains(viewer.getUniqueId());
    }

    @Override
    public void remove() {
        display.remove();
    }

    @Override
    public TextDisplay entity() {
        return display;
    }
}
