package com.uxplima.uxmlib.packet.npc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

/**
 * The {@link NpcPackets} port contract, exercised against a recording fake. The real packet construction is
 * compile-gated against the dev bundle and covered by the plugin smoke; this proves the port itself is
 * NMS-free, that ids are allocated monotonically, that the spawn reuses the player-info profile id (the link
 * the client needs to attach the skin), and that {@code bundle}/{@code send} behave as documented.
 */
class NpcPacketsContractTest {

    /**
     * The pose names {@code net.minecraft.world.entity.Pose} declares: the by-name target for {@link NpcPose}.
     * The list is fixed by the protocol, so pinning it here is stable; it proves every {@code NpcPose} server name
     * has a real {@code Pose} counterpart so the NMS {@code Pose.valueOf} never throws at render time.
     */
    private static final java.util.Set<String> SERVER_POSE_NAMES = java.util.Set.of(
            "STANDING",
            "FALL_FLYING",
            "SLEEPING",
            "SWIMMING",
            "SPIN_ATTACK",
            "CROUCHING",
            "LONG_JUMPING",
            "DYING",
            "CROAKING",
            "USING_TONGUE",
            "SITTING",
            "ROARING",
            "SNIFFING",
            "EMERGING",
            "DIGGING",
            "SLIDING",
            "SHOOTING",
            "INHALING");

    /** The 16 colour names {@code net.minecraft.ChatFormatting} declares: the by-name target for {@link NamedColor}. */
    private static final java.util.Set<String> CHAT_FORMATTING_COLORS = java.util.Set.of(
            "BLACK",
            "DARK_BLUE",
            "DARK_GREEN",
            "DARK_AQUA",
            "DARK_RED",
            "DARK_PURPLE",
            "GOLD",
            "GRAY",
            "DARK_GRAY",
            "BLUE",
            "GREEN",
            "AQUA",
            "RED",
            "LIGHT_PURPLE",
            "YELLOW",
            "WHITE");

    @BeforeEach
    void setUp() {
        // A mocked server is needed only to mint a real ItemStack for the equipment contract; the rest is pure.
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void allocatesMonotonicIds() {
        FakeNpcPackets packets = new FakeNpcPackets();

        assertThat(packets.allocateEntityId()).isEqualTo(1);
        assertThat(packets.allocateEntityId()).isEqualTo(2);
    }

    @Test
    void tabAddCarriesNameAndSkin() {
        FakeNpcPackets packets = new FakeNpcPackets();
        UUID profileId = UUID.randomUUID();
        TabSkin skin = TabSkin.unsigned("tex");

        FakeNpcPackets.TabAdd add = (FakeNpcPackets.TabAdd) packets.tabAdd(profileId, "Guide", skin);

        assertThat(add.profileId()).isEqualTo(profileId);
        assertThat(add.name()).isEqualTo("Guide");
        assertThat(add.skin()).isEqualTo(skin);
        // The no-flag shorthand seats a listed entry: the NPC shows in the tab list.
        assertThat(add.listed()).isTrue();
    }

    @Test
    void tabAddCarriesTheListedFlagExplicitly() {
        FakeNpcPackets packets = new FakeNpcPackets();
        UUID profileId = UUID.randomUUID();
        TabSkin skin = TabSkin.unsigned("tex");

        FakeNpcPackets.TabAdd unlisted = (FakeNpcPackets.TabAdd) packets.tabAdd(profileId, "Guide", skin, false);
        FakeNpcPackets.TabAdd listed = (FakeNpcPackets.TabAdd) packets.tabAdd(profileId, "Guide", skin, true);

        // listed=false renders the body without a tab-list row: the normal fake-player NPC case; listed=true also
        // shows the row. Either way the entry is added and kept (no remove is part of the add).
        assertThat(unlisted.listed()).isFalse();
        assertThat(unlisted.skin()).isEqualTo(skin);
        assertThat(listed.listed()).isTrue();
    }

    @Test
    void spawnReusesTheProfileIdSoTheSkinLinks() {
        FakeNpcPackets packets = new FakeNpcPackets();
        UUID profileId = UUID.randomUUID();
        int entityId = packets.allocateEntityId();

        FakeNpcPackets.Spawn spawn =
                (FakeNpcPackets.Spawn) packets.spawnPlayer(entityId, profileId, 1.0, 2.0, 3.0, 90.0f, 0.0f);

        assertThat(spawn.entityId()).isEqualTo(entityId);
        assertThat(spawn.profileId()).isEqualTo(profileId);
        assertThat(spawn.yaw()).isEqualTo(90.0f);
    }

    @Test
    void spawnEntityCarriesTheTypeKeyAndEntityUuid() {
        FakeNpcPackets packets = new FakeNpcPackets();
        UUID entityUuid = UUID.randomUUID();
        int entityId = packets.allocateEntityId();

        FakeNpcPackets.SpawnEntity spawn = (FakeNpcPackets.SpawnEntity)
                packets.spawnEntity(entityId, entityUuid, "minecraft:villager", 1.0, 2.0, 3.0, 90.0f, 0.0f);

        assertThat(spawn.entityId()).isEqualTo(entityId);
        assertThat(spawn.entityUuid()).isEqualTo(entityUuid);
        assertThat(spawn.entityTypeKey()).isEqualTo("minecraft:villager");
        assertThat(spawn.yaw()).isEqualTo(90.0f);
    }

    @Test
    void bundleCopiesItsInput() {
        FakeNpcPackets packets = new FakeNpcPackets();
        List<Object> input = new ArrayList<>();
        input.add("a");

        FakeNpcPackets.Bundle bundle = (FakeNpcPackets.Bundle) packets.bundle(input);
        input.add("b");

        assertThat(bundle.packets()).containsExactly("a");
    }

    @Test
    void equipmentCarriesEachSlotItem() {
        FakeNpcPackets packets = new FakeNpcPackets();
        int entityId = packets.allocateEntityId();
        ItemStack item = item();
        Map<EquipmentSlot, ItemStack> items = Map.of(EquipmentSlot.HEAD, item);

        FakeNpcPackets.Equipment equipment = (FakeNpcPackets.Equipment) packets.equipment(entityId, items);

        assertThat(equipment.entityId()).isEqualTo(entityId);
        assertThat(equipment.items()).containsEntry(EquipmentSlot.HEAD, item);
    }

    @Test
    void glowCarriesTheToggle() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.Glow on = (FakeNpcPackets.Glow) packets.glow(7, true);
        FakeNpcPackets.Glow off = (FakeNpcPackets.Glow) packets.glow(7, false);

        assertThat(on.glowing()).isTrue();
        assertThat(off.glowing()).isFalse();
    }

    @Test
    void sharedFlagsComposeOnFireGlowAndInvisibleIntoOnePacket() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.SharedFlags flags = (FakeNpcPackets.SharedFlags) packets.sharedFlags(7, true, false, true);

        assertThat(flags.entityId()).isEqualTo(7);
        assertThat(flags.onFire()).isTrue();
        assertThat(flags.glowing()).isFalse();
        assertThat(flags.invisible()).isTrue();
    }

    @Test
    void silentCarriesTheEntityAndToggle() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.Silent on = (FakeNpcPackets.Silent) packets.silent(7, true);
        FakeNpcPackets.Silent off = (FakeNpcPackets.Silent) packets.silent(7, false);

        assertThat(on.entityId()).isEqualTo(7);
        assertThat(on.silent()).isTrue();
        assertThat(off.silent()).isFalse();
    }

    @Test
    void teamCarriesTheMemberColourRuleAndNametagVisibility() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.TeamSet on =
                (FakeNpcPackets.TeamSet) packets.team("uxmnpc_guide", "guide", NamedColor.RED, true, false);
        FakeNpcPackets.TeamSet off = (FakeNpcPackets.TeamSet) packets.team("uxmnpc_guide", "guide", null, false, true);

        assertThat(on.teamName()).isEqualTo("uxmnpc_guide");
        assertThat(on.memberName()).isEqualTo("guide");
        assertThat(on.color()).isEqualTo(NamedColor.RED);
        assertThat(on.collidable()).isTrue();
        assertThat(on.hideNametag()).isFalse();
        assertThat(off.color()).isNull();
        assertThat(off.collidable()).isFalse();
        assertThat(off.hideNametag()).isTrue();
    }

    @Test
    void poseCarriesTheEntityAndPose() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.PoseSet posed = (FakeNpcPackets.PoseSet) packets.pose(7, NpcPose.SITTING);

        assertThat(posed.entityId()).isEqualTo(7);
        assertThat(posed.pose()).isEqualTo(NpcPose.SITTING);
    }

    @Test
    void scaleCarriesTheEntityAndScale() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.Scale scaled = (FakeNpcPackets.Scale) packets.scale(7, 2.5);

        assertThat(scaled.entityId()).isEqualTo(7);
        assertThat(scaled.scale()).isEqualTo(2.5);
    }

    @Test
    void babyCarriesTheEntityAndToggle() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.Baby on = (FakeNpcPackets.Baby) packets.baby(7, true);
        FakeNpcPackets.Baby off = (FakeNpcPackets.Baby) packets.baby(7, false);

        assertThat(on.entityId()).isEqualTo(7);
        assertThat(on.baby()).isTrue();
        assertThat(off.baby()).isFalse();
    }

    @Test
    void monsterFamilyBabiesAreSeparateBuildersFromTheAgeableOne() {
        // The zombie line, piglins, and zoglins extend Monster, not AgeableMob, so their baby flag lives at a
        // different data index; each gets its own builder so a value never lands on the wrong field.
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.ZombieBaby zombie = (FakeNpcPackets.ZombieBaby) packets.zombieBaby(7, true);
        FakeNpcPackets.PiglinBaby piglin = (FakeNpcPackets.PiglinBaby) packets.piglinBaby(8, true);
        FakeNpcPackets.ZoglinBaby zoglin = (FakeNpcPackets.ZoglinBaby) packets.zoglinBaby(9, false);

        assertThat(zombie.entityId()).isEqualTo(7);
        assertThat(zombie.baby()).isTrue();
        assertThat(piglin.entityId()).isEqualTo(8);
        assertThat(piglin.baby()).isTrue();
        assertThat(zoglin.entityId()).isEqualTo(9);
        assertThat(zoglin.baby()).isFalse();
    }

    @Test
    void villagerDataCarriesTypeProfessionAndLevel() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.VillagerData data =
                (FakeNpcPackets.VillagerData) packets.villagerData(7, "desert", "librarian", 3);

        assertThat(data.entityId()).isEqualTo(7);
        assertThat(data.type()).isEqualTo("desert");
        assertThat(data.profession()).isEqualTo("librarian");
        assertThat(data.level()).isEqualTo(3);
    }

    @Test
    void slimeSizeCarriesTheEntityAndSize() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.SlimeSize sized = (FakeNpcPackets.SlimeSize) packets.slimeSize(7, 4);

        assertThat(sized.entityId()).isEqualTo(7);
        assertThat(sized.size()).isEqualTo(4);
    }

    @Test
    void chargedCarriesTheEntityAndToggle() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.Charged on = (FakeNpcPackets.Charged) packets.charged(7, true);
        FakeNpcPackets.Charged off = (FakeNpcPackets.Charged) packets.charged(7, false);

        assertThat(on.entityId()).isEqualTo(7);
        assertThat(on.charged()).isTrue();
        assertThat(off.charged()).isFalse();
    }

    @Test
    void horseVariantCarriesTheEntityAndTheColourAndMarkings() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.HorseVariant variant = (FakeNpcPackets.HorseVariant) packets.horseVariant(7, 3, 2);

        assertThat(variant.entityId()).isEqualTo(7);
        assertThat(variant.color()).isEqualTo(3);
        assertThat(variant.markings()).isEqualTo(2);
    }

    @Test
    void llamaVariantCarriesTheEntityAndVariant() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.LlamaVariant variant = (FakeNpcPackets.LlamaVariant) packets.llamaVariant(7, 2);

        assertThat(variant.entityId()).isEqualTo(7);
        assertThat(variant.variant()).isEqualTo(2);
    }

    @Test
    void sheepWoolCarriesTheEntityColourAndSheared() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.SheepWool wool = (FakeNpcPackets.SheepWool) packets.sheepWool(7, 14, true);

        assertThat(wool.entityId()).isEqualTo(7);
        assertThat(wool.color()).isEqualTo(14);
        assertThat(wool.sheared()).isTrue();
    }

    @Test
    void wolfCollarCarriesTheEntityAndColour() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.WolfCollar collar = (FakeNpcPackets.WolfCollar) packets.wolfCollar(8, 11);

        assertThat(collar.entityId()).isEqualTo(8);
        assertThat(collar.color()).isEqualTo(11);
    }

    @Test
    void shulkerColorCarriesTheEntityAndColour() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.ShulkerColor color = (FakeNpcPackets.ShulkerColor) packets.shulkerColor(9, 5);

        assertThat(color.entityId()).isEqualTo(9);
        assertThat(color.color()).isEqualTo(5);
    }

    @Test
    void shulkerPeekCarriesTheEntityAndOpening() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.ShulkerPeek peek = (FakeNpcPackets.ShulkerPeek) packets.shulkerPeek(10, 100);

        assertThat(peek.entityId()).isEqualTo(10);
        assertThat(peek.peek()).isEqualTo(100);
    }

    @Test
    void spinAttackCarriesTheEntityAndFlag() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.SpinAttack spin = (FakeNpcPackets.SpinAttack) packets.spinAttack(11, true);

        assertThat(spin.entityId()).isEqualTo(11);
        assertThat(spin.spinning()).isTrue();
    }

    @Test
    void sleepingPositionCarriesTheEntityAndBlock() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.SleepingPosition bed = (FakeNpcPackets.SleepingPosition) packets.sleepingPosition(12, 3, -64, 7);

        assertThat(bed.entityId()).isEqualTo(12);
        assertThat(bed.x()).isEqualTo(3);
        assertThat(bed.y()).isEqualTo(-64);
        assertThat(bed.z()).isEqualTo(7);
    }

    @Test
    void shulkerAttachFaceCarriesTheEntityAndDirection() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.ShulkerAttachFace face = (FakeNpcPackets.ShulkerAttachFace) packets.shulkerAttachFace(10, "up");

        assertThat(face.entityId()).isEqualTo(10);
        assertThat(face.face()).isEqualTo("up");
    }

    @Test
    void pandaGeneCarriesTheEntityAndGene() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.PandaGene gene = (FakeNpcPackets.PandaGene) packets.pandaGene(11, 4);

        assertThat(gene.entityId()).isEqualTo(11);
        assertThat(gene.gene()).isEqualTo(4);
    }

    @Test
    void goatScreamingCarriesTheEntityAndFlag() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.GoatScreaming screaming = (FakeNpcPackets.GoatScreaming) packets.goatScreaming(12, true);

        assertThat(screaming.entityId()).isEqualTo(12);
        assertThat(screaming.screaming()).isTrue();
    }

    @Test
    void allayDancingCarriesTheEntityAndFlag() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.AllayDancing dancing = (FakeNpcPackets.AllayDancing) packets.allayDancing(13, true);

        assertThat(dancing.entityId()).isEqualTo(13);
        assertThat(dancing.dancing()).isTrue();
    }

    @Test
    void piglinDancingCarriesTheEntityAndFlag() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.PiglinDancing dancing = (FakeNpcPackets.PiglinDancing) packets.piglinDancing(14, false);

        assertThat(dancing.entityId()).isEqualTo(14);
        assertThat(dancing.dancing()).isFalse();
    }

    @Test
    void camelDashCarriesTheEntityAndFlag() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.CamelDash dash = (FakeNpcPackets.CamelDash) packets.camelDash(15, true);

        assertThat(dash.entityId()).isEqualTo(15);
        assertThat(dash.dashing()).isTrue();
    }

    @Test
    void beeFlagsCarriesTheEntityAndFlags() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.BeeFlags bee = (FakeNpcPackets.BeeFlags) packets.beeFlags(16, true, false, true);

        assertThat(bee.entityId()).isEqualTo(16);
        assertThat(bee.nectar()).isTrue();
        assertThat(bee.rolling()).isFalse();
        assertThat(bee.stung()).isTrue();
    }

    @Test
    void vexChargingCarriesTheEntityAndFlag() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.VexCharging charging = (FakeNpcPackets.VexCharging) packets.vexCharging(17, true);

        assertThat(charging.entityId()).isEqualTo(17);
        assertThat(charging.charging()).isTrue();
    }

    @Test
    void tropicalFishVariantCarriesTheEntityAndIndex() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.TropicalFishVariant variant =
                (FakeNpcPackets.TropicalFishVariant) packets.tropicalFishVariant(18, 7);

        assertThat(variant.entityId()).isEqualTo(18);
        assertThat(variant.variantIndex()).isEqualTo(7);
    }

    @Test
    void armorStandFlagsCarryTheEntityAndEachFlag() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.ArmorStandFlags flags =
                (FakeNpcPackets.ArmorStandFlags) packets.armorStandFlags(19, true, true, false, false);

        assertThat(flags.entityId()).isEqualTo(19);
        assertThat(flags.small()).isTrue();
        assertThat(flags.showArms()).isTrue();
        assertThat(flags.noBasePlate()).isFalse();
        assertThat(flags.marker()).isFalse();
    }

    @Test
    void armorStandPoseCarriesTheEntityPartAndAngles() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.ArmorStandPose pose =
                (FakeNpcPackets.ArmorStandPose) packets.armorStandPose(20, ArmorStandPart.LEFT_ARM, 10f, -20f, 30.5f);

        assertThat(pose.entityId()).isEqualTo(20);
        assertThat(pose.part()).isEqualTo(ArmorStandPart.LEFT_ARM);
        assertThat(pose.x()).isEqualTo(10f);
        assertThat(pose.y()).isEqualTo(-20f);
        assertThat(pose.z()).isEqualTo(30.5f);
    }

    @Test
    void interactionSizeCarriesTheEntityAndDimensions() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.InteractionSize size = (FakeNpcPackets.InteractionSize) packets.interactionSize(21, 1.5f, 2.0f);

        assertThat(size.entityId()).isEqualTo(21);
        assertThat(size.width()).isEqualTo(1.5f);
        assertThat(size.height()).isEqualTo(2.0f);
    }

    @Test
    void blockDisplayStateCarriesTheEntityAndBlock() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.BlockDisplayState state =
                (FakeNpcPackets.BlockDisplayState) packets.blockDisplayState(23, Material.STONE.createBlockData());

        assertThat(state.entityId()).isEqualTo(23);
        assertThat(state.material()).isEqualTo(Material.STONE);
    }

    @Test
    void itemDisplayItemCarriesTheEntityAndItem() {
        FakeNpcPackets packets = new FakeNpcPackets();
        ItemStack item = item();

        FakeNpcPackets.ItemDisplayItem display = (FakeNpcPackets.ItemDisplayItem) packets.itemDisplayItem(24, item);

        assertThat(display.entityId()).isEqualTo(24);
        assertThat(display.material()).isEqualTo(item.getType());
    }

    @Test
    void textDisplayTextCarriesTheEntityAndText() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.TextDisplayText text = (FakeNpcPackets.TextDisplayText)
                packets.textDisplayText(25, net.kyori.adventure.text.Component.text("Welcome"));

        assertThat(text.entityId()).isEqualTo(25);
        assertThat(text.plain()).isEqualTo("Welcome");
    }

    @Test
    void displayScaleCarriesTheEntityAndScale() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.DisplayScale scale = (FakeNpcPackets.DisplayScale) packets.displayScale(26, 2.5f);

        assertThat(scale.entityId()).isEqualTo(26);
        assertThat(scale.scale()).isEqualTo(2.5f);
    }

    @Test
    void displayScaleXyzCarriesTheEntityAndAxes() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.DisplayScaleXyz scale = (FakeNpcPackets.DisplayScaleXyz) packets.displayScale(30, 1f, 2f, 3f);

        assertThat(scale.entityId()).isEqualTo(30);
        assertThat(scale.x()).isEqualTo(1f);
        assertThat(scale.y()).isEqualTo(2f);
        assertThat(scale.z()).isEqualTo(3f);
    }

    @Test
    void displayTranslationCarriesTheEntityAndOffset() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.DisplayTranslation t =
                (FakeNpcPackets.DisplayTranslation) packets.displayTranslation(31, 0f, -0.5f, 1f);

        assertThat(t.entityId()).isEqualTo(31);
        assertThat(t.y()).isEqualTo(-0.5f);
        assertThat(t.z()).isEqualTo(1f);
    }

    @Test
    void displayBillboardCarriesTheEntityAndConstraint() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.DisplayBillboard billboard =
                (FakeNpcPackets.DisplayBillboard) packets.displayBillboard(27, (byte) 3);

        assertThat(billboard.entityId()).isEqualTo(27);
        assertThat(billboard.constraint()).isEqualTo((byte) 3);
    }

    @Test
    void textDisplayBackgroundCarriesTheEntityAndColour() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.TextDisplayBackground background =
                (FakeNpcPackets.TextDisplayBackground) packets.textDisplayBackground(28, 0x80FF0000);

        assertThat(background.entityId()).isEqualTo(28);
        assertThat(background.argb()).isEqualTo(0x80FF0000);
    }

    @Test
    void textDisplayLineWidthCarriesTheEntityAndWidth() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.TextDisplayLineWidth width =
                (FakeNpcPackets.TextDisplayLineWidth) packets.textDisplayLineWidth(29, 150);

        assertThat(width.entityId()).isEqualTo(29);
        assertThat(width.lineWidth()).isEqualTo(150);
    }

    @Test
    void parrotVariantCarriesTheEntityAndVariant() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.ParrotVariant variant = (FakeNpcPackets.ParrotVariant) packets.parrotVariant(7, 4);

        assertThat(variant.entityId()).isEqualTo(7);
        assertThat(variant.variant()).isEqualTo(4);
    }

    @Test
    void axolotlVariantCarriesTheEntityAndVariant() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.AxolotlVariant variant = (FakeNpcPackets.AxolotlVariant) packets.axolotlVariant(7, 3);

        assertThat(variant.entityId()).isEqualTo(7);
        assertThat(variant.variant()).isEqualTo(3);
    }

    @Test
    void foxTypeCarriesTheEntityAndType() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.FoxType fox = (FakeNpcPackets.FoxType) packets.foxType(7, 1);

        assertThat(fox.entityId()).isEqualTo(7);
        assertThat(fox.type()).isEqualTo(1);
    }

    @Test
    void rabbitTypeCarriesTheEntityAndTypeIncludingTheKillerVariant() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.RabbitType rabbit = (FakeNpcPackets.RabbitType) packets.rabbitType(7, 99);

        assertThat(rabbit.entityId()).isEqualTo(7);
        // 99 is the killer (toast) rabbit on the wire, carried straight through as the raw type id.
        assertThat(rabbit.type()).isEqualTo(99);
    }

    @Test
    void catVariantCarriesTheEntityAndName() {
        // Cat and frog variants are dynamic-registry values resolved by name; the NMS impl reaches the live
        // server registry, so the port carries only the name and the contract proves it round-trips through.
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.CatVariant cat = (FakeNpcPackets.CatVariant) packets.catVariant(7, "calico");

        assertThat(cat.entityId()).isEqualTo(7);
        assertThat(cat.name()).isEqualTo("calico");
    }

    @Test
    void frogVariantCarriesTheEntityAndName() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.WolfVariant wolf = (FakeNpcPackets.WolfVariant) packets.wolfVariant(8, "ashen");
        assertThat(wolf.entityId()).isEqualTo(8);
        assertThat(wolf.name()).isEqualTo("ashen");
        FakeNpcPackets.ChickenVariant chicken = (FakeNpcPackets.ChickenVariant) packets.chickenVariant(9, "warm");
        assertThat(chicken.name()).isEqualTo("warm");
        FakeNpcPackets.CowVariant cow = (FakeNpcPackets.CowVariant) packets.cowVariant(10, "cold");
        assertThat(cow.name()).isEqualTo("cold");
        FakeNpcPackets.PigVariant pig = (FakeNpcPackets.PigVariant) packets.pigVariant(11, "temperate");
        assertThat(pig.name()).isEqualTo("temperate");
        FakeNpcPackets.AxolotlPlayingDead dead =
                (FakeNpcPackets.AxolotlPlayingDead) packets.axolotlPlayingDead(12, true);
        assertThat(dead.playingDead()).isTrue();
        FakeNpcPackets.RaiderCelebrating celeb = (FakeNpcPackets.RaiderCelebrating) packets.raiderCelebrating(13, true);
        assertThat(celeb.celebrating()).isTrue();
        FakeNpcPackets.TameableSitting sit = (FakeNpcPackets.TameableSitting) packets.tameableSitting(14, true);
        assertThat(sit.entityId()).isEqualTo(14);
        assertThat(sit.sitting()).isTrue();
        FakeNpcPackets.FoxFlags fox = (FakeNpcPackets.FoxFlags) packets.foxFlags(15, true, false, true);
        assertThat(fox.sitting()).isTrue();
        assertThat(fox.sleeping()).isFalse();
        assertThat(fox.crouching()).isTrue();
        FakeNpcPackets.PandaEating panda = (FakeNpcPackets.PandaEating) packets.pandaEating(17, true);
        assertThat(panda.eating()).isTrue();
        FakeNpcPackets.SnifferState sniffer = (FakeNpcPackets.SnifferState) packets.snifferState(18, "digging");
        assertThat(sniffer.state()).isEqualTo("digging");
        FakeNpcPackets.ArmadilloState armadillo = (FakeNpcPackets.ArmadilloState) packets.armadilloState(19, "rolling");
        assertThat(armadillo.state()).isEqualTo("rolling");
        FakeNpcPackets.UsingItem use = (FakeNpcPackets.UsingItem) packets.usingItem(20, true, true);
        assertThat(use.using()).isTrue();
        assertThat(use.offHand()).isTrue();
        FakeNpcPackets.FrozenTicks frozen = (FakeNpcPackets.FrozenTicks) packets.frozenTicks(21, 200);
        assertThat(frozen.ticks()).isEqualTo(200);

        FakeNpcPackets.FrogVariant frog = (FakeNpcPackets.FrogVariant) packets.frogVariant(7, "warm");

        assertThat(frog.entityId()).isEqualTo(7);
        assertThat(frog.name()).isEqualTo("warm");
    }

    @Test
    void everyNpcPoseNamesAServerPose() {
        // The NMS impl resolves a Pose by the constant's server name; this proves each NpcPose has a counterpart so
        // the by-name Pose.valueOf never throws at render time. GLIDING maps to the server's FALL_FLYING.
        for (NpcPose pose : NpcPose.values()) {
            assertThat(SERVER_POSE_NAMES).contains(pose.serverName());
        }
        assertThat(NpcPose.GLIDING.serverName()).isEqualTo("FALL_FLYING");
        assertThat(NpcPose.STANDING.serverName()).isEqualTo("STANDING");
    }

    @Test
    void glowColorCarriesTheTeamMemberAndColor() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.GlowColor colored =
                (FakeNpcPackets.GlowColor) packets.glowColor("uxmnpc_guide", "guide", NamedColor.RED);

        assertThat(colored.teamName()).isEqualTo("uxmnpc_guide");
        assertThat(colored.memberName()).isEqualTo("guide");
        assertThat(colored.color()).isEqualTo(NamedColor.RED);
    }

    @Test
    void glowColorRemoveCarriesTheTeamName() {
        FakeNpcPackets packets = new FakeNpcPackets();

        FakeNpcPackets.GlowColorRemove removed = (FakeNpcPackets.GlowColorRemove) packets.glowColorRemove("guide");

        assertThat(removed.teamName()).isEqualTo("guide");
    }

    @Test
    void everyNamedColorNamesAChatFormatting() {
        // The NMS impl maps a NamedColor onto ChatFormatting by name; this proves each constant has a counterpart
        // so the by-name mapping never throws at render time. The 16 ChatFormatting colour names are fixed by the
        // protocol, so pinning them here is stable.
        for (NamedColor color : NamedColor.values()) {
            assertThat(CHAT_FORMATTING_COLORS).contains(color.name());
        }
    }

    @Test
    void sendRecordsTheRecipient() {
        FakeNpcPackets packets = new FakeNpcPackets();
        Player viewer = (Player) java.lang.reflect.Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> null);

        packets.send(viewer, "packet");

        assertThat(packets.sends).containsExactly(new FakeNpcPackets.Sent(viewer, "packet"));
    }

    /** A real (mocked) {@link ItemStack} the equipment contract carries through the fake. */
    private static ItemStack item() {
        return new ItemStackMock(Material.DIAMOND_HELMET);
    }

    /** A recording fake whose packets are sentinel records so the contract can be asserted without NMS. */
    private static final class FakeNpcPackets implements NpcPackets {

        record TabAdd(UUID profileId, String name, @Nullable TabSkin skin, boolean listed) {}

        record TabRemove(UUID profileId) {}

        record Spawn(int entityId, UUID profileId, double x, double y, double z, float yaw, float pitch) {}

        record SpawnEntity(
                int entityId,
                UUID entityUuid,
                String entityTypeKey,
                double x,
                double y,
                double z,
                float yaw,
                float pitch) {}

        record HeadLook(int entityId, float yaw) {}

        record BodyLook(int entityId, float yaw, float pitch) {}

        record Teleport(int entityId, double x, double y, double z, float yaw, float pitch) {}

        record Remove(int entityId) {}

        record Equipment(int entityId, Map<EquipmentSlot, ItemStack> items) {}

        record Glow(int entityId, boolean glowing) {}

        record SharedFlags(int entityId, boolean onFire, boolean glowing, boolean invisible) {}

        record Silent(int entityId, boolean silent) {}

        record TeamSet(
                String teamName,
                String memberName,
                @Nullable NamedColor color,
                boolean collidable,
                boolean hideNametag) {}

        record PoseSet(int entityId, NpcPose pose) {}

        record Scale(int entityId, double scale) {}

        record Baby(int entityId, boolean baby) {}

        record ZombieBaby(int entityId, boolean baby) {}

        record PiglinBaby(int entityId, boolean baby) {}

        record ZoglinBaby(int entityId, boolean baby) {}

        record VillagerData(int entityId, String type, String profession, int level) {}

        record SlimeSize(int entityId, int size) {}

        record Charged(int entityId, boolean charged) {}

        record HorseVariant(int entityId, int color, int markings) {}

        record LlamaVariant(int entityId, int variant) {}

        record SheepWool(int entityId, int color, boolean sheared) {}

        record WolfCollar(int entityId, int color) {}

        record ShulkerColor(int entityId, int color) {}

        record ShulkerPeek(int entityId, int peek) {}

        record ShulkerAttachFace(int entityId, String face) {}

        record SpinAttack(int entityId, boolean spinning) {}

        record SleepingPosition(int entityId, int x, int y, int z) {}

        record PandaGene(int entityId, int gene) {}

        record GoatScreaming(int entityId, boolean screaming) {}

        record AllayDancing(int entityId, boolean dancing) {}

        record PiglinDancing(int entityId, boolean dancing) {}

        record CamelDash(int entityId, boolean dashing) {}

        record BeeFlags(int entityId, boolean nectar, boolean rolling, boolean stung) {}

        record VexCharging(int entityId, boolean charging) {}

        record TropicalFishVariant(int entityId, int variantIndex) {}

        record ArmorStandFlags(int entityId, boolean small, boolean showArms, boolean noBasePlate, boolean marker) {}

        record ArmorStandPose(int entityId, ArmorStandPart part, float x, float y, float z) {}

        record InteractionSize(int entityId, float width, float height) {}

        record BlockDisplayState(int entityId, Material material) {}

        record ItemDisplayItem(int entityId, Material material) {}

        record TextDisplayText(int entityId, String plain) {}

        record DisplayScale(int entityId, float scale) {}

        record DisplayScaleXyz(int entityId, float x, float y, float z) {}

        record DisplayTranslation(int entityId, float x, float y, float z) {}

        record DisplayBillboard(int entityId, byte constraint) {}

        record TextDisplayBackground(int entityId, int argb) {}

        record TextDisplayLineWidth(int entityId, int lineWidth) {}

        record ParrotVariant(int entityId, int variant) {}

        record AxolotlVariant(int entityId, int variant) {}

        record FoxType(int entityId, int type) {}

        record RabbitType(int entityId, int type) {}

        record CatVariant(int entityId, String name) {}

        record FrogVariant(int entityId, String name) {}

        record WolfVariant(int entityId, String name) {}

        record ChickenVariant(int entityId, String name) {}

        record CowVariant(int entityId, String name) {}

        record PigVariant(int entityId, String name) {}

        record AxolotlPlayingDead(int entityId, boolean playingDead) {}

        record RaiderCelebrating(int entityId, boolean celebrating) {}

        record TameableSitting(int entityId, boolean sitting) {}

        record FoxFlags(int entityId, boolean sitting, boolean sleeping, boolean crouching) {}

        record PandaEating(int entityId, boolean eating) {}

        record SnifferState(int entityId, String state) {}

        record ArmadilloState(int entityId, String state) {}

        record UsingItem(int entityId, boolean using, boolean offHand) {}

        record FrozenTicks(int entityId, int ticks) {}

        record GlowColor(
                String teamName,
                String memberName,
                @Nullable NamedColor color) {}

        record GlowColorRemove(String teamName) {}

        record Bundle(List<Object> packets) {}

        record Sent(Player viewer, Object packet) {}

        private final AtomicInteger nextId = new AtomicInteger(1);
        private final List<Sent> sends = new ArrayList<>();

        @Override
        public int allocateEntityId() {
            return nextId.getAndIncrement();
        }

        @Override
        public Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin) {
            return tabAdd(profileId, name, skin, true);
        }

        @Override
        public Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin, boolean listed) {
            return new TabAdd(profileId, name, skin, listed);
        }

        @Override
        public Object tabRemove(UUID profileId) {
            return new TabRemove(profileId);
        }

        @Override
        public Object spawnPlayer(int entityId, UUID profileId, double x, double y, double z, float yaw, float pitch) {
            return new Spawn(entityId, profileId, x, y, z, yaw, pitch);
        }

        @Override
        public Object spawnEntity(
                int entityId,
                UUID entityUuid,
                String entityTypeKey,
                double x,
                double y,
                double z,
                float yaw,
                float pitch) {
            return new SpawnEntity(entityId, entityUuid, entityTypeKey, x, y, z, yaw, pitch);
        }

        @Override
        public Object headLook(int entityId, float yaw) {
            return new HeadLook(entityId, yaw);
        }

        @Override
        public Object bodyLook(int entityId, float yaw, float pitch) {
            return new BodyLook(entityId, yaw, pitch);
        }

        @Override
        public Object teleport(int entityId, double x, double y, double z, float yaw, float pitch) {
            return new Teleport(entityId, x, y, z, yaw, pitch);
        }

        @Override
        public Object remove(int entityId) {
            return new Remove(entityId);
        }

        @Override
        public Object equipment(int entityId, Map<EquipmentSlot, ItemStack> items) {
            return new Equipment(entityId, new LinkedHashMap<>(items));
        }

        @Override
        public Object glow(int entityId, boolean glowing) {
            return new Glow(entityId, glowing);
        }

        @Override
        public Object sharedFlags(int entityId, boolean onFire, boolean glowing, boolean invisible) {
            return new SharedFlags(entityId, onFire, glowing, invisible);
        }

        @Override
        public Object silent(int entityId, boolean silent) {
            return new Silent(entityId, silent);
        }

        @Override
        public Object team(
                String teamName,
                String memberName,
                @Nullable NamedColor color,
                boolean collidable,
                boolean hideNametag) {
            return new TeamSet(teamName, memberName, color, collidable, hideNametag);
        }

        @Override
        public Object pose(int entityId, NpcPose pose) {
            return new PoseSet(entityId, pose);
        }

        @Override
        public Object scale(int entityId, double scale) {
            return new Scale(entityId, scale);
        }

        @Override
        public Object baby(int entityId, boolean baby) {
            return new Baby(entityId, baby);
        }

        @Override
        public Object zombieBaby(int entityId, boolean baby) {
            return new ZombieBaby(entityId, baby);
        }

        @Override
        public Object piglinBaby(int entityId, boolean baby) {
            return new PiglinBaby(entityId, baby);
        }

        @Override
        public Object zoglinBaby(int entityId, boolean baby) {
            return new ZoglinBaby(entityId, baby);
        }

        @Override
        public Object villagerData(int entityId, String type, String profession, int level) {
            return new VillagerData(entityId, type, profession, level);
        }

        @Override
        public Object slimeSize(int entityId, int size) {
            return new SlimeSize(entityId, size);
        }

        @Override
        public Object charged(int entityId, boolean charged) {
            return new Charged(entityId, charged);
        }

        @Override
        public Object horseVariant(int entityId, int color, int markings) {
            return new HorseVariant(entityId, color, markings);
        }

        @Override
        public Object llamaVariant(int entityId, int variant) {
            return new LlamaVariant(entityId, variant);
        }

        @Override
        public Object sheepWool(int entityId, int color, boolean sheared) {
            return new SheepWool(entityId, color, sheared);
        }

        @Override
        public Object wolfCollar(int entityId, int color) {
            return new WolfCollar(entityId, color);
        }

        @Override
        public Object shulkerColor(int entityId, int color) {
            return new ShulkerColor(entityId, color);
        }

        @Override
        public Object shulkerPeek(int entityId, int peek) {
            return new ShulkerPeek(entityId, peek);
        }

        @Override
        public Object shulkerAttachFace(int entityId, String face) {
            return new ShulkerAttachFace(entityId, face);
        }

        @Override
        public Object spinAttack(int entityId, boolean spinning) {
            return new SpinAttack(entityId, spinning);
        }

        @Override
        public Object sleepingPosition(int entityId, int x, int y, int z) {
            return new SleepingPosition(entityId, x, y, z);
        }

        @Override
        public Object noSleepingPosition(int entityId) {
            return new SleepingPosition(entityId, 0, 0, 0);
        }

        @Override
        public Object pandaGene(int entityId, int gene) {
            return new PandaGene(entityId, gene);
        }

        @Override
        public Object goatScreaming(int entityId, boolean screaming) {
            return new GoatScreaming(entityId, screaming);
        }

        @Override
        public Object allayDancing(int entityId, boolean dancing) {
            return new AllayDancing(entityId, dancing);
        }

        @Override
        public Object piglinDancing(int entityId, boolean dancing) {
            return new PiglinDancing(entityId, dancing);
        }

        @Override
        public Object camelDash(int entityId, boolean dashing) {
            return new CamelDash(entityId, dashing);
        }

        @Override
        public Object beeFlags(int entityId, boolean nectar, boolean rolling, boolean stung) {
            return new BeeFlags(entityId, nectar, rolling, stung);
        }

        @Override
        public Object vexCharging(int entityId, boolean charging) {
            return new VexCharging(entityId, charging);
        }

        @Override
        public Object tropicalFishVariant(int entityId, int variantIndex) {
            return new TropicalFishVariant(entityId, variantIndex);
        }

        @Override
        public Object armorStandFlags(
                int entityId, boolean small, boolean showArms, boolean noBasePlate, boolean marker) {
            return new ArmorStandFlags(entityId, small, showArms, noBasePlate, marker);
        }

        @Override
        public Object armorStandPose(int entityId, ArmorStandPart part, float x, float y, float z) {
            return new ArmorStandPose(entityId, part, x, y, z);
        }

        @Override
        public Object interactionSize(int entityId, float width, float height) {
            return new InteractionSize(entityId, width, height);
        }

        @Override
        public Object blockDisplayState(int entityId, BlockData blockData) {
            return new BlockDisplayState(entityId, blockData.getMaterial());
        }

        @Override
        public Object itemDisplayItem(int entityId, ItemStack item) {
            return new ItemDisplayItem(entityId, item.getType());
        }

        @Override
        public Object textDisplayText(int entityId, net.kyori.adventure.text.Component text) {
            return new TextDisplayText(
                    entityId,
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(text));
        }

        @Override
        public Object displayScale(int entityId, float scale) {
            return new DisplayScale(entityId, scale);
        }

        @Override
        public Object displayScale(int entityId, float x, float y, float z) {
            return new DisplayScaleXyz(entityId, x, y, z);
        }

        @Override
        public Object displayTranslation(int entityId, float x, float y, float z) {
            return new DisplayTranslation(entityId, x, y, z);
        }

        @Override
        public Object displayBillboard(int entityId, byte constraint) {
            return new DisplayBillboard(entityId, constraint);
        }

        @Override
        public Object textDisplayBackground(int entityId, int argb) {
            return new TextDisplayBackground(entityId, argb);
        }

        @Override
        public Object textDisplayLineWidth(int entityId, int lineWidth) {
            return new TextDisplayLineWidth(entityId, lineWidth);
        }

        @Override
        public Object parrotVariant(int entityId, int variant) {
            return new ParrotVariant(entityId, variant);
        }

        @Override
        public Object axolotlVariant(int entityId, int variant) {
            return new AxolotlVariant(entityId, variant);
        }

        @Override
        public Object foxType(int entityId, int type) {
            return new FoxType(entityId, type);
        }

        @Override
        public Object rabbitType(int entityId, int type) {
            return new RabbitType(entityId, type);
        }

        @Override
        public Object catVariant(int entityId, String name) {
            return new CatVariant(entityId, name);
        }

        @Override
        public Object frogVariant(int entityId, String name) {
            return new FrogVariant(entityId, name);
        }

        @Override
        public Object wolfVariant(int entityId, String name) {
            return new WolfVariant(entityId, name);
        }

        @Override
        public Object chickenVariant(int entityId, String name) {
            return new ChickenVariant(entityId, name);
        }

        @Override
        public Object cowVariant(int entityId, String name) {
            return new CowVariant(entityId, name);
        }

        @Override
        public Object pigVariant(int entityId, String name) {
            return new PigVariant(entityId, name);
        }

        @Override
        public Object axolotlPlayingDead(int entityId, boolean playingDead) {
            return new AxolotlPlayingDead(entityId, playingDead);
        }

        @Override
        public Object raiderCelebrating(int entityId, boolean celebrating) {
            return new RaiderCelebrating(entityId, celebrating);
        }

        @Override
        public Object tameableSitting(int entityId, boolean sitting) {
            return new TameableSitting(entityId, sitting);
        }

        @Override
        public Object foxFlags(int entityId, boolean sitting, boolean sleeping, boolean crouching) {
            return new FoxFlags(entityId, sitting, sleeping, crouching);
        }

        @Override
        public Object pandaEating(int entityId, boolean eating) {
            return new PandaEating(entityId, eating);
        }

        @Override
        public Object snifferState(int entityId, String state) {
            return new SnifferState(entityId, state);
        }

        @Override
        public Object armadilloState(int entityId, String state) {
            return new ArmadilloState(entityId, state);
        }

        @Override
        public Object usingItem(int entityId, boolean using, boolean offHand) {
            return new UsingItem(entityId, using, offHand);
        }

        @Override
        public Object frozenTicks(int entityId, int ticks) {
            return new FrozenTicks(entityId, ticks);
        }

        @Override
        public Object glowColor(String teamName, String memberName, @Nullable NamedColor color) {
            return new GlowColor(teamName, memberName, color);
        }

        @Override
        public Object glowColorRemove(String teamName) {
            return new GlowColorRemove(teamName);
        }

        @Override
        public Object bundle(List<Object> packets) {
            return new Bundle(List.copyOf(packets));
        }

        @Override
        public void send(Player viewer, Object packet) {
            sends.add(new Sent(viewer, packet));
        }
    }
}
