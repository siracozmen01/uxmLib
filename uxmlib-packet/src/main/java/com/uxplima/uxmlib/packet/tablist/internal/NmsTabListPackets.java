package com.uxplima.uxmlib.packet.tablist.internal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.mojang.authlib.GameProfile;
import com.uxplima.uxmlib.pipeline.PacketSender;
import com.uxplima.uxmlib.packet.Components;
import com.uxplima.uxmlib.packet.GameProfiles;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoEntry;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoGameMode;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoPackets;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoValue;
import com.uxplima.uxmlib.packet.tablist.TabEntry;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * The sole NMS-bearing class of the tab-list layer: it builds the real Mojang-mapped player-info packets that
 * paint a per-viewer tab row and writes them through the connection. Quarantining {@code net.minecraft} to one
 * class follows the same precedent as {@code uxmlib-pipeline}'s {@code ChannelResolver} and the nametag renderer's
 * {@code NmsNametagPackets}, which isolate the unavoidable server-internal reach so the rest of the module
 * stays pure and unit-testable against a fake.
 *
 * <p>Built against the Mojang-mapped 1.21.11 dev bundle; Paper's runtime remapper maps these back to the
 * server's own mappings at load. The update packet is assembled through Paper's public
 * {@code ClientboundPlayerInfoUpdatePacket(EnumSet<Action>, List<Entry>)} constructor (added by
 * Paper's "Add Listing API for Player" patch): vanilla alone exposes only an {@code Entry}-from-{@code
 * ServerPlayer} path, so this public object constructor is the clean way to seat synthetic entries without the
 * stream-codec round-trip the {@code Codecs} helper is reserved for. The {@code Entry} record's nine
 * components are written in their declared order; the actions {@code EnumSet} tells the client which of them to
 * read, so unused components are filled with harmless defaults.
 */
public final class NmsTabListPackets implements TabListPackets, PlayerInfoPackets {

    private final PacketSender sender;

    public NmsTabListPackets(PacketSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    @Override
    public Object addOrUpdate(TabEntry entry) {
        return addOrUpdate(List.of(Objects.requireNonNull(entry, "entry").toPlayerInfoEntry()));
    }

    @Override
    public Object addOrUpdate(List<PlayerInfoEntry> entries) {
        List<PlayerInfoEntry> values = List.copyOf(Objects.requireNonNull(entries, "entries"));
        EnumSet<Action> actions = EnumSet.of(
                Action.ADD_PLAYER,
                Action.UPDATE_LISTED,
                Action.UPDATE_LATENCY,
                Action.UPDATE_GAME_MODE,
                Action.UPDATE_DISPLAY_NAME,
                Action.UPDATE_LIST_ORDER,
                Action.UPDATE_HAT);
        List<Entry> built = new ArrayList<>(values.size());
        for (PlayerInfoEntry entry : values) {
            built.add(new Entry(
                    entry.id(),
                    profileFor(entry),
                    entry.listed(),
                    entry.latency(),
                    gameType(entry.gameMode()),
                    Components.asVanilla(entry.displayName()),
                    entry.showHat(),
                    entry.listOrder(),
                    null));
        }
        return packet(actions, built);
    }

    @Override
    public Object displayName(UUID id, Component name) {
        return displayNames(List.of(PlayerInfoValue.of(id, name)));
    }

    @Override
    public Object listOrder(UUID id, int order) {
        return listOrders(List.of(PlayerInfoValue.of(id, order)));
    }

    @Override
    public Object remove(List<UUID> ids) {
        return removeEntries(ids);
    }

    @Override
    public Object relist(List<UUID> ids, boolean listed) {
        Objects.requireNonNull(ids, "ids");
        List<PlayerInfoValue<Boolean>> values = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            values.add(PlayerInfoValue.of(id, listed));
        }
        return listed(values);
    }

    @Override
    public void send(Player viewer, Object packet) {
        sendPacket(viewer, packet);
    }

    @Override
    public Object displayNames(List<PlayerInfoValue<Component>> entries) {
        return update(
                Action.UPDATE_DISPLAY_NAME,
                entries,
                (fields, value) -> fields.displayName(Components.asVanilla(value)));
    }

    @Override
    public Object listOrders(List<PlayerInfoValue<Integer>> entries) {
        return update(Action.UPDATE_LIST_ORDER, entries, EntryFields::listOrder);
    }

    @Override
    public Object listed(List<PlayerInfoValue<Boolean>> entries) {
        return update(Action.UPDATE_LISTED, entries, EntryFields::listed);
    }

    @Override
    public Object latencies(List<PlayerInfoValue<Integer>> entries) {
        return update(Action.UPDATE_LATENCY, entries, EntryFields::latency);
    }

    @Override
    public Object gameModes(List<PlayerInfoValue<PlayerInfoGameMode>> entries) {
        return update(Action.UPDATE_GAME_MODE, entries, (fields, value) -> fields.gameMode(gameType(value)));
    }

    @Override
    public Object showHat(List<PlayerInfoValue<Boolean>> entries) {
        return update(Action.UPDATE_HAT, entries, EntryFields::showHat);
    }

    @Override
    public Object removeEntries(List<UUID> ids) {
        return new ClientboundPlayerInfoRemovePacket(List.copyOf(Objects.requireNonNull(ids, "ids")));
    }

    @Override
    public void sendPacket(Player viewer, Object packet) {
        sender.send(viewer, packet);
    }

    /** The profile carried by a complete synthetic player-info entry. */
    private static GameProfile profileFor(PlayerInfoEntry entry) {
        TabSkin skin = entry.skin();
        if (skin == null) {
            return GameProfiles.plain(entry.id(), entry.profileName());
        }
        return GameProfiles.withTextures(entry.id(), entry.profileName(), skin.textureValue(), skin.signature());
    }

    /** Build a multi-entry update packet without leaking the NMS entry type through the public port. */
    private static ClientboundPlayerInfoUpdatePacket packet(EnumSet<Action> actions, List<Entry> entries) {
        return new ClientboundPlayerInfoUpdatePacket(actions, entries);
    }

    private static <T> ClientboundPlayerInfoUpdatePacket update(
            Action action, List<PlayerInfoValue<T>> entries, BiConsumer<EntryFields, T> mutator) {
        List<PlayerInfoValue<T>> values = List.copyOf(Objects.requireNonNull(entries, "entries"));
        List<Entry> built = new ArrayList<>(values.size());
        for (PlayerInfoValue<T> value : values) {
            EntryFields fields = new EntryFields(value.id());
            mutator.accept(fields, value.value());
            built.add(fields.build());
        }
        return packet(EnumSet.of(action), built);
    }

    private static GameType gameType(PlayerInfoGameMode gameMode) {
        return switch (Objects.requireNonNull(gameMode, "gameMode")) {
            case SURVIVAL -> GameType.SURVIVAL;
            case CREATIVE -> GameType.CREATIVE;
            case ADVENTURE -> GameType.ADVENTURE;
            case SPECTATOR -> GameType.SPECTATOR;
        };
    }

    /**
     * A tiny mutable holder for homogeneous update packets. Unread components retain vanilla defaults; the
     * packet's action set makes the client consume only the field the caller changed.
     */
    private static final class EntryFields {
        private final UUID id;
        private boolean listed = true;
        private int latency;
        private GameType gameMode = GameType.DEFAULT_MODE;
        private net.minecraft.network.chat.@Nullable Component displayName;
        private boolean showHat = true;
        private int listOrder;

        private EntryFields(UUID id) {
            this.id = id;
        }

        private EntryFields displayName(net.minecraft.network.chat.Component value) {
            this.displayName = value;
            return this;
        }

        private EntryFields listed(boolean value) {
            this.listed = value;
            return this;
        }

        private EntryFields latency(int value) {
            this.latency = value;
            return this;
        }

        private EntryFields gameMode(GameType value) {
            this.gameMode = value;
            return this;
        }

        private EntryFields showHat(boolean value) {
            this.showHat = value;
            return this;
        }

        private EntryFields listOrder(int value) {
            this.listOrder = value;
            return this;
        }

        private Entry build() {
            return new Entry(id, null, listed, latency, gameMode, displayName, showHat, listOrder, null);
        }
    }
}
