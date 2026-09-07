package com.uxplima.uxmlib.packet.display.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.packet.Bundles;
import com.uxplima.uxmlib.packet.Components;
import com.uxplima.uxmlib.packet.EntityIds;
import com.uxplima.uxmlib.packet.Reflect;
import com.uxplima.uxmlib.packet.VanillaEntityTypes;
import com.uxplima.uxmlib.packet.display.HologramAppearance;
import com.uxplima.uxmlib.packet.display.HologramPackets;
import com.uxplima.uxmlib.pipeline.PacketSender;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The sole NMS-bearing class of the packet hologram: it builds the real Mojang-mapped add-entity, set-entity-data
 * and remove-entities packets that put a text display in a client and never on the server. Quarantining
 * {@code net.minecraft} to one class follows the precedent of {@code NmsNametagPackets} and
 * {@code NmsDisplayTextPackets}, so everything above the port stays pure and unit-testable against a fake.
 *
 * <p>Built against the Mojang-mapped dev bundle; Paper's runtime remapper maps these back to the server's own
 * mappings at load. The {@code Display} and {@code Display.TextDisplay} data-watcher accessors are
 * package-private static fields, so they are read once at construction through the shared {@link Reflect}
 * helper: the accessor object carries its own network id, which keeps us off the volatile integer indices.
 */
public final class NmsHologramPackets implements HologramPackets {

    private final PacketSender sender;

    private final EntityDataAccessor<net.minecraft.network.chat.Component> textAccessor;
    private final EntityDataAccessor<Byte> billboardAccessor;
    private final EntityDataAccessor<Integer> backgroundAccessor;
    private final EntityDataAccessor<Byte> textOpacityAccessor;
    private final EntityDataAccessor<Byte> styleFlagsAccessor;
    private final EntityDataAccessor<Integer> lineWidthAccessor;
    private final EntityDataAccessor<Float> viewRangeAccessor;
    private final EntityDataAccessor<org.joml.Vector3fc> translationAccessor;
    private final EntityDataAccessor<org.joml.Vector3fc> scaleAccessor;
    /** The registry's own text-display type, read once rather than named as a constant. */
    private final EntityType<?> textDisplayType;

    public NmsHologramPackets(PacketSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
        // Off the registry, not off a constant: the class the entity-type constants live on has been renamed
        // across Minecraft lines, while the registry key has not.
        this.textDisplayType = VanillaEntityTypes.of("text_display");
        // Read each accessor once here and hold it in a final field, keeping the reflection off every send.
        this.textAccessor = Reflect.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_TEXT_ID");
        this.billboardAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
        this.backgroundAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_BACKGROUND_COLOR_ID");
        this.textOpacityAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_TEXT_OPACITY_ID");
        this.styleFlagsAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_STYLE_FLAGS_ID");
        this.lineWidthAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_LINE_WIDTH_ID");
        this.viewRangeAccessor = Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_VIEW_RANGE_ID");
        this.translationAccessor = Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_TRANSLATION_ID");
        this.scaleAccessor = Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_SCALE_ID");
    }

    @Override
    public int allocateEntityId() {
        return EntityIds.next();
    }

    @Override
    public Object spawnPacket(int entityId, double x, double y, double z) {
        return new ClientboundAddEntityPacket(
                entityId, new UUID(0L, entityId), x, y, z, 0.0f, 0.0f, textDisplayType, 0, Vec3.ZERO, 0.0);
    }

    @Override
    public Object metadataPacket(int entityId, Component text, HologramAppearance appearance, Vector3f translation) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(translation, "translation");
        return new ClientboundSetEntityDataPacket(entityId, dataValues(text, appearance, translation));
    }

    private List<SynchedEntityData.DataValue<?>> dataValues(
            Component text, HologramAppearance appearance, Vector3f translation) {
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(9);
        values.add(SynchedEntityData.DataValue.create(textAccessor, Components.asVanilla(text)));
        values.add(SynchedEntityData.DataValue.create(billboardAccessor, billboardId(appearance.billboard())));
        values.add(SynchedEntityData.DataValue.create(backgroundAccessor, appearance.backgroundArgb()));
        values.add(SynchedEntityData.DataValue.create(textOpacityAccessor, (byte) appearance.textOpacity()));
        values.add(SynchedEntityData.DataValue.create(styleFlagsAccessor, styleFlags(appearance)));
        values.add(SynchedEntityData.DataValue.create(lineWidthAccessor, appearance.lineWidth()));
        values.add(SynchedEntityData.DataValue.create(viewRangeAccessor, appearance.viewRange()));
        values.add(SynchedEntityData.DataValue.create(translationAccessor, new Vector3f(translation)));
        values.add(SynchedEntityData.DataValue.create(scaleAccessor, appearance.scale()));
        return values;
    }

    /** The data-watcher byte for the billboard mode, matching vanilla {@code Display.BillboardConstraints}. */
    private static byte billboardId(Display.Billboard billboard) {
        return switch (billboard) {
            case FIXED -> (byte) 0;
            case VERTICAL -> (byte) 1;
            case HORIZONTAL -> (byte) 2;
            case CENTER -> (byte) 3;
        };
    }

    /** OR together the {@code TextDisplay} style bits the appearance selects. */
    private static byte styleFlags(HologramAppearance appearance) {
        byte flags = 0;
        if (appearance.textShadow()) {
            flags |= net.minecraft.world.entity.Display.TextDisplay.FLAG_SHADOW;
        }
        if (appearance.seeThrough()) {
            flags |= net.minecraft.world.entity.Display.TextDisplay.FLAG_SEE_THROUGH;
        }
        if (appearance.alignment() == TextDisplay.TextAlignment.LEFT) {
            flags |= net.minecraft.world.entity.Display.TextDisplay.FLAG_ALIGN_LEFT;
        } else if (appearance.alignment() == TextDisplay.TextAlignment.RIGHT) {
            flags |= net.minecraft.world.entity.Display.TextDisplay.FLAG_ALIGN_RIGHT;
        }
        return flags;
    }

    @Override
    public Object removePacket(int[] entityIds) {
        Objects.requireNonNull(entityIds, "entityIds");
        return new ClientboundRemoveEntitiesPacket(entityIds.clone());
    }

    @Override
    public Object bundle(List<Object> packets) {
        return Bundles.of(packets);
    }

    @Override
    public void send(Player viewer, Object packet) {
        sender.send(viewer, packet);
    }
}
