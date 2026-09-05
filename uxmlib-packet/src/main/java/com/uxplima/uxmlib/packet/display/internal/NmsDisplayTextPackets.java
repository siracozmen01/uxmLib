package com.uxplima.uxmlib.packet.display.internal;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.pipeline.PacketSender;
import com.uxplima.uxmlib.packet.Components;
import com.uxplima.uxmlib.packet.Reflect;
import com.uxplima.uxmlib.packet.display.DisplayTextPackets;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;

/**
 * The sole NMS-bearing class of the per-viewer text-override layer: it builds the real Mojang-mapped
 * {@code ClientboundSetEntityDataPacket} that overrides only the text component of an existing
 * {@code TextDisplay} for one viewer, and writes it through the connection. Quarantining {@code net.minecraft}
 * to one class follows the same precedent as the nametag renderer's {@code NmsNametagPackets} and the tab-list
 * {@code NmsTabListPackets}, which isolate the unavoidable server-internal reach so the rest of the module stays
 * pure and unit-testable against a fake.
 *
 * <p>Built against the Mojang-mapped 1.21.11 dev bundle; Paper's runtime remapper maps these back to the
 * server's own mappings at load. The {@code Display.TextDisplay#DATA_TEXT_ID} data-watcher accessor is a
 * package-private static field, so it is read once at construction through the shared {@link Reflect} helper
 * (the accessor object carries its own network id, which keeps us off the volatile integer index). The packet
 * carries that one data value only, so sending it to a viewer changes their copy of the entity's text and
 * nothing else: the shared spawn's billboard, background, scale and the rest stay as every other viewer has
 * them.
 */
public final class NmsDisplayTextPackets implements DisplayTextPackets {

    private final PacketSender sender;
    private final EntityDataAccessor<net.minecraft.network.chat.Component> textAccessor;

    public NmsDisplayTextPackets(PacketSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
        // Read the text accessor once here and hold it in a final field, keeping the reflection off every send.
        this.textAccessor = Reflect.accessor(Display.TextDisplay.class, "DATA_TEXT_ID");
    }

    @Override
    public Object textOverride(int entityId, Component text) {
        Objects.requireNonNull(text, "text");
        SynchedEntityData.DataValue<?> value =
                SynchedEntityData.DataValue.create(textAccessor, Components.asVanilla(text));
        return new ClientboundSetEntityDataPacket(entityId, List.of(value));
    }

    @Override
    public void send(Player viewer, Object packet) {
        sender.send(viewer, packet);
    }
}
