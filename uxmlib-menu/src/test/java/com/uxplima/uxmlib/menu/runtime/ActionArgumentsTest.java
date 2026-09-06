package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The action side of placeholder expansion. Rendered text already gets this through the item renderer; these are the
 * same two rules applied to what an action ref is dispatched with, so a menu opened with {@code amount=5} whose confirm
 * button runs {@code give-money:%argument_amount%} actually gives five.
 */
class ActionArgumentsTest {

    @Test
    void aMenuOpenedWithNoArgumentsGetsBackTheVeryMapItGave() {
        Map<String, String> args = Map.of("amount", "%argument_amount%");
        assertThat(ActionArguments.resolve(args, Map.of()))
                .as("the common case is a menu opened without a command, and it should allocate nothing")
                .isSameAs(args);
    }

    @Test
    void anArgumentTokenBecomesTheValueTheMenuWasOpenedWith() {
        assertThat(ActionArguments.resolve(Map.of("amount", "%argument_amount%"), Map.of("amount", "5")))
                .containsExactly(Map.entry("amount", "5"));
    }

    @Test
    void anArgumentNameTheOpenDidNotCarryYieldsEmptyAsTheRendererDoes() {
        assertThat(ActionArguments.resolve(Map.of("to", "%argument_target%"), Map.of("amount", "5")))
                .containsExactly(Map.entry("to", ""));
    }

    @Test
    void aTokenThatIsNotAnArgumentIsLeftVerbatimForTheRegistryToAnswer() {
        assertThat(ActionArguments.resolve(Map.of("who", "%player_name%"), Map.of("amount", "5")))
                .as("the downstream registry, PlaceholderAPI and MiniMessage handling must be unchanged")
                .containsExactly(Map.entry("who", "%player_name%"));
    }

    @Test
    void oneValueMayCarryAnArgumentTokenBesideAForeignOneAndPlainText() {
        assertThat(ActionArguments.resolve(
                        Map.of("line", "give %argument_amount% to %player_name% now"), Map.of("amount", "5")))
                .containsExactly(Map.entry("line", "give 5 to %player_name% now"));
    }

    @Test
    void aValueCarryingADollarSignIsInsertedLiterallyRatherThanReadAsAGroupReference() {
        assertThat(ActionArguments.resolve(Map.of("msg", "%argument_text%"), Map.of("text", "cost is $1 or \\2")))
                .containsExactly(Map.entry("msg", "cost is $1 or \\2"));
    }

    @Test
    void onlyValuesAreExpandedAndTheKeysAreLeftAlone() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("%argument_amount%", "%argument_amount%");
        assertThat(ActionArguments.resolve(args, Map.of("amount", "5")))
                .containsExactly(Map.entry("%argument_amount%", "5"));
    }

    @Test
    void aMenuWithNoLocalPlaceholdersGetsBackTheVeryMapItGave() {
        Map<String, String> args = Map.of("a", "%mine%");
        assertThat(ActionArguments.resolveLocals(args, Map.of())).isSameAs(args);
    }

    @Test
    void aLocalTokenBecomesItsValueAndAnUndefinedOneStaysVerbatim() {
        assertThat(ActionArguments.resolveLocals(
                        Map.of("line", "%drag_material% x%drag_amount% for %papi_balance%"),
                        Map.of("drag_material", "STONE", "drag_amount", "3")))
                .as("an undefined local is left for the registry rather than blanked, unlike an unknown argument")
                .containsExactly(Map.entry("line", "STONE x3 for %papi_balance%"));
    }

    @Test
    void aLocalNamedLikeAnArgumentTokenIsNotTreatedSpeciallyByTheLocalPass() {
        assertThat(ActionArguments.resolveLocals(
                        Map.of("a", "%argument_amount%"), Map.of("argument_amount", "written locally")))
                .containsExactly(Map.entry("a", "written locally"));
    }
}
