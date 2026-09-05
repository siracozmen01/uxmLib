package com.uxplima.uxmlib.packet.scoreboard.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.pipeline.PacketSender;
import com.uxplima.uxmlib.packet.Bundles;
import com.uxplima.uxmlib.packet.Components;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardDisplaySlot;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardNumberFormat;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardObjective;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardPackets;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardRenderType;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardScore;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;

/** Modern Mojang-mapped packet implementation; no Bukkit scoreboard is allocated or installed on the viewer. */
public final class NmsScoreboardPackets implements ScoreboardPackets {

    private final PacketSender sender;

    public NmsScoreboardPackets(PacketSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    @Override
    public Object createObjective(ScoreboardObjective objective) {
        return new ClientboundSetObjectivePacket(
                objective(Objects.requireNonNull(objective, "objective")), ClientboundSetObjectivePacket.METHOD_ADD);
    }

    @Override
    public Object updateObjective(ScoreboardObjective objective) {
        return new ClientboundSetObjectivePacket(
                objective(Objects.requireNonNull(objective, "objective")), ClientboundSetObjectivePacket.METHOD_CHANGE);
    }

    @Override
    public Object removeObjective(String objectiveName) {
        return new ClientboundSetObjectivePacket(
                namedObjective(objectiveName), ClientboundSetObjectivePacket.METHOD_REMOVE);
    }

    @Override
    public Object displayObjective(ScoreboardDisplaySlot slot, String objectiveName) {
        return new ClientboundSetDisplayObjectivePacket(slot(slot), namedObjective(objectiveName));
    }

    @Override
    public Object clearDisplay(ScoreboardDisplaySlot slot) {
        return new ClientboundSetDisplayObjectivePacket(slot(slot), null);
    }

    @Override
    public Object setScore(ScoreboardScore score) {
        Objects.requireNonNull(score, "score");
        return new ClientboundSetScorePacket(
                score.holder(),
                score.objectiveName(),
                score.score(),
                Optional.of(Components.asVanilla(score.displayName())),
                Optional.ofNullable(numberFormat(score.numberFormat())));
    }

    @Override
    public Object removeScore(String objectiveName, String holder) {
        return new ClientboundResetScorePacket(
                requireIdentifier(holder, "score holder"), requireIdentifier(objectiveName, "objective name"));
    }

    @Override
    public void sendPacket(Player viewer, Object packet) {
        sender.send(Objects.requireNonNull(viewer, "viewer"), Objects.requireNonNull(packet, "packet"));
    }

    @Override
    public void sendPackets(Player viewer, List<Object> packets) {
        List<Object> copy = List.copyOf(Objects.requireNonNull(packets, "packets"));
        if (!copy.isEmpty()) {
            sender.send(
                    Objects.requireNonNull(viewer, "viewer"), copy.size() == 1 ? copy.getFirst() : Bundles.of(copy));
        }
    }

    private static Objective objective(ScoreboardObjective objective) {
        return new Objective(
                new Scoreboard(),
                objective.name(),
                ObjectiveCriteria.DUMMY,
                Components.asVanilla(objective.displayName()),
                renderType(objective.renderType()),
                false,
                numberFormat(objective.numberFormat()));
    }

    private static Objective namedObjective(String name) {
        return new Objective(
                new Scoreboard(),
                requireIdentifier(name, "objective name"),
                ObjectiveCriteria.DUMMY,
                net.minecraft.network.chat.Component.empty(),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                null);
    }

    private static DisplaySlot slot(ScoreboardDisplaySlot slot) {
        return switch (Objects.requireNonNull(slot, "slot")) {
            case PLAYER_LIST -> DisplaySlot.LIST;
            case SIDEBAR -> DisplaySlot.SIDEBAR;
            case BELOW_NAME -> DisplaySlot.BELOW_NAME;
        };
    }

    private static ObjectiveCriteria.RenderType renderType(ScoreboardRenderType type) {
        return switch (type) {
            case INTEGER -> ObjectiveCriteria.RenderType.INTEGER;
            case HEARTS -> ObjectiveCriteria.RenderType.HEARTS;
        };
    }

    private static @Nullable NumberFormat numberFormat(ScoreboardNumberFormat format) {
        return switch (Objects.requireNonNull(format, "format")) {
            case ScoreboardNumberFormat.Default ignored -> null;
            case ScoreboardNumberFormat.Blank ignored -> BlankFormat.INSTANCE;
            case ScoreboardNumberFormat.Fixed fixed -> new FixedFormat(Components.asVanilla(fixed.value()));
        };
    }

    private static String requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
