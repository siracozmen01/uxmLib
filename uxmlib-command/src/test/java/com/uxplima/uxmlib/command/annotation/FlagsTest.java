package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Flag;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.command.annotation.annotations.Switch;
import org.junit.jupiter.api.Test;

/**
 * Covers {@code @Flag}/{@code @Switch}: the model records flags, the renderer ends a flagged branch with one
 * greedy {@code flags} node while keeping positional args intact, {@link FlagArgumentType} extracts value
 * flags and presence switches (long form, shorthand, inline {@code =}, any order), and the order rules
 * (flags last, switches boolean) are enforced at registration.
 */
class FlagsTest {

    @Command(name = "give")
    static class GiveCommand {
        @Subcommand("item")
        void item(
                Sender sender,
                @Arg("material") String material,
                @Flag(value = "count", shorthand = 'c') int count,
                @Switch(value = "silent", shorthand = 's') boolean silent) {}
    }

    @Command(name = "badorder")
    static class FlagBeforeArg {
        @Subcommand("go")
        void go(Sender sender, @Switch("force") boolean force, @Arg("name") String name) {}
    }

    @Command(name = "badswitch")
    static class NonBooleanSwitch {
        @Subcommand("go")
        void go(Sender sender, @Switch("level") int level) {}
    }

    @Command(name = "badgreedyflag")
    static class GreedyWithFlag {
        @Subcommand("say")
        void say(Sender sender, @Arg(value = "msg", greedy = true) String msg, @Switch("loud") boolean loud) {}
    }

    @Command(name = "nativeflag")
    static class NativeTypedFlag {
        @Subcommand("tp")
        void tp(Sender sender, @Flag("dest") org.bukkit.World dest) {}
    }

    @Command(name = "nativelist")
    static class NativeElementList {
        @Subcommand("worlds")
        void worlds(Sender sender, @Arg("ws") java.util.List<org.bukkit.World> ws) {}
    }

    @Test
    void modelCarriesFlagsAndPositionalArgsSeparately() {
        CommandModel model = CommandModels.reflect(new GiveCommand(), ParamResolvers.withDefaults());
        BranchModel item = model.branches().get(0);

        assertThat(item.args()).extracting(ArgBinder.ParamArg::name).containsExactly("material");
        assertThat(item.flags()).extracting(FlagModel::name).containsExactly("count", "silent");
        assertThat(item.flags().get(0).isValueFlag()).isTrue();
        assertThat(item.flags().get(1).isValueFlag()).isFalse();
    }

    @Test
    void rendererEndsAFlaggedBranchWithATrailingFlagsNode() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new GiveCommand());
        CommandNode<CommandSourceStack> material =
                java.util.Objects.requireNonNull(node.getChild("item")).getChild("material");
        assertThat(material).isNotNull();
        CommandNode<CommandSourceStack> flags =
                java.util.Objects.requireNonNull(material).getChild("flags");
        assertThat(flags).isInstanceOf(ArgumentCommandNode.class);
        // The positional arg still ends the command (flags are optional), and so does the flags node.
        assertThat(java.util.Objects.requireNonNull(material).getCommand()).isNotNull();
        assertThat(java.util.Objects.requireNonNull(flags).getCommand()).isNotNull();
    }

    @Test
    void parsesValueFlagsAndSwitchesInAnyForm() throws Exception {
        Flags flags = parse(flagType(), "--count 5 -s");
        assertThat(flags.value("count")).isEqualTo("5");
        assertThat(flags.isSet("silent")).isTrue();
    }

    @Test
    void parsesInlineValueAndShorthandValueFlag() throws Exception {
        Flags flags = parse(flagType(), "-c=10");
        assertThat(flags.value("count")).isEqualTo("10");
        assertThat(flags.isSet("silent")).isFalse();
    }

    @Test
    void emptyInputYieldsNoFlags() throws Exception {
        Flags flags = parse(flagType(), "");
        assertThat(flags.value("count")).isNull();
        assertThat(flags.isSet("silent")).isFalse();
    }

    @Test
    void completingALaterFlagReplacesOnlyTheTypedTokenNotThePriorFlags() throws Exception {
        // The greedy flags blob begins right after "give item diamond ".
        String line = "give item diamond --count 5 --si";
        int blobStart = line.indexOf("--count");
        SuggestionsBuilder builder = new SuggestionsBuilder(line, blobStart);

        Suggestions suggestions =
                flagType().listSuggestions(emptyContext(), builder).join();

        Suggestion silent = suggestions.getList().stream()
                .filter(s -> s.getText().equals("--silent"))
                .findFirst()
                .orElseThrow();
        // Applying the accepted suggestion must keep the already-typed "--count 5" intact.
        assertThat(silent.apply(line)).isEqualTo("give item diamond --count 5 --silent");
        assertThat(silent.getRange().getStart()).isEqualTo(line.lastIndexOf("--si"));
    }

    @Test
    void completingAnEmptyTailOffersEveryFlagAfterATrailingSpace() throws Exception {
        String line = "give item diamond --count 5 ";
        int blobStart = line.indexOf("--count");
        SuggestionsBuilder builder = new SuggestionsBuilder(line, blobStart);

        Suggestions suggestions =
                flagType().listSuggestions(emptyContext(), builder).join();

        assertThat(suggestions.getList()).extracting(Suggestion::getText).contains("--count", "--silent");
        // A fresh token at the cursor: the insert range starts at the cursor, leaving "--count 5 " untouched.
        Suggestion any = suggestions.getList().get(0);
        assertThat(any.getRange().getStart()).isEqualTo(line.length());
    }

    @Test
    void rejectsAnUnknownFlag() {
        assertThatThrownBy(() -> parse(flagType(), "--bogus 1")).isInstanceOf(CommandSyntaxException.class);
    }

    @Test
    void rejectsAValueFlagWithNoValue() {
        assertThatThrownBy(() -> parse(flagType(), "--count")).isInstanceOf(CommandSyntaxException.class);
    }

    @Test
    void rejectsAPositionalArgAfterAFlag() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new FlagBeforeArg()))
                .isInstanceOf(CommandParseException.class)
                .hasMessageContaining("positional");
    }

    @Test
    void rejectsANonBooleanSwitch() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new NonBooleanSwitch()))
                .isInstanceOf(CommandParseException.class)
                .hasMessageContaining("boolean");
    }

    @Test
    void rejectsAGreedyArgCombinedWithAFlag() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new GreedyWithFlag()))
                .isInstanceOf(CommandParseException.class)
                .hasMessageContaining("greedy");
    }

    @Test
    void rejectsANativeArgumentTypeAsAFlagValue() {
        // A native type (World/Player/Location/...) cannot be parsed in the throwaway dispatcher a flag value
        // uses, so it must be rejected at registration rather than blow up the first time the command runs.
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new NativeTypedFlag()))
                .isInstanceOf(CommandParseException.class)
                .hasMessageContaining("native");
    }

    @Test
    void rejectsACollectionOfANativeArgumentType() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new NativeElementList()))
                .isInstanceOf(CommandParseException.class)
                .hasMessageContaining("native");
    }

    private static Flags parse(FlagArgumentType type, String input) throws CommandSyntaxException {
        return type.parse(new StringReader(input));
    }

    /** listSuggestions never reads the context, so a bare mock with no stubbing is enough. */
    @SuppressWarnings("unchecked")
    private static CommandContext<CommandSourceStack> emptyContext() {
        return org.mockito.Mockito.mock(CommandContext.class);
    }

    private static FlagArgumentType flagType() throws Exception {
        Method m = GiveCommand.class.getDeclaredMethod("item", Sender.class, String.class, int.class, boolean.class);
        Parameter[] params = m.getParameters();
        ParamResolver<?> intResolver =
                java.util.Objects.requireNonNull(ParamResolvers.withDefaults().resolverFor(int.class));
        FlagModel count = FlagModel.valueFlag("count", 'c', intResolver, params[2]);
        FlagModel silent = FlagModel.switchFlag("silent", 's', params[3]);
        return new FlagArgumentType(List.of(count, silent));
    }
}
