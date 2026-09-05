package com.uxplima.uxmlib.nametag.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.nametag.Alignment;
import com.uxplima.uxmlib.nametag.Appearance;
import com.uxplima.uxmlib.nametag.Billboard;
import com.uxplima.uxmlib.nametag.NametagPackets;
import com.uxplima.uxmlib.pipeline.PacketSender;
import com.uxplima.uxmlib.packet.Bundles;
import com.uxplima.uxmlib.packet.Codecs;
import com.uxplima.uxmlib.packet.Components;
import com.uxplima.uxmlib.packet.EntityIds;
import com.uxplima.uxmlib.packet.Reflect;
import com.uxplima.uxmlib.packet.VanillaEntityTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The sole NMS-bearing class of the nametag renderer: it builds the real Mojang-mapped packets that paint a
 * text {@code Display} per viewer and writes them through the connection. Quarantining {@code net.minecraft}
 * to one class follows the same precedent as {@code uxmlib-pipeline}'s {@code ChannelResolver}, which isolates the
 * unavoidable server-internal reach so the rest of the module stays pure and unit-testable against a fake.
 *
 * <p>Built against the Mojang-mapped 1.21.11 dev bundle; Paper's runtime remapper maps these back to the
 * server's own mappings at load. A handful of the {@code Display}/{@code TextDisplay} data-watcher accessors
 * the metadata packet needs are package-private static fields, so they are read once at construction through
 * the shared {@link Reflect} helper (the accessor object carries its own network id, which keeps us from
 * hard-coding the volatile integer indices). The generic machinery: accessor reflection, the bundle builder,
 * the Adventure-to-vanilla conversion, the stream-codec buffer trick, the entity-id allocator, now lives in
 * {@code uxmlib-packet} and is shared with the tablist renderer; only the {@code TextDisplay}-specific shape of
 * the metadata stays here.
 */
public final class NmsNametagPackets implements NametagPackets {

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
    private final EntityDataAccessor<Integer> interpolationDurationAccessor;
    private final EntityDataAccessor<Integer> interpolationStartDeltaAccessor;
    /** The registry's own text-display type, read once rather than named as a constant. */
    private final EntityType<?> textDisplayType;

    public NmsNametagPackets(PacketSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
        // Off the registry, not off a constant: the class the entity-type constants live on has been renamed
        // across Minecraft lines, while the registry key has not.
        this.textDisplayType = VanillaEntityTypes.of("text_display");
        // Read each accessor once here and hold it in a final field, keeping the reflection off every hot path.
        this.textAccessor = Reflect.accessor(Display.TextDisplay.class, "DATA_TEXT_ID");
        this.billboardAccessor = Reflect.accessor(Display.class, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
        this.backgroundAccessor = Reflect.accessor(Display.TextDisplay.class, "DATA_BACKGROUND_COLOR_ID");
        this.textOpacityAccessor = Reflect.accessor(Display.TextDisplay.class, "DATA_TEXT_OPACITY_ID");
        this.styleFlagsAccessor = Reflect.accessor(Display.TextDisplay.class, "DATA_STYLE_FLAGS_ID");
        this.lineWidthAccessor = Reflect.accessor(Display.TextDisplay.class, "DATA_LINE_WIDTH_ID");
        this.viewRangeAccessor = Reflect.accessor(Display.class, "DATA_VIEW_RANGE_ID");
        this.translationAccessor = Reflect.accessor(Display.class, "DATA_TRANSLATION_ID");
        this.scaleAccessor = Reflect.accessor(Display.class, "DATA_SCALE_ID");
        this.interpolationDurationAccessor =
                Reflect.accessor(Display.class, "DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID");
        this.interpolationStartDeltaAccessor =
                Reflect.accessor(Display.class, "DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID");
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
    public Object metadataPacket(
            int entityId, Component text, Appearance appearance, int opacity, Vector3f translation) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(translation, "translation");
        if (opacity < 0 || opacity > 255) {
            throw new IllegalArgumentException("opacity must be 0-255, was " + opacity);
        }
        return new ClientboundSetEntityDataPacket(entityId, dataValues(text, appearance, opacity, translation));
    }

    private List<SynchedEntityData.DataValue<?>> dataValues(
            Component text, Appearance appearance, int opacity, Vector3f translation) {
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(11);
        values.add(SynchedEntityData.DataValue.create(textAccessor, Components.asVanilla(text)));
        values.add(SynchedEntityData.DataValue.create(billboardAccessor, billboardId(appearance.billboard())));
        values.add(SynchedEntityData.DataValue.create(backgroundAccessor, appearance.backgroundArgb()));
        values.add(SynchedEntityData.DataValue.create(textOpacityAccessor, (byte) opacity));
        values.add(SynchedEntityData.DataValue.create(styleFlagsAccessor, styleFlags(appearance)));
        values.add(SynchedEntityData.DataValue.create(lineWidthAccessor, appearance.lineWidth()));
        values.add(SynchedEntityData.DataValue.create(viewRangeAccessor, appearance.viewRange()));
        values.add(SynchedEntityData.DataValue.create(translationAccessor, new Vector3f(translation)));
        values.add(SynchedEntityData.DataValue.create(scaleAccessor, appearance.scale()));
        values.add(SynchedEntityData.DataValue.create(
                interpolationDurationAccessor, appearance.interpolationDurationTicks()));
        // Start the lerp from this very tick so a transform change is applied immediately, not after a delay.
        values.add(SynchedEntityData.DataValue.create(interpolationStartDeltaAccessor, 0));
        return values;
    }

    /** The data-watcher byte for the billboard mode, matching vanilla {@code Display.BillboardConstraints}. */
    private static byte billboardId(Billboard billboard) {
        return switch (billboard) {
            case FIXED -> (byte) 0;
            case VERTICAL -> (byte) 1;
            case HORIZONTAL -> (byte) 2;
            case CENTER -> (byte) 3;
        };
    }

    /** OR together the {@code TextDisplay} style bits the appearance selects. */
    private static byte styleFlags(Appearance appearance) {
        byte flags = 0;
        if (appearance.textShadow()) {
            flags |= Display.TextDisplay.FLAG_SHADOW;
        }
        if (appearance.seeThrough()) {
            flags |= Display.TextDisplay.FLAG_SEE_THROUGH;
        }
        if (appearance.alignment() == Alignment.LEFT) {
            flags |= Display.TextDisplay.FLAG_ALIGN_LEFT;
        } else if (appearance.alignment() == Alignment.RIGHT) {
            flags |= Display.TextDisplay.FLAG_ALIGN_RIGHT;
        }
        return flags;
    }

    @Override
    public Object mountPacket(int vehicleEntityId, int[] passengerIds) {
        Objects.requireNonNull(passengerIds, "passengerIds");
        // The packet exposes only an Entity constructor and a private buffer one, so seat the riders through
        // the public stream codec: write the wire form (vehicle id, then the passenger id array) and decode.
        int[] passengers = passengerIds.clone();
        return Codecs.decodeVia(ClientboundSetPassengersPacket.STREAM_CODEC, buffer -> {
            buffer.writeVarInt(vehicleEntityId);
            buffer.writeVarIntArray(passengers);
        });
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
