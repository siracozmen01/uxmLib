package com.uxplima.uxmlib.hologram;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.entity.Interaction;

/**
 * A text hologram with a native {@link Interaction} entity over it, so it can be clicked. Both entities
 * are spawned together; removing the clickable removes both. Create one through
 * {@link HologramInteractions#clickable} so its clicks are routed. The interaction box is sized in blocks
 * by the {@code width}/{@code height} given at spawn.
 */
public final class ClickableHologram {

    private final Hologram hologram;
    private final Interaction interaction;

    private ClickableHologram(Hologram hologram, Interaction interaction) {
        this.hologram = hologram;
        this.interaction = interaction;
    }

    /** Wrap an already-spawned text hologram and interaction box. Package-private (spawn + tests). */
    static ClickableHologram of(Hologram hologram, Interaction interaction) {
        Objects.requireNonNull(hologram, "hologram");
        Objects.requireNonNull(interaction, "interaction");
        return new ClickableHologram(hologram, interaction);
    }

    static ClickableHologram spawn(HologramSpec spec, Location location, float width, float height) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(location, "location");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        Objects.requireNonNull(location.getWorld(), "location world");
        Hologram text = Holograms.spawn(spec, location);
        Interaction box = location.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(width);
            entity.setInteractionHeight(height);
            entity.setResponsive(true);
            Markers.stamp(entity);
        });
        return of(text, box);
    }

    /** The text hologram (move/restyle it through this). */
    public Hologram text() {
        return hologram;
    }

    /** The backing interaction entity (its UUID keys the click router). */
    public Interaction interaction() {
        return interaction;
    }

    /** The current click-box width in blocks. */
    public float width() {
        return interaction.getInteractionWidth();
    }

    /** The current click-box height in blocks. */
    public float height() {
        return interaction.getInteractionHeight();
    }

    /**
     * Resize the live click box to {@code width} by {@code height} blocks, so the clickable region can be
     * grown past the text footprint (the text rarely matches where players expect to click) without a
     * re-spawn. Must run on the interaction entity's region thread (Folia); route it through your scheduler.
     */
    public void resize(float width, float height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        interaction.setInteractionWidth(width);
        interaction.setInteractionHeight(height);
    }

    /** Despawn both the text and the interaction entity. */
    public void remove() {
        hologram.remove();
        interaction.remove();
    }
}
