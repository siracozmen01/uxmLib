package com.uxplima.uxmlib.hud.nametag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import net.kyori.adventure.text.format.NamedTextColor;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * One registry for one server, whichever plugin loaded first.
 *
 * <p>Two plugins in one JVM cannot be given two class loaders here, so what is proved is the seam that would
 * have to cross one: the second plugin never builds a registry of its own, its calls travel as an
 * {@code Object[]} of types nobody shades, and both plugins' parts end up on one name. A relocated copy of
 * this library differs from this test only in the class loader the same protocol runs over.
 */
class SharedNametagsTest {

    private ServerMock server;
    private Plugin glow;
    private Plugin tags;
    private FakeNametagSink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        glow = MockBukkit.createMockPlugin("uxmGlow");
        tags = MockBukkit.createMockPlugin("uxmTags");
        sink = new FakeNametagSink();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the plugin that claims first owns the one registry")
    void theFirstClaimOwnsIt() {
        claim(glow, sink);
        Nametags second = claim(tags, new FakeNametagSink());

        PlayerMock player = server.addPlayer("Amy");
        second.contribute(player, NametagContribution.prefix("uxmTags", Text.mini("<gold>[VIP]")));

        assertThat(SharedNametags.ownerName()).contains("uxmGlow");
        // The second plugin's own sink was never built, so nothing of its own reached a scoreboard.
        assertThat(sink.shown(player.getUniqueId())).isNotNull();
    }

    @Test
    @DisplayName("a plugin that is not the owner never builds a registry of its own")
    void aSecondPluginBuildsNothing() {
        claim(glow, sink);
        boolean[] built = {false};
        Nametags second = SharedNametags.claim(tags, () -> {
            built[0] = true;
            return new NametagRegistry(new FakeNametagSink(), Logger.getLogger("second"));
        });

        second.contribute(server.addPlayer("Amy"), NametagContribution.prefix("uxmTags", Text.mini("[VIP]")));

        assertThat(built[0]).isFalse();
    }

    @Test
    @DisplayName("two plugins across the seam compose into one name instead of overwriting each other")
    void twoPluginsComposeIntoOneName() {
        Nametags first = claim(glow, sink);
        Nametags second = claim(tags, new FakeNametagSink());
        PlayerMock player = server.addPlayer("Amy");

        second.contribute(player, NametagContribution.prefix("uxmTags", 50, Text.mini("<gold>[VIP]")));
        first.contribute(player, NametagContribution.color("uxmGlow", 100, NamedTextColor.RED));

        ComposedNametag name = shownFor(sink, player);
        assertThat(Text.plain(name.prefix())).isEqualTo("[VIP]");
        assertThat(name.color()).isEqualTo(NamedTextColor.RED);
        assertThat(name.colorOwner()).isEqualTo("uxmGlow");
    }

    @Test
    @DisplayName("the composed name reads back across the seam, parts and all")
    void theComposedNameReadsBack() {
        Nametags first = claim(glow, sink);
        Nametags second = claim(tags, new FakeNametagSink());
        PlayerMock player = server.addPlayer("Amy");

        first.contribute(player, NametagContribution.color("uxmGlow", 100, NamedTextColor.RED));
        second.contribute(
                player,
                new NametagContribution(
                        "uxmTags", 50, Text.mini("<gold>[VIP]"), Text.mini("<gray>*"), NamedTextColor.BLUE));

        ComposedNametag read = second.composed(player.getUniqueId());
        assertThat(Text.plain(read.prefix())).isEqualTo("[VIP]");
        assertThat(Text.plain(read.suffix())).isEqualTo("*");
        assertThat(read.color()).isEqualTo(NamedTextColor.BLUE);
        assertThat(read.colorOwner()).isEqualTo("uxmTags");
        assertThat(read.colorSources()).containsExactly("uxmGlow", "uxmTags");
    }

    @Test
    @DisplayName("a withdrawal across the seam takes back only that plugin's part")
    void aWithdrawalTakesBackOnlyItsOwnPart() {
        Nametags first = claim(glow, sink);
        Nametags second = claim(tags, new FakeNametagSink());
        PlayerMock player = server.addPlayer("Amy");
        first.contribute(player, NametagContribution.prefix("uxmGlow", 100, Text.mini("<red>[A]")));
        second.contribute(player, NametagContribution.prefix("uxmTags", 200, Text.mini("<gold>[B]")));

        second.withdraw("uxmTags");

        assertThat(Text.plain(shownFor(sink, player).prefix())).isEqualTo("[A]");
    }

    @Test
    @DisplayName("closing a registry another plugin owns takes back your parts and leaves theirs")
    void closingSomebodyElsesRegistryOnlyTakesBackYourOwn() {
        Nametags first = claim(glow, sink);
        Nametags second = claim(tags, new FakeNametagSink());
        PlayerMock player = server.addPlayer("Amy");
        first.contribute(player, NametagContribution.prefix("uxmGlow", 100, Text.mini("<red>[A]")));
        second.contribute(player, NametagContribution.prefix("uxmTags", 200, Text.mini("<gold>[B]")));

        second.close();

        assertThat(sink.clearAllCalls()).isZero();
        assertThat(Text.plain(shownFor(sink, player).prefix())).isEqualTo("[A]");
        assertThat(SharedNametags.ownerName()).contains("uxmGlow");
    }

    @Test
    @DisplayName("a quit forgets the player across the seam")
    void aQuitForgetsAcrossTheSeam() {
        Nametags first = claim(glow, sink);
        Nametags second = claim(tags, new FakeNametagSink());
        PlayerMock player = server.addPlayer("Amy");
        first.contribute(player, NametagContribution.prefix("uxmGlow", 100, Text.mini("<red>[A]")));

        second.forget(player);

        assertThat(sink.shown(player.getUniqueId())).isNull();
        assertThat(sink.cleared()).containsExactly(player.getUniqueId());
    }

    @Test
    @DisplayName("the owner going away hands the registry to whoever is still running")
    void theRegistryPassesOnWhenTheOwnerGoes() {
        claim(glow, sink);
        FakeNametagSink theirs = new FakeNametagSink();
        Nametags second = claim(tags, theirs);
        PlayerMock player = server.addPlayer("Amy");

        SharedNametags.release(glow);
        second.contribute(player, NametagContribution.prefix("uxmTags", 200, Text.mini("<gold>[B]")));

        assertThat(SharedNametags.ownerName()).contains("uxmTags");
        assertThat(Text.plain(shownFor(theirs, player).prefix())).isEqualTo("[B]");
    }

    @Test
    @DisplayName("a Function service somebody registered for their own purpose is left alone")
    void anUnmarkedFunctionServiceIsNotARegistry() {
        Function<Object[], Object> theirs = payload -> "not ours";
        register(theirs);

        Nametags ours = claim(glow, sink);
        PlayerMock player = server.addPlayer("Amy");
        ours.contribute(player, NametagContribution.prefix("uxmGlow", 100, Text.mini("<red>[A]")));

        assertThat(SharedNametags.ownerName()).contains("uxmGlow");
        assertThat(sink.shown(player.getUniqueId())).isNotNull();
    }

    @Test
    @DisplayName("the registry is claimed when the plugin loads, not when it first paints a name")
    void ownershipIsSettledAtClaimTime() {
        claim(glow, sink);

        assertThat(SharedNametags.ownerName()).contains("uxmGlow");
    }

    @Test
    @DisplayName("release takes the registry off the server")
    void releaseTakesItOff() {
        claim(glow, sink);

        SharedNametags.release(glow);

        assertThat(SharedNametags.ownerName()).isEmpty();
    }

    private Nametags claim(Plugin plugin, NametagSink target) {
        return SharedNametags.claim(plugin, () -> new NametagRegistry(target, Logger.getLogger(plugin.getName())));
    }

    @SuppressWarnings("unchecked") // Bukkit keys a service by its class literal, which cannot carry arguments.
    private void register(Function<Object[], Object> provider) {
        server.getServicesManager()
                .register(
                        (Class<Function<Object[], Object>>) (Class<?>) Function.class,
                        provider,
                        glow,
                        ServicePriority.Normal);
    }

    /** What the sink is showing for {@code player}; fails the test rather than the compiler when nothing is. */
    private static ComposedNametag shownFor(FakeNametagSink sink, PlayerMock player) {
        ComposedNametag name = sink.shown(player.getUniqueId());
        assertThat(name).as("a name for %s", player.getName()).isNotNull();
        return Objects.requireNonNull(name);
    }
}
