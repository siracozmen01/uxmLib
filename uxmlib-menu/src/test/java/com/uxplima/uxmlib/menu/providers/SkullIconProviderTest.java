package com.uxplima.uxmlib.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The one provider an operator reaches for by hand more than any other, because a head is how a menu shows a person.
 * It claims three prefixes and nothing else, and every way of getting a head wrong has to end in the material
 * fallback rather than in a blank head: a head with no skin looks like a bug in the server, not like a typo in a
 * file, so it sends the operator looking in the wrong place.
 */
class SkullIconProviderTest {

    private final SkullIconProvider provider = new SkullIconProvider();

    private PlayerMock viewer;

    private MenuContext ctx;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        ctx = MenuContext.of(viewer, null, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Optional<ItemStack> icon(String spec) {
        return provider.icon(spec, ctx);
    }

    private static SkullMeta metaOf(ItemStack stack) {
        return (SkullMeta) stack.getItemMeta();
    }

    // -- what it claims -------------------------------------------------------------------------------------

    @Test
    void allThreePrefixesProduceAPlayerHead() {
        for (String spec : new String[] {"skull:Notch", "head:Notch", "basehead:dGV4dHVyZQ=="}) {
            assertThat(icon(spec)).as(spec).isPresent();
            assertThat(icon(spec).orElseThrow().getType()).as(spec).isEqualTo(Material.PLAYER_HEAD);
        }
    }

    /** An operator writes these by hand, so the prefix ignores case and the spaces around the whole spec. */
    @Test
    void thePrefixIgnoresCaseAndSurroundingSpace() {
        assertThat(icon("SKULL:Notch")).isPresent();
        assertThat(icon("  Head:Notch  ")).isPresent();
        assertThat(icon("BaseHead:dGV4dHVyZQ==")).isPresent();
    }

    /** A bare material name is the renderer's fallback, and a prefix nobody here owns belongs to another provider. */
    @Test
    void aSpecWithoutOneOfTheThreePrefixesIsLeftAlone() {
        assertThat(icon("PLAYER_HEAD")).isEmpty();
        assertThat(icon("itemsadder:ruby")).isEmpty();
        assertThat(icon("")).isEmpty();
        assertThat(icon("   ")).isEmpty();
    }

    // -- the viewer's own head ------------------------------------------------------------------------------

    /**
     * {@code self} is the viewer, taken by uuid rather than by name. A name would send the head through a blocking
     * account lookup on the thread the menu is drawn on, which is the one thing this provider promises not to do.
     *
     * <p>The two are told apart by what the head carries: a head owned by a name keeps that name, and this one does
     * not, because the word never reached the account lookup at all.
     */
    @Test
    void selfIsTheViewerAndNotAnAccountNamedSelf() {
        ItemStack own = icon("skull:self").orElseThrow();
        ItemStack named = icon("skull:selfish").orElseThrow();

        assertThat(metaOf(own).getOwningPlayer()).isNotNull();
        assertThat(metaOf(own).getOwningPlayer().getName()).isNotEqualTo("self");
        assertThat(metaOf(named).getOwningPlayer().getName())
                .as("a name that merely starts with the word is still a name")
                .isEqualTo("selfish");
    }

    /** The word is read without regard to case or the spaces around it, under either of the two prefixes. */
    @Test
    void selfIsReadWithoutRegardToCaseOrSurroundingSpace() {
        for (String spelling : new String[] {"SELF", "Self", "  self  "}) {
            for (String prefix : new String[] {"skull:", "head:"}) {
                ItemStack head = icon(prefix + spelling).orElseThrow();
                assertThat(metaOf(head).getOwningPlayer()).as(prefix + spelling).isNotNull();
                assertThat(metaOf(head).getOwningPlayer().getName())
                        .as(prefix + spelling)
                        .isNotEqualTo(spelling.trim());
            }
        }
    }

    // -- a texture is not a name ----------------------------------------------------------------------------

    /**
     * {@code basehead:} is a texture payload and never a player. The two are told apart because a texture head
     * carries a profile with no account behind it, while a named head carries an owner.
     */
    @Test
    void aBaseheadIsATextureAndNotAPlayerName() {
        ItemStack texture = icon("basehead:dGV4dHVyZQ==").orElseThrow();
        ItemStack named = icon("head:Notch").orElseThrow();

        assertThat(metaOf(texture).getOwningPlayer()).isNull();
        assertThat(metaOf(named).getOwningPlayer()).isNotNull();
    }

    // -- everything that falls through ----------------------------------------------------------------------

    /**
     * A prefix with nothing after it is a typo, and the menu shows the material fallback. A head built from
     * nothing would be a blank head, which looks like a broken server rather than a broken file.
     */
    @Test
    void aPrefixWithNothingAfterItFallsThroughRatherThanBuildingABlankHead() {
        assertThat(icon("skull:")).isEmpty();
        assertThat(icon("head:")).isEmpty();
        assertThat(icon("basehead:")).isEmpty();
        assertThat(icon("skull:    ")).isEmpty();
        assertThat(icon("basehead:    ")).isEmpty();
    }

    /** The value keeps its own spaces trimmed off, so a config written with a space still finds the account. */
    @Test
    void theValueIsTrimmedBeforeItIsRead() {
        ItemStack spaced = icon("skull:   Notch   ").orElseThrow();

        assertThat(metaOf(spaced).getOwningPlayer()).isNotNull();
        assertThat(metaOf(spaced).getOwningPlayer().getName()).isEqualTo("Notch");
    }
}
