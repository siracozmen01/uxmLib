package com.uxplima.uxmlib.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class CmdTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void buildsALiteralNodeWithTheGivenName() {
        LiteralCommandNode<CommandSourceStack> node =
                Cmd.literal("greet").executes(ctx -> Cmd.OK).build();

        assertThat(node.getLiteral()).isEqualTo("greet");
    }

    @Test
    void buildsAnArgumentChildUnderALiteral() {
        LiteralCommandNode<CommandSourceStack> node = Cmd.literal("give")
                .then(Cmd.argument("amount", IntegerArgumentType.integer(1, 64)).executes(ctx -> Cmd.OK))
                .build();

        assertThat(node.getChildren()).hasSize(1);
        assertThat(node.getChildren().iterator().next().getName()).isEqualTo("amount");
    }

    @Test
    void okIsBrigadierSingleSuccess() {
        assertThat(Cmd.OK).isEqualTo(com.mojang.brigadier.Command.SINGLE_SUCCESS);
    }

    @Test
    void rejectsNullNames() {
        assertThatThrownBy(() -> Cmd.literal(null)).isInstanceOf(NullPointerException.class);
    }
}
