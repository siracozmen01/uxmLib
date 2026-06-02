package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.condition.OperandResolver;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;

/**
 * An {@link ActionList} runs its actions in declaration order against a fake {@link ActionContext}, each
 * action firing with placeholders resolved. The fake audience and command sinks capture what was delivered so
 * the test stays off any real server.
 */
class ActionListTest {

    /** Captures every component the target/broadcast audience receives, by channel. */
    private static final class CapturingAudience implements Audience {
        final List<Component> messages = new ArrayList<>();
        final List<Component> actionBars = new ArrayList<>();

        @Override
        public void sendMessage(Component message) {
            messages.add(message);
        }

        @Override
        public void sendActionBar(Component message) {
            actionBars.add(message);
        }
    }

    /**
     * A resolver that substitutes embedded {@code %token%} placeholders inside the whole template, the way a
     * PlaceholderAPI bridge does for action strings (which are full lines, not single operands).
     */
    private static OperandResolver mapResolver(Map<String, String> values) {
        return (player, template) -> {
            String result = template;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            return result;
        };
    }

    @Test
    void runsEachActionInOrderWithResolvedPlaceholders() {
        CapturingAudience target = new CapturingAudience();
        List<String> consoleCommands = new ArrayList<>();
        OperandResolver resolver = mapResolver(Map.of("%player_name%", "Steve"));

        ActionContext context = ActionContext.builder(resolver)
                .target(target)
                .consoleSink(consoleCommands::add)
                .build();

        ActionList list = ActionList.parse(
                List.of("[message] <green>Hello, %player_name%!", "[console] give %player_name% diamond"));
        list.run(context);

        assertThat(target.messages).hasSize(1);
        assertThat(Text.plain(target.messages.get(0))).isEqualTo("Hello, Steve!");
        assertThat(consoleCommands).containsExactly("give Steve diamond");
    }

    @Test
    void broadcastReachesTheBroadcastAudienceNotTheTarget() {
        CapturingAudience target = new CapturingAudience();
        CapturingAudience everyone = new CapturingAudience();
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .target(target)
                .broadcast(everyone)
                .build();

        ActionList.parse(List.of("[broadcast] <red>server restarting")).run(context);

        assertThat(everyone.messages).hasSize(1);
        assertThat(target.messages).isEmpty();
    }

    @Test
    void actionBarRoutesToTheActionBarChannel() {
        CapturingAudience target = new CapturingAudience();
        ActionContext context =
                ActionContext.builder(OperandResolver.identity()).target(target).build();

        ActionList.parse(List.of("[actionbar] <yellow>combat!")).run(context);

        assertThat(target.actionBars).hasSize(1);
        assertThat(Text.plain(target.actionBars.get(0))).isEqualTo("combat!");
        assertThat(target.messages).isEmpty();
    }

    @Test
    void playerCommandStripsALeadingSlashBeforeDispatch() {
        List<String> playerCommands = new ArrayList<>();
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .playerSink(playerCommands::add)
                .build();

        ActionList.parse(List.of("[player] /spawn")).run(context);

        assertThat(playerCommands).containsExactly("spawn");
    }

    @Test
    void hasAsyncActionsReflectsTheMix() {
        assertThat(ActionList.parse(List.of("[message] hi")).hasAsyncActions()).isTrue();
        assertThat(ActionList.parse(List.of("[console] say hi")).hasAsyncActions())
                .isFalse();
        assertThat(ActionList.parse(List.of("[console] say hi", "[message] hi")).hasAsyncActions())
                .isTrue();
    }

    @Test
    void syncAndAsyncPartitionKeepsEachActionOnItsOwnLane() {
        // A mixed list must NOT be routed wholesale onto an async lane: [console]/[close] are sync-only and
        // would be illegal off the main thread. The partition lets a scheduler-aware driver run each subset on
        // the right lane while preserving per-subset declaration order.
        ActionList mixed = ActionList.parse(List.of("[message] a", "[console] say b", "[close]", "[actionbar] c"));

        assertThat(mixed.asyncActions()).hasSize(2);
        assertThat(mixed.asyncActions()).allMatch(Action::async);
        assertThat(mixed.syncActions()).hasSize(2);
        assertThat(mixed.syncActions()).noneMatch(Action::async);
    }

    @Test
    void runHonoursEachActionsOwnLaneOnTheCallingThread() {
        // run() executes every action regardless of flag (the pure path); the flag is a routing hint, not a
        // promise that the whole list may run async. Mixed sync+async actions all still fire.
        CapturingAudience target = new CapturingAudience();
        List<String> consoleCommands = new ArrayList<>();
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .target(target)
                .consoleSink(consoleCommands::add)
                .build();

        ActionList.parse(List.of("[message] hi", "[console] say hi")).run(context);

        assertThat(target.messages).hasSize(1);
        assertThat(consoleCommands).containsExactly("say hi");
    }

    @Test
    void emptyListRunsWithoutEffect() {
        CapturingAudience target = new CapturingAudience();
        ActionContext context =
                ActionContext.builder(OperandResolver.identity()).target(target).build();
        ActionList.of(List.of()).run(context);
        assertThat(target.messages).isEmpty();
    }

    @Test
    void aMalformedSoundKeyIsSkippedWithoutThrowingOrAbortingLaterActions() {
        // The key resolves to an uppercase/garbage value Key.key would reject; the action must fail soft so the
        // following [message] still fires (the Action no-throw contract ActionList.run relies on).
        CapturingAudience target = new CapturingAudience();
        OperandResolver resolver = mapResolver(Map.of("%sound%", "NOT A VALID KEY"));
        ActionContext context = ActionContext.builder(resolver).target(target).build();

        ActionList.parse(List.of("[sound] %sound%", "[message] still here")).run(context);

        assertThat(target.messages).hasSize(1);
        assertThat(Text.plain(target.messages.get(0))).isEqualTo("still here");
    }
}
