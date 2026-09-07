package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that {@code commands.conf} is read and obeyed.
 *
 * <p>The guard matters more than it looks: a configuration file that nothing reads still looks correct in a
 * review, and an operator only finds out that the rename did nothing after they have restarted a live
 * server. This test fails the moment the wiring is lost.
 */
final class ConfiguredCommandsTest {

    @Test
    @DisplayName("a renamed command takes the name and the aliases from the file")
    void renameIsRead(@TempDir Path folder) throws IOException {
        Path file = write(folder, """
                commands {
                  example {
                    name    = "parla"
                    aliases = ["renk"]
                    enabled = true
                  }
                }
                """);

        ConfiguredCommands.Entry entry = ConfiguredCommands.load(file).entryOf("example", "example");

        assertThat(entry.name()).isEqualTo("parla");
        assertThat(entry.aliases()).containsExactly("renk");
        assertThat(entry.enabled()).isTrue();
    }

    @Test
    @DisplayName("a key the file does not mention keeps the fallback")
    void missingKeyFallsBack(@TempDir Path folder) throws IOException {
        Path file = write(folder, "commands { }\n");

        ConfiguredCommands.Entry entry = ConfiguredCommands.load(file).entryOf("example", "example");

        assertThat(entry.name()).isEqualTo("example");
        assertThat(entry.aliases()).isEmpty();
        assertThat(entry.enabled()).isTrue();
    }

    @Test
    @DisplayName("a file that is not there leaves every command as the handler declares it")
    void aMissingFileIsNotAnError(@TempDir Path folder) {
        ConfiguredCommands.Entry entry =
                ConfiguredCommands.load(folder.resolve("commands.conf")).entryOf("example", "example");

        assertThat(entry.name()).isEqualTo("example");
        assertThat(entry.enabled()).isTrue();
    }

    @Test
    @DisplayName("a command can be turned off entirely")
    void disabledCommandIsReported(@TempDir Path folder) throws IOException {
        Path file = write(folder, "commands { example { enabled = false } }\n");

        assertThat(ConfiguredCommands.load(file).isEnabled("example", "example"))
                .isFalse();
    }

    @Test
    @DisplayName("the replacer rewrites @FromConfig into the @Command the DSL registers")
    void theReplacerCarriesTheFileIntoTheAnnotation(@TempDir Path folder) throws IOException {
        Path file = write(folder, """
                commands {
                  example {
                    name    = "parla"
                    aliases = ["renk", "isik"]
                  }
                }
                """);

        List<java.lang.annotation.Annotation> replacements = ConfiguredCommands.load(file)
                .replacer()
                .replace(fromConfig("example", "example", "Shine"), Placeholder.class);
        Command command = (Command) replacements.get(0);

        assertThat(command.name()).isEqualTo("parla");
        assertThat(command.aliases()).containsExactly("renk", "isik");
        assertThat(command.description()).isEqualTo("Shine");
    }

    @FromConfig(value = "example", fallbackName = "example", description = "An example")
    static class ExampleCommand {

        @FromConfig("sell")
        void sell(Sender sender) {}

        @FromConfig("buy")
        void buy(Sender sender) {}

        @Subcommand("")
        void front(Sender sender) {}
    }

    @Test
    @DisplayName("a renamed subcommand takes the word and the aliases of the file")
    void abranchIsNamedByTheFile(@TempDir Path folder) throws IOException {
        Path file = write(folder, """
                commands {
                  example {
                    subcommands {
                      sell { name = "sat", aliases = ["satis"] }
                    }
                  }
                }
                """);

        LiteralCommandNode<CommandSourceStack> node = build(file);

        assertThat(node.getChild("sat")).isNotNull();
        assertThat(node.getChild("satis")).isNotNull();
        assertThat(node.getChild("sell")).isNull();
    }

    @Test
    @DisplayName("a subcommand the file does not name keeps the word the author wrote")
    void abranchTheFileSkipsKeepsItsFallback(@TempDir Path folder) throws IOException {
        Path file = write(folder, "commands { example { subcommands { sell { name = \"sat\" } } } }\n");

        LiteralCommandNode<CommandSourceStack> node = build(file);

        assertThat(node.getChild("buy")).isNotNull();
    }

    @Test
    @DisplayName("a subcommand can be turned off, and then it is not in the tree")
    void abranchCanBeTurnedOff(@TempDir Path folder) throws IOException {
        Path file = write(folder, "commands { example { subcommands { buy { enabled = false } } } }\n");

        LiteralCommandNode<CommandSourceStack> node = build(file);

        assertThat(node.getChild("buy")).isNull();
        assertThat(node.getChild("sell")).isNotNull();
    }

    @Test
    @DisplayName("a name the author leaves out is the key itself")
    void anemptyFallbackIsTheKey(@TempDir Path folder) {
        Path file = folder.resolve("commands.conf");

        LiteralCommandNode<CommandSourceStack> node = build(file);

        assertThat(node.getChild("sell")).isNotNull();
        assertThat(node.getChild("buy")).isNotNull();
    }

    @Test
    @DisplayName("a subcommand the file switches off on one line is reported off")
    void aplainSwitchIsRead(@TempDir Path folder) throws IOException {
        Path file = write(folder, """
                commands {
                  example {
                    subcommands {
                      buy  = false
                      sell = true
                    }
                  }
                }
                """);

        ConfiguredCommands commands = ConfiguredCommands.load(file);

        assertThat(commands.isBranchEnabled("example", "buy")).isFalse();
        assertThat(commands.isBranchEnabled("example", "sell")).isTrue();
    }

    @Test
    @DisplayName("a subcommand switched off on one line stays in the command tree")
    void aplainSwitchLeavesTheBranchInTheTree(@TempDir Path folder) throws IOException {
        // The whole point of the one-line form: the branch is reached and answers, rather than the server
        // saying the word is unknown. One instance does both jobs here, which is how a plugin wires it: the
        // replacer reads the branch's name and enabled flag at registration and the handler asks this same
        // object afterwards. Two instances would hide a reader that damages the tree it reads from.
        Path file = write(folder, "commands { example { subcommands { buy = false } } }\n");
        ConfiguredCommands commands = ConfiguredCommands.load(file);
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(FromConfig.class, commands.replacer());

        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new ExampleCommand(), resolvers);

        assertThat(node.getChild("buy")).isNotNull();
        assertThat(commands.isBranchEnabled("example", "buy")).isFalse();
    }

    @Test
    @DisplayName("a subcommand the file does not mention is on")
    void anunmentionedBranchIsOn(@TempDir Path folder) throws IOException {
        Path file = write(folder, "commands { example { subcommands { buy = false } } }\n");

        assertThat(ConfiguredCommands.load(file).isBranchEnabled("example", "sell"))
                .isTrue();
    }

    @Test
    @DisplayName("a missing file leaves every subcommand on")
    void amissingFileLeavesEveryBranchOn(@TempDir Path folder) {
        ConfiguredCommands commands = ConfiguredCommands.load(folder.resolve("absent.conf"));

        assertThat(commands.isBranchEnabled("example", "buy")).isTrue();
    }

    @Test
    @DisplayName("the block form reports off too, because a branch nobody can reach is off")
    void ablockFormBranchReportsOff(@TempDir Path folder) throws IOException {
        Path file = write(folder, "commands { example { subcommands { buy { enabled = false } } } }\n");

        ConfiguredCommands commands = ConfiguredCommands.load(file);

        assertThat(commands.isBranchEnabled("example", "buy")).isFalse();
        assertThat(build(file).getChild("buy")).isNull();
    }

    @Test
    @DisplayName("a branch renamed in the block form is still on")
    void arenamedBlockFormBranchIsOn(@TempDir Path folder) throws IOException {
        Path file = write(folder, "commands { example { subcommands { buy { name = \"al\" } } } }\n");

        assertThat(ConfiguredCommands.load(file).isBranchEnabled("example", "buy"))
                .isTrue();
        assertThat(build(file).getChild("al")).isNotNull();
    }

    @Test
    @DisplayName("the block of one command names no branch of another")
    void abranchKeySitsUnderItsOwnCommand() {
        assertThat(ConfiguredCommands.branchKey("example", "sell")).isEqualTo("example.subcommands.sell");
    }

    /** The node the DSL builds for {@link ExampleCommand} with the file behind it. */
    private static LiteralCommandNode<CommandSourceStack> build(Path file) {
        ParamResolvers resolvers = ParamResolvers.withDefaults()
                .replacer(FromConfig.class, ConfiguredCommands.load(file).replacer());
        return AnnotatedCommands.buildNode(new ExampleCommand(), resolvers);
    }

    /** A class the replacer is asked about; the replacer reads only the annotation, never the element. */
    @SuppressWarnings("unused")
    private static final class Placeholder {}

    /** The annotation as the DSL would read it off a class, built through the library's own proxy. */
    private static FromConfig fromConfig(String value, String fallbackName, String description) {
        return Replacements.of(
                FromConfig.class, Map.of("value", value, "fallbackName", fallbackName, "description", description));
    }

    private static Path write(Path folder, String contents) throws IOException {
        Path file = folder.resolve("commands.conf");
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }
}
