package com.uxplima.uxmlib.packet.npc.internal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.uxplima.uxmlib.npc.PacketSender;
import com.uxplima.uxmlib.packet.Bundles;
import com.uxplima.uxmlib.packet.Codecs;
import com.uxplima.uxmlib.packet.Components;
import com.uxplima.uxmlib.packet.EntityIds;
import com.uxplima.uxmlib.packet.GameProfiles;
import com.uxplima.uxmlib.packet.Reflect;
import com.uxplima.uxmlib.packet.ServerInternals;
import com.uxplima.uxmlib.packet.VanillaEntityTypes;
import com.uxplima.uxmlib.packet.npc.ArmorStandPart;
import com.uxplima.uxmlib.packet.npc.ByteAngle;
import com.uxplima.uxmlib.packet.npc.EquipmentSlot;
import com.uxplima.uxmlib.packet.npc.HorseVariant;
import com.uxplima.uxmlib.packet.npc.NamedColor;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import com.uxplima.uxmlib.packet.npc.NpcPose;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.pig.PigVariant;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The sole NMS-bearing class of the packet NPC layer: it builds the real Mojang-mapped packets that spawn and
 * steer a fake-player NPC per viewer and writes them through the connection. Quarantining {@code net.minecraft}
 * to one class follows the same precedent as {@code uxmlib-npc}'s {@code ChannelResolver}, the nametag
 * renderer's {@code NmsNametagPackets}, and the tablist renderer's {@code NmsTabListPackets}.
 *
 * <p>Built against the Mojang-mapped 1.21.11 dev bundle; Paper's runtime remapper maps these back to the
 * server's own mappings at load. Two construction notes that are easy to get wrong:
 *
 * <ul>
 *   <li><b>Spawning an entity.</b> Since 1.20.2 there is no separate add-player packet; every NPC: a fake
 *       player or a mob, spawns through {@code ClientboundAddEntityPacket}, so both spawn methods share one
 *       builder that differs only in the {@code EntityType} and the spawn UUID. Both types come out of the
 *       entity-type registry rather than off a constant, since the class those constants live on is one of the
 *       few internals that has been renamed across lines. A fake player passes the player type and the profile
 *       id as the spawn UUID, which must equal the id from the player-info ADD entry or the client will not
 *       link the skin to the entity; a mob passes the type for its key and an opaque UUID with no skin to
 *       bind. That public constructor packs the raw degree rotations itself (via {@code Mth.packDegrees}), so
 *       spawn passes raw floats; the standalone look and teleport packets instead take a pre-packed byte,
 *       which {@link ByteAngle} produces with the same {@code floor} math so the two stay in step.
 *   <li><b>Head rotation.</b> {@code ClientboundRotateHeadPacket} exposes only an {@code Entity}-bound public
 *       constructor and a private buffer one, so it is built through its public stream codec exactly like the
 *       passenger packet in {@code NmsNametagPackets}: write the wire form (entity id, head-yaw byte), decode.
 * </ul>
 *
 * <p>The player-info ADD entry carries the name and skin so the client can render the body; it is assembled
 * through Paper's public {@code ClientboundPlayerInfoUpdatePacket(EnumSet<Action>, List<Entry>)} constructor,
 * the same path {@code NmsTabListPackets} uses. The {@code GameProfile} {@code textures} property is the same
 * five lines as the tablist builder; it is replicated here rather than shared to avoid coupling the two
 * renderers through an extracted helper.
 *
 * <p>Three more construction notes cover the equipment and glow packets:
 *
 * <ul>
 *   <li><b>Equipment.</b> {@code ClientboundSetEquipmentPacket(int, List<Pair<EquipmentSlot, ItemStack>>)} takes
 *       the server's own {@code ItemStack}, so each Bukkit item is copied across with {@code
 *       CraftItemStack.asNMSCopy}: the standard bridge from the Bukkit item form to the server's.
 *   <li><b>Glow.</b> Glowing is the {@code glowing} bit of an entity's shared-flags byte (data item 0). The
 *       accessor and the bit index are both {@code protected static} on {@code Entity}, so they are read once at
 *       construction through {@link Reflect} (the same precedent as the nametag accessors), and the metadata
 *       packet sets that single byte.
 *   <li><b>Glow colour.</b> The client tints a glowing outline with the colour of the team the entity's name is
 *       on, so a colour is a {@code ClientboundSetPlayerTeamPacket.createAddOrModifyPacket} over a throwaway
 *       {@code PlayerTeam} carrying the colour and the NPC's profile name as its member: the approach
 *       FancyNpcs uses. Which enum a team's colour is depends on the line, so it is set through the compat
 *       seam rather than here.
 * </ul>
 */
public final class NmsNpcPackets implements NpcPackets {

    private final PacketSender sender;

    /** The {@code Byte} data item that holds an entity's shared flags (on-fire, sneaking, glowing, ...). */
    private final EntityDataAccessor<Byte> sharedFlagsAccessor;
    /** The shared-flags byte with only the glowing bit set, sent to switch the outline on. */
    private final byte glowingFlag;
    /** The shared-flags byte with only the on-fire bit set; combined with the others in {@link #sharedFlags}. */
    private final byte onFireFlag;
    /** The shared-flags byte with only the invisible bit set; combined with the others in {@link #sharedFlags}. */
    private final byte invisibleFlag;
    /** The {@code Boolean} silent data item on {@code Entity}; read once like the shared-flags accessor. */
    private final EntityDataAccessor<Boolean> silentAccessor;
    /** The {@code Pose} data item that holds an entity's body pose; read once, like the shared-flags accessor. */
    private final EntityDataAccessor<Pose> poseAccessor;
    /** The {@code Boolean} baby/adult data item on {@code AgeableMob} (animals, villagers, hoglin); read once. */
    private final EntityDataAccessor<Boolean> babyAccessor;
    /** The {@code Boolean} baby/adult data item on the zombie line ({@code Zombie}); a separate index. Read once. */
    private final EntityDataAccessor<Boolean> zombieBabyAccessor;
    /** The {@code Boolean} baby/adult data item on {@code Piglin}; a separate index again. Read once. */
    private final EntityDataAccessor<Boolean> piglinBabyAccessor;
    /** The {@code Boolean} baby/adult data item on {@code Zoglin}; a separate index again. Read once. */
    private final EntityDataAccessor<Boolean> zoglinBabyAccessor;
    /** The {@code Integer} slime-size data item on {@code Slime}; read once. */
    private final EntityDataAccessor<Integer> slimeSizeAccessor;
    /** The {@code Boolean} powered data item on {@code Creeper}; read once. */
    private final EntityDataAccessor<Boolean> chargedAccessor;
    /** The {@code VillagerData} data item on {@code Villager}; read once. */
    private final EntityDataAccessor<net.minecraft.world.entity.npc.villager.VillagerData> villagerDataAccessor;
    /** The {@code Integer} packed colour+markings variant data item on {@code Horse}; read once. */
    private final EntityDataAccessor<Integer> horseVariantAccessor;
    /** The {@code Integer} coat-variant data item on {@code Llama}; read once. */
    private final EntityDataAccessor<Integer> llamaVariantAccessor;
    /** The {@code Byte} wool data item on {@code Sheep} (low 4 bits colour, bit 0x10 sheared); read once. */
    private final EntityDataAccessor<Byte> sheepWoolAccessor;
    /** The {@code Integer} collar-colour data item on {@code Wolf} (a DyeColor id, 0 to 15); read once. */
    private final EntityDataAccessor<Integer> wolfCollarAccessor;
    /** The {@code Byte} shell-colour data item on {@code Shulker} (a DyeColor id, 0 to 15; 16 is default); read once. */
    private final EntityDataAccessor<Byte> shulkerColorAccessor;
    /** The {@code Byte} peek data item on {@code Shulker} (0 closed, 100 open); read once. */
    private final EntityDataAccessor<Byte> shulkerPeekAccessor;

    private final EntityDataAccessor<Direction> shulkerAttachFaceAccessor;
    /** The {@code Byte} main (visible) gene data item on {@code Panda} (0 to 6); read once. */
    private final EntityDataAccessor<Byte> pandaMainGeneAccessor;
    /** The {@code Byte} hidden gene data item on {@code Panda} (0 to 6); set alongside the main gene so it renders. */
    private final EntityDataAccessor<Byte> pandaHiddenGeneAccessor;
    /** The {@code Boolean} screaming-variant data item on {@code Goat}; read once. */
    private final EntityDataAccessor<Boolean> goatScreamingAccessor;
    /** The {@code Boolean} dancing data item on {@code Allay}; read once. */
    private final EntityDataAccessor<Boolean> allayDancingAccessor;
    /** The {@code Boolean} dancing data item on {@code Piglin}; read once. */
    private final EntityDataAccessor<Boolean> piglinDancingAccessor;
    /** The {@code Boolean} dash data item on {@code Camel}; read once. */
    private final EntityDataAccessor<Boolean> camelDashAccessor;
    /** The {@code Byte} flags data item on {@code Bee} and its nectar/roll/stung bit masks; read once. */
    private final EntityDataAccessor<Byte> beeFlagsAccessor;

    private final byte beeNectarFlag;
    private final byte beeRollFlag;
    private final byte beeStungFlag;
    /** The sheared bit ({@code 0x10}) of the sheep's wool byte, composed alongside the colour nibble. */
    private final byte sheepShearedFlag = 0x10;
    /** The {@code Byte} flags data item on {@code Vex} and its charging bit mask; read once. */
    private final EntityDataAccessor<Byte> vexFlagsAccessor;

    private final byte vexChargingFlag;
    /** The {@code Integer} packed type-variant data item on {@code TropicalFish}; read once. */
    private final EntityDataAccessor<Integer> tropicalFishVariantAccessor;
    /** The {@code Byte} client-flags data item on {@code ArmorStand} and its four bit masks; read once. */
    private final EntityDataAccessor<Byte> armorStandFlagsAccessor;

    private final byte armorStandSmallFlag;
    private final byte armorStandArmsFlag;
    private final byte armorStandNoBasePlateFlag;
    private final byte armorStandMarkerFlag;
    /** The six {@code Rotations} pose data items on {@code ArmorStand} (head/body/arms/legs); read once. */
    private final EntityDataAccessor<Rotations> armorStandHeadPoseAccessor;

    private final EntityDataAccessor<Rotations> armorStandBodyPoseAccessor;
    private final EntityDataAccessor<Rotations> armorStandLeftArmPoseAccessor;
    private final EntityDataAccessor<Rotations> armorStandRightArmPoseAccessor;
    private final EntityDataAccessor<Rotations> armorStandLeftLegPoseAccessor;
    private final EntityDataAccessor<Rotations> armorStandRightLegPoseAccessor;
    /** The {@code Float} width/height and {@code Boolean} response data items on {@code Interaction}; read once. */
    private final EntityDataAccessor<Float> interactionWidthAccessor;

    private final EntityDataAccessor<Float> interactionHeightAccessor;
    private final EntityDataAccessor<Boolean> interactionResponseAccessor;
    /** The {@code BlockState} data item on {@code Display.BlockDisplay}; read once. */
    private final EntityDataAccessor<net.minecraft.world.level.block.state.BlockState> blockDisplayStateAccessor;
    /** The {@code ItemStack} data item on {@code Display.ItemDisplay}; read once. */
    private final EntityDataAccessor<net.minecraft.world.item.ItemStack> itemDisplayItemAccessor;
    /** The {@code Component} text data item on {@code Display.TextDisplay}; read once. */
    private final EntityDataAccessor<net.minecraft.network.chat.Component> textDisplayTextAccessor;
    /** The {@code Vector3f} scale and {@code Byte} billboard data items on {@code Display} (all subtypes); read once. */
    private final EntityDataAccessor<Vector3fc> displayScaleAccessor;

    private final EntityDataAccessor<Byte> displayBillboardAccessor;
    /** The {@code Vector3f} translation-offset data item on {@code Display} (all subtypes); read once. */
    private final EntityDataAccessor<Vector3fc> displayTranslationAccessor;
    /** The {@code Integer} ARGB background data item on {@code Display.TextDisplay}; read once. */
    private final EntityDataAccessor<Integer> textDisplayBackgroundAccessor;
    /** The {@code Integer} line-width (text-wrap pixels) data item on {@code Display.TextDisplay}; read once. */
    private final EntityDataAccessor<Integer> textDisplayLineWidthAccessor;
    /** The {@code Integer} variant data item on {@code Parrot}; read once. */
    private final EntityDataAccessor<Integer> parrotVariantAccessor;
    /** The {@code Integer} variant data item on {@code Axolotl}; read once. */
    private final EntityDataAccessor<Integer> axolotlVariantAccessor;
    /** The {@code Integer} type data item on {@code Fox}; read once. */
    private final EntityDataAccessor<Integer> foxTypeAccessor;
    /** The {@code Integer} type data item on {@code Rabbit}; read once. */
    private final EntityDataAccessor<Integer> rabbitTypeAccessor;
    /** The {@code Holder<CatVariant>} variant data item on {@code Cat}; read once. The variant is resolved per call. */
    private final EntityDataAccessor<Holder<CatVariant>> catVariantAccessor;
    /** The {@code Holder<FrogVariant>} variant data item on {@code Frog}; read once. The variant is resolved per call. */
    private final EntityDataAccessor<Holder<FrogVariant>> frogVariantAccessor;
    /** The {@code Holder<…Variant>} coat-variant data items on {@code Wolf}/{@code Chicken}/{@code Cow}/{@code Pig}. */
    private final EntityDataAccessor<Holder<WolfVariant>> wolfVariantAccessor;

    private final EntityDataAccessor<Holder<ChickenVariant>> chickenVariantAccessor;
    private final EntityDataAccessor<Holder<CowVariant>> cowVariantAccessor;
    private final EntityDataAccessor<Holder<PigVariant>> pigVariantAccessor;
    /** The {@code Boolean} play-dead data item on {@code Axolotl} and celebrating on {@code Raider}; read once. */
    private final EntityDataAccessor<Boolean> axolotlPlayingDeadAccessor;

    private final EntityDataAccessor<Boolean> raiderCelebratingAccessor;

    /** {@code TamableAnimal.DATA_FLAGS_ID} and its sitting bit (the unnamed {@code 0x01} of {@code isInSittingPose}). */
    private final EntityDataAccessor<Byte> tameableFlagsAccessor;

    private final byte tameableSittingFlag;
    /** {@code Fox.DATA_FLAGS_ID} and its three pose bit masks (sitting/sleeping/crouching). */
    private final EntityDataAccessor<Byte> foxFlagsAccessor;

    private final byte foxSittingFlag;
    private final byte foxSleepingFlag;
    private final byte foxCrouchingFlag;
    /** {@code Panda.EAT_COUNTER}; a positive counter drives the sitting-and-eating pose. */
    private final EntityDataAccessor<Integer> pandaEatCounterAccessor;
    /** {@code Sniffer.DATA_STATE} and {@code Armadillo.ARMADILLO_STATE}; each carries the mob's animation enum. */
    private final EntityDataAccessor<Sniffer.State> snifferStateAccessor;

    private final EntityDataAccessor<Armadillo.ArmadilloState> armadilloStateAccessor;
    /** {@code LivingEntity.DATA_LIVING_ENTITY_FLAGS} and the item-use/off-hand bit masks composed into it. */
    private final EntityDataAccessor<Byte> livingEntityFlagsAccessor;

    private final byte spinAttackFlag;
    private final EntityDataAccessor<Optional<BlockPos>> sleepingPosAccessor;

    private final byte usingItemFlag;
    private final byte offHandFlag;
    /** {@code Entity.DATA_TICKS_FROZEN}; a value past the freeze threshold renders the shivering overlay. */
    private final EntityDataAccessor<Integer> frozenTicksAccessor;
    /** The registry's own player type, read once rather than named as a constant (see VanillaEntityTypes). */
    private final EntityType<?> playerType;

    public NmsNpcPackets(PacketSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.playerType = VanillaEntityTypes.of("player");
        // Read the shared-flags and pose accessors and the glowing bit index once here, off every hot path.
        // FLAG_GLOWING is the bit position (6); the wire value is the byte with that one bit set.
        this.sharedFlagsAccessor = Reflect.accessor(Entity.class, "DATA_SHARED_FLAGS_ID");
        int glowingBit = Reflect.accessor(Entity.class, "FLAG_GLOWING");
        this.glowingFlag = (byte) (1 << glowingBit);
        // The on-fire and invisible bits live in the same shared-flags byte, so sharedFlags composes all three
        // rather than letting glow/on-fire/invisible each overwrite the whole byte. Their bit indices are read off
        // Entity here, once, exactly like the glowing bit.
        int onFireBit = Reflect.accessor(Entity.class, "FLAG_ONFIRE");
        this.onFireFlag = (byte) (1 << onFireBit);
        int invisibleBit = Reflect.accessor(Entity.class, "FLAG_INVISIBLE");
        this.invisibleFlag = (byte) (1 << invisibleBit);
        this.silentAccessor = Reflect.accessor(Entity.class, "DATA_SILENT");
        this.poseAccessor = Reflect.accessor(Entity.class, "DATA_POSE");
        // The type-specific appearance accessors, each read once here off every hot path, exactly like glow/pose.
        // Each lives on the entity class that owns the property, so it is only ever sent to that type (the plugin
        // gates which property reaches which entity); a wrong index would land on an unrelated field otherwise.
        this.babyAccessor = Reflect.accessor(AgeableMob.class, "DATA_BABY_ID");
        // The zombie line, piglins, and zoglins extend Monster (not AgeableMob), so each carries its own baby
        // boolean at its own data index; sending the AgeableMob baby to them would land on an unrelated field.
        this.zombieBabyAccessor = Reflect.accessor(Zombie.class, "DATA_BABY_ID");
        this.piglinBabyAccessor = Reflect.accessor(Piglin.class, "DATA_BABY_ID");
        this.zoglinBabyAccessor = Reflect.accessor(Zoglin.class, "DATA_BABY_ID");
        this.slimeSizeAccessor = Reflect.accessorByClass(
                "ID_SIZE",
                "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob",
                "net.minecraft.world.entity.monster.Slime");
        this.chargedAccessor = Reflect.accessor(Creeper.class, "DATA_IS_POWERED");
        this.villagerDataAccessor = Reflect.accessor(Villager.class, "DATA_VILLAGER_DATA");
        // The animal-variant accessors, each on the class that owns it, read once like the rest. The horse packs
        // colour and markings into one integer; the sheep keeps colour in the low nibble of its wool byte. Each is
        // only ever sent to its own type (the plugin gates which property reaches which entity).
        this.horseVariantAccessor = Reflect.accessor(Horse.class, "DATA_ID_TYPE_VARIANT");
        this.llamaVariantAccessor = Reflect.accessor(Llama.class, "DATA_VARIANT_ID");
        this.sheepWoolAccessor = Reflect.accessor(Sheep.class, "DATA_WOOL_ID");
        this.wolfCollarAccessor = Reflect.accessor(Wolf.class, "DATA_COLLAR_COLOR");
        this.shulkerColorAccessor = Reflect.accessor(Shulker.class, "DATA_COLOR_ID");
        this.shulkerPeekAccessor = Reflect.accessor(Shulker.class, "DATA_PEEK_ID");
        this.shulkerAttachFaceAccessor = Reflect.accessor(Shulker.class, "DATA_ATTACH_FACE_ID");
        this.pandaMainGeneAccessor = Reflect.accessor(Panda.class, "MAIN_GENE_ID");
        this.pandaHiddenGeneAccessor = Reflect.accessor(Panda.class, "HIDDEN_GENE_ID");
        this.goatScreamingAccessor = Reflect.accessor(Goat.class, "DATA_IS_SCREAMING_GOAT");
        this.allayDancingAccessor = Reflect.accessor(Allay.class, "DATA_DANCING");
        this.piglinDancingAccessor = Reflect.accessor(Piglin.class, "DATA_IS_DANCING");
        this.camelDashAccessor = Reflect.accessor(Camel.class, "DASH");
        // Bee/Vex pack their state into a flags byte. Unlike Entity's FLAG_GLOWING (a bit position the shared-flags
        // path shifts), the bee/vex FLAG_* constants are bit MASKS the mob applies directly, so they need no shift.
        this.beeFlagsAccessor = Reflect.accessor(Bee.class, "DATA_FLAGS_ID");
        int beeNectarMask = Reflect.accessor(Bee.class, "FLAG_HAS_NECTAR");
        this.beeNectarFlag = (byte) beeNectarMask;
        int beeRollMask = Reflect.accessor(Bee.class, "FLAG_ROLL");
        this.beeRollFlag = (byte) beeRollMask;
        int beeStungMask = Reflect.accessor(Bee.class, "FLAG_HAS_STUNG");
        this.beeStungFlag = (byte) beeStungMask;
        this.vexFlagsAccessor = Reflect.accessor(Vex.class, "DATA_FLAGS_ID");
        int vexChargingMask = Reflect.accessor(Vex.class, "FLAG_IS_CHARGING");
        this.vexChargingFlag = (byte) vexChargingMask;
        this.tropicalFishVariantAccessor = Reflect.accessor(TropicalFish.class, "DATA_ID_TYPE_VARIANT");
        // ArmorStand's four client-flag constants are bit masks the server applies directly (setBit does b|flag),
        // like the bee/vex flags; read each mask once and compose them into the one client-flags byte.
        this.armorStandFlagsAccessor = Reflect.accessor(ArmorStand.class, "DATA_CLIENT_FLAGS");
        int small = Reflect.accessor(ArmorStand.class, "CLIENT_FLAG_SMALL");
        this.armorStandSmallFlag = (byte) small;
        int arms = Reflect.accessor(ArmorStand.class, "CLIENT_FLAG_SHOW_ARMS");
        this.armorStandArmsFlag = (byte) arms;
        int noBasePlate = Reflect.accessor(ArmorStand.class, "CLIENT_FLAG_NO_BASEPLATE");
        this.armorStandNoBasePlateFlag = (byte) noBasePlate;
        int marker = Reflect.accessor(ArmorStand.class, "CLIENT_FLAG_MARKER");
        this.armorStandMarkerFlag = (byte) marker;
        this.armorStandHeadPoseAccessor = Reflect.accessor(ArmorStand.class, "DATA_HEAD_POSE");
        this.armorStandBodyPoseAccessor = Reflect.accessor(ArmorStand.class, "DATA_BODY_POSE");
        this.armorStandLeftArmPoseAccessor = Reflect.accessor(ArmorStand.class, "DATA_LEFT_ARM_POSE");
        this.armorStandRightArmPoseAccessor = Reflect.accessor(ArmorStand.class, "DATA_RIGHT_ARM_POSE");
        this.armorStandLeftLegPoseAccessor = Reflect.accessor(ArmorStand.class, "DATA_LEFT_LEG_POSE");
        this.armorStandRightLegPoseAccessor = Reflect.accessor(ArmorStand.class, "DATA_RIGHT_LEG_POSE");
        this.interactionWidthAccessor = Reflect.accessor(Interaction.class, "DATA_WIDTH_ID");
        this.interactionHeightAccessor = Reflect.accessor(Interaction.class, "DATA_HEIGHT_ID");
        this.interactionResponseAccessor = Reflect.accessor(Interaction.class, "DATA_RESPONSE_ID");
        this.blockDisplayStateAccessor = Reflect.accessor(Display.BlockDisplay.class, "DATA_BLOCK_STATE_ID");
        this.itemDisplayItemAccessor = Reflect.accessor(Display.ItemDisplay.class, "DATA_ITEM_STACK_ID");
        this.textDisplayTextAccessor = Reflect.accessor(Display.TextDisplay.class, "DATA_TEXT_ID");
        this.displayScaleAccessor = Reflect.accessor(Display.class, "DATA_SCALE_ID");
        this.displayBillboardAccessor = Reflect.accessor(Display.class, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
        this.displayTranslationAccessor = Reflect.accessor(Display.class, "DATA_TRANSLATION_ID");
        this.textDisplayBackgroundAccessor = Reflect.accessor(Display.TextDisplay.class, "DATA_BACKGROUND_COLOR_ID");
        this.textDisplayLineWidthAccessor = Reflect.accessor(Display.TextDisplay.class, "DATA_LINE_WIDTH_ID");
        this.parrotVariantAccessor = Reflect.accessor(Parrot.class, "DATA_VARIANT_ID");
        this.axolotlVariantAccessor = Reflect.accessor(Axolotl.class, "DATA_VARIANT");
        this.foxTypeAccessor = Reflect.accessor(Fox.class, "DATA_TYPE_ID");
        this.rabbitTypeAccessor = Reflect.accessor(Rabbit.class, "DATA_TYPE_ID");
        // The cat and frog variants are dynamic-registry values: their metadata field carries a Holder, not an int.
        // The accessor is read once here like the rest, but the Holder itself is resolved off the live server's
        // registry per call (see catVariant/frogVariant), since the variant set only exists on a running server.
        this.catVariantAccessor = Reflect.accessor(Cat.class, "DATA_VARIANT_ID");
        this.frogVariantAccessor = Reflect.accessor(Frog.class, "DATA_VARIANT_ID");
        this.wolfVariantAccessor = Reflect.accessor(Wolf.class, "DATA_VARIANT_ID");
        this.chickenVariantAccessor = Reflect.accessor(Chicken.class, "DATA_VARIANT_ID");
        this.cowVariantAccessor = Reflect.accessor(Cow.class, "DATA_VARIANT_ID");
        this.pigVariantAccessor = Reflect.accessor(Pig.class, "DATA_VARIANT_ID");
        this.axolotlPlayingDeadAccessor = Reflect.accessor(Axolotl.class, "DATA_PLAYING_DEAD");
        this.raiderCelebratingAccessor = Reflect.accessor(Raider.class, "IS_CELEBRATING");
        this.tameableFlagsAccessor = Reflect.accessor(TamableAnimal.class, "DATA_FLAGS_ID");
        this.tameableSittingFlag = 1;
        // Fox's FLAG_* constants are bit masks the mob applies directly (setFlag does flags | mask), like bee/vex.
        this.foxFlagsAccessor = Reflect.accessor(Fox.class, "DATA_FLAGS_ID");
        int foxSitting = Reflect.accessor(Fox.class, "FLAG_SITTING");
        this.foxSittingFlag = (byte) foxSitting;
        int foxSleeping = Reflect.accessor(Fox.class, "FLAG_SLEEPING");
        this.foxSleepingFlag = (byte) foxSleeping;
        int foxCrouching = Reflect.accessor(Fox.class, "FLAG_CROUCHING");
        this.foxCrouchingFlag = (byte) foxCrouching;
        this.pandaEatCounterAccessor = Reflect.accessor(Panda.class, "EAT_COUNTER");
        this.snifferStateAccessor = Reflect.accessor(Sniffer.class, "DATA_STATE");
        this.armadilloStateAccessor = Reflect.accessor(Armadillo.class, "ARMADILLO_STATE");
        // LivingEntity's item-use flags are bit masks the mob applies directly (setLivingEntityFlag does b|mask).
        this.livingEntityFlagsAccessor = Reflect.accessor(LivingEntity.class, "DATA_LIVING_ENTITY_FLAGS");
        int usingItem = Reflect.accessor(LivingEntity.class, "LIVING_ENTITY_FLAG_IS_USING");
        this.usingItemFlag = (byte) usingItem;
        int offHand = Reflect.accessor(LivingEntity.class, "LIVING_ENTITY_FLAG_OFF_HAND");
        this.offHandFlag = (byte) offHand;
        int spinAttack = Reflect.accessor(LivingEntity.class, "LIVING_ENTITY_FLAG_SPIN_ATTACK");
        this.spinAttackFlag = (byte) spinAttack;
        // The sleeping position lives on LivingEntity, not on Player: any living thing can be laid in a bed.
        this.sleepingPosAccessor = Reflect.accessor(LivingEntity.class, "SLEEPING_POS_ID");
        this.frozenTicksAccessor = Reflect.accessor(Entity.class, "DATA_TICKS_FROZEN");
    }

    @Override
    public int allocateEntityId() {
        return EntityIds.next();
    }

    @Override
    public Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin) {
        return tabAdd(profileId, name, skin, true);
    }

    @Override
    public Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin, boolean listed) {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(name, "name");
        // The ADD seats the entry's initial listed state; UPDATE_LISTED is bundled so the flag is read either way.
        // listed=false keeps the entry a known client entity (the body and skin render) but draws no tab-list row.
        EnumSet<Action> actions = EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_LISTED);
        Entry entry = new Entry(
                profileId, profileFor(profileId, name, skin), listed, 0, GameType.DEFAULT_MODE, null, true, 0, null);
        return new ClientboundPlayerInfoUpdatePacket(actions, List.of(entry));
    }

    @Override
    public Object tabRemove(UUID profileId) {
        Objects.requireNonNull(profileId, "profileId");
        return new ClientboundPlayerInfoRemovePacket(List.of(profileId));
    }

    @Override
    public Object spawnPlayer(int entityId, UUID profileId, double x, double y, double z, float yaw, float pitch) {
        Objects.requireNonNull(profileId, "profileId");
        // The spawn UUID is the profile id so the client links the skin; the head yaw matches the body yaw so the
        // NPC faces one way on spawn. The player type is the specialisation of the generic build below.
        return addEntity(entityId, profileId, playerType, x, y, z, yaw, pitch);
    }

    @Override
    public Object spawnEntity(
            int entityId, UUID entityUuid, String entityTypeKey, double x, double y, double z, float yaw, float pitch) {
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(entityTypeKey, "entityTypeKey");
        // A mob has no player-info entry, so entityUuid is just the opaque spawn UUID. Resolve the server entity
        // type from the key off the entity-type registry; an unresolved key is a guard failure (the plugin
        // validates first).
        return addEntity(entityId, entityUuid, VanillaEntityTypes.of(entityTypeKey), x, y, z, yaw, pitch);
    }

    /**
     * The generic {@code ClientboundAddEntityPacket} builder both spawn methods share. The public constructor
     * packs the raw degree rotations itself (via {@code Mth.packDegrees}), so it takes raw floats; data is 0 and
     * the delta movement zero for a static NPC, and the head yaw matches the body yaw so the NPC faces one way on
     * spawn.
     */
    private static ClientboundAddEntityPacket addEntity(
            int entityId, UUID spawnUuid, EntityType<?> type, double x, double y, double z, float yaw, float pitch) {
        return new ClientboundAddEntityPacket(entityId, spawnUuid, x, y, z, pitch, yaw, type, 0, Vec3.ZERO, yaw);
    }

    @Override
    public Object headLook(int entityId, float yaw) {
        byte headYaw = ByteAngle.of(yaw);
        return Codecs.decodeVia(ClientboundRotateHeadPacket.STREAM_CODEC, buffer -> {
            buffer.writeVarInt(entityId);
            buffer.writeByte(headYaw);
        });
    }

    @Override
    public Object bodyLook(int entityId, float yaw, float pitch) {
        return new ClientboundMoveEntityPacket.Rot(entityId, ByteAngle.of(yaw), ByteAngle.of(pitch), true);
    }

    @Override
    public Object teleport(int entityId, double x, double y, double z, float yaw, float pitch) {
        PositionMoveRotation change = new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, yaw, pitch);
        return new ClientboundTeleportEntityPacket(entityId, change, Set.<Relative>of(), true);
    }

    @Override
    public Object remove(int entityId) {
        return new ClientboundRemoveEntitiesPacket(entityId);
    }

    @Override
    public Object equipment(int entityId, Map<EquipmentSlot, ItemStack> items) {
        Objects.requireNonNull(items, "items");
        List<Pair<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack>> slots =
                new ArrayList<>(items.size());
        for (Map.Entry<EquipmentSlot, ItemStack> entry : items.entrySet()) {
            slots.add(Pair.of(toNmsSlot(entry.getKey()), CraftItemStack.asNMSCopy(entry.getValue())));
        }
        return new ClientboundSetEquipmentPacket(entityId, slots);
    }

    @Override
    public Object glow(int entityId, boolean glowing) {
        // A fresh NPC carries no other shared flags, so the byte is the glowing bit alone (or zero to clear it).
        byte flags = glowing ? glowingFlag : 0;
        SynchedEntityData.DataValue<Byte> value = SynchedEntityData.DataValue.create(sharedFlagsAccessor, flags);
        return new ClientboundSetEntityDataPacket(entityId, List.of(value));
    }

    @Override
    public Object sharedFlags(int entityId, boolean onFire, boolean glowing, boolean invisible) {
        // Compose the three bits into one shared-flags byte so none overwrites the others' state. An unset flag
        // leaves its bit clear; an all-false call clears the byte to zero (no glow, not on fire, visible).
        byte flags = 0;
        if (onFire) {
            flags |= onFireFlag;
        }
        if (glowing) {
            flags |= glowingFlag;
        }
        if (invisible) {
            flags |= invisibleFlag;
        }
        SynchedEntityData.DataValue<Byte> value = SynchedEntityData.DataValue.create(sharedFlagsAccessor, flags);
        return new ClientboundSetEntityDataPacket(entityId, List.of(value));
    }

    @Override
    public Object silent(int entityId, boolean silent) {
        // DATA_SILENT lives on Entity, so it applies to any NPC type; ship it the same way glow ships its byte.
        return dataPacket(entityId, SynchedEntityData.DataValue.create(silentAccessor, silent));
    }

    @Override
    public Object pose(int entityId, NpcPose pose) {
        Objects.requireNonNull(pose, "pose");
        // Resolve the server pose from the constant's server name (GLIDING -> FALL_FLYING, the rest by name) and
        // ship it the same way glow ships its byte: DataValue.create derives the POSE serializer from the accessor.
        Pose nmsPose = Pose.valueOf(pose.serverName());
        SynchedEntityData.DataValue<Pose> value = SynchedEntityData.DataValue.create(poseAccessor, nmsPose);
        return new ClientboundSetEntityDataPacket(entityId, List.of(value));
    }

    @Override
    public Object scale(int entityId, double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be finite and positive, was " + scale);
        }
        // The packet's public constructor reads each instance's base value and modifiers into a snapshot, so an
        // instance carrying just the scale base value (no modifiers) is the simplest way past the private snapshot
        // constructor without a registry-buffer round-trip.
        AttributeInstance instance = new AttributeInstance(Attributes.SCALE, ignored -> {});
        instance.setBaseValue(scale);
        return new ClientboundUpdateAttributesPacket(entityId, List.of(instance));
    }

    @Override
    public Object baby(int entityId, boolean baby) {
        // Ships the AgeableMob baby boolean exactly the way glow ships its byte; the accessor carries the boolean
        // serializer, so DataValue.create needs nothing more than the accessor and the value.
        return dataPacket(entityId, SynchedEntityData.DataValue.create(babyAccessor, baby));
    }

    @Override
    public Object zombieBaby(int entityId, boolean baby) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(zombieBabyAccessor, baby));
    }

    @Override
    public Object piglinBaby(int entityId, boolean baby) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(piglinBabyAccessor, baby));
    }

    @Override
    public Object zoglinBaby(int entityId, boolean baby) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(zoglinBabyAccessor, baby));
    }

    @Override
    public Object villagerData(int entityId, String type, String profession, int level) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(profession, "profession");
        // Resolve the type/profession by name off their defaulted registries: an unknown name returns the registry
        // default rather than nothing, and pack them into the VillagerData record the metadata field carries.
        net.minecraft.world.entity.npc.villager.VillagerData data =
                new net.minecraft.world.entity.npc.villager.VillagerData(
                        holder(BuiltInRegistries.VILLAGER_TYPE, type),
                        holder(BuiltInRegistries.VILLAGER_PROFESSION, profession),
                        level);
        return dataPacket(entityId, SynchedEntityData.DataValue.create(villagerDataAccessor, data));
    }

    @Override
    public Object slimeSize(int entityId, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("slime size must be at least 1, was " + size);
        }
        return dataPacket(entityId, SynchedEntityData.DataValue.create(slimeSizeAccessor, size));
    }

    @Override
    public Object charged(int entityId, boolean charged) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(chargedAccessor, charged));
    }

    @Override
    public Object horseVariant(int entityId, int color, int markings) {
        // Colour and markings pack into the one integer the field carries, the same packing the server uses; the
        // pure HorseVariant helper does the masking so the bit layout stays testable off the NMS path.
        int packed = HorseVariant.pack(color, markings);
        return dataPacket(entityId, SynchedEntityData.DataValue.create(horseVariantAccessor, packed));
    }

    @Override
    public Object llamaVariant(int entityId, int variant) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(llamaVariantAccessor, variant));
    }

    @Override
    public Object sheepWool(int entityId, int color, boolean sheared) {
        // The wool byte holds the colour in its low four bits and the sheared flag in bit 0x10; compose both.
        byte wool = (byte) (color & 0x0F);
        if (sheared) {
            wool |= sheepShearedFlag;
        }
        return dataPacket(entityId, SynchedEntityData.DataValue.create(sheepWoolAccessor, wool));
    }

    @Override
    public Object wolfCollar(int entityId, int color) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(wolfCollarAccessor, color));
    }

    @Override
    public Object shulkerColor(int entityId, int color) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(shulkerColorAccessor, (byte) color));
    }

    @Override
    public Object shulkerPeek(int entityId, int peek) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(shulkerPeekAccessor, (byte) peek));
    }

    @Override
    public Object shulkerAttachFace(int entityId, String face) {
        Objects.requireNonNull(face, "face");
        Direction direction = Direction.byName(face.toLowerCase(Locale.ROOT));
        if (direction == null) {
            throw new IllegalArgumentException("no such direction: " + face);
        }
        return dataPacket(entityId, SynchedEntityData.DataValue.create(shulkerAttachFaceAccessor, direction));
    }

    @Override
    public Object pandaGene(int entityId, int gene) {
        // Set the hidden gene to the main gene too: the client renders a recessive gene (brown/weak) only when both
        // match, so a single main-gene write would otherwise fall back to the normal phenotype for those two.
        byte id = (byte) gene;
        return dataPacket(
                entityId,
                List.of(
                        SynchedEntityData.DataValue.create(pandaMainGeneAccessor, id),
                        SynchedEntityData.DataValue.create(pandaHiddenGeneAccessor, id)));
    }

    @Override
    public Object goatScreaming(int entityId, boolean screaming) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(goatScreamingAccessor, screaming));
    }

    @Override
    public Object allayDancing(int entityId, boolean dancing) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(allayDancingAccessor, dancing));
    }

    @Override
    public Object piglinDancing(int entityId, boolean dancing) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(piglinDancingAccessor, dancing));
    }

    @Override
    public Object camelDash(int entityId, boolean dashing) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(camelDashAccessor, dashing));
    }

    @Override
    public Object beeFlags(int entityId, boolean nectar, boolean rolling, boolean stung) {
        byte flags = 0;
        if (nectar) {
            flags |= beeNectarFlag;
        }
        if (rolling) {
            flags |= beeRollFlag;
        }
        if (stung) {
            flags |= beeStungFlag;
        }
        return dataPacket(entityId, SynchedEntityData.DataValue.create(beeFlagsAccessor, flags));
    }

    @Override
    public Object vexCharging(int entityId, boolean charging) {
        byte flags = charging ? vexChargingFlag : 0;
        return dataPacket(entityId, SynchedEntityData.DataValue.create(vexFlagsAccessor, flags));
    }

    @Override
    public Object armorStandFlags(int entityId, boolean small, boolean showArms, boolean noBasePlate, boolean marker) {
        byte flags = 0;
        if (small) {
            flags |= armorStandSmallFlag;
        }
        if (showArms) {
            flags |= armorStandArmsFlag;
        }
        if (noBasePlate) {
            flags |= armorStandNoBasePlateFlag;
        }
        if (marker) {
            flags |= armorStandMarkerFlag;
        }
        return dataPacket(entityId, SynchedEntityData.DataValue.create(armorStandFlagsAccessor, flags));
    }

    @Override
    public Object armorStandPose(int entityId, ArmorStandPart part, float x, float y, float z) {
        EntityDataAccessor<Rotations> accessor = poseAccessor(part);
        return dataPacket(entityId, SynchedEntityData.DataValue.create(accessor, new Rotations(x, y, z)));
    }

    @Override
    public Object blockDisplayState(int entityId, BlockData blockData) {
        return dataPacket(
                entityId,
                SynchedEntityData.DataValue.create(blockDisplayStateAccessor, ((CraftBlockData) blockData).getState()));
    }

    @Override
    public Object itemDisplayItem(int entityId, ItemStack item) {
        return dataPacket(
                entityId, SynchedEntityData.DataValue.create(itemDisplayItemAccessor, CraftItemStack.asNMSCopy(item)));
    }

    @Override
    public Object textDisplayText(int entityId, net.kyori.adventure.text.Component text) {
        return dataPacket(
                entityId, SynchedEntityData.DataValue.create(textDisplayTextAccessor, Components.asVanilla(text)));
    }

    @Override
    public Object displayScale(int entityId, float scale) {
        return dataPacket(
                entityId, SynchedEntityData.DataValue.create(displayScaleAccessor, new Vector3f(scale, scale, scale)));
    }

    @Override
    public Object displayScale(int entityId, float x, float y, float z) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(displayScaleAccessor, new Vector3f(x, y, z)));
    }

    @Override
    public Object displayTranslation(int entityId, float x, float y, float z) {
        return dataPacket(
                entityId, SynchedEntityData.DataValue.create(displayTranslationAccessor, new Vector3f(x, y, z)));
    }

    @Override
    public Object displayBillboard(int entityId, byte constraint) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(displayBillboardAccessor, constraint));
    }

    @Override
    public Object textDisplayBackground(int entityId, int argb) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(textDisplayBackgroundAccessor, argb));
    }

    @Override
    public Object textDisplayLineWidth(int entityId, int lineWidth) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(textDisplayLineWidthAccessor, lineWidth));
    }

    @Override
    public Object interactionSize(int entityId, float width, float height) {
        return dataPacket(
                entityId,
                List.of(
                        SynchedEntityData.DataValue.create(interactionWidthAccessor, width),
                        SynchedEntityData.DataValue.create(interactionHeightAccessor, height),
                        SynchedEntityData.DataValue.create(interactionResponseAccessor, true)));
    }

    private EntityDataAccessor<Rotations> poseAccessor(ArmorStandPart part) {
        return switch (part) {
            case HEAD -> armorStandHeadPoseAccessor;
            case BODY -> armorStandBodyPoseAccessor;
            case LEFT_ARM -> armorStandLeftArmPoseAccessor;
            case RIGHT_ARM -> armorStandRightArmPoseAccessor;
            case LEFT_LEG -> armorStandLeftLegPoseAccessor;
            case RIGHT_LEG -> armorStandRightLegPoseAccessor;
        };
    }

    @Override
    public Object tropicalFishVariant(int entityId, int variantIndex) {
        // Pick the predefined variant from the server's own COMMON_VARIANTS and use its packed id, so the bit
        // layout is the server's, not a copied formula. Clamp defensively; the plugin validates the index first.
        java.util.List<TropicalFish.Variant> variants = TropicalFish.COMMON_VARIANTS;
        int index = Math.clamp(variantIndex, 0, variants.size() - 1);
        int packed = variants.get(index).getPackedId();
        return dataPacket(entityId, SynchedEntityData.DataValue.create(tropicalFishVariantAccessor, packed));
    }

    @Override
    public Object parrotVariant(int entityId, int variant) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(parrotVariantAccessor, variant));
    }

    @Override
    public Object axolotlVariant(int entityId, int variant) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(axolotlVariantAccessor, variant));
    }

    @Override
    public Object foxType(int entityId, int type) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(foxTypeAccessor, type));
    }

    @Override
    public Object rabbitType(int entityId, int type) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(rabbitTypeAccessor, type));
    }

    @Override
    public @Nullable Object catVariant(int entityId, String name) {
        Objects.requireNonNull(name, "name");
        Holder<CatVariant> variant = dynamicHolder(Registries.CAT_VARIANT, name);
        return variant == null
                ? null
                : dataPacket(entityId, SynchedEntityData.DataValue.create(catVariantAccessor, variant));
    }

    @Override
    public @Nullable Object frogVariant(int entityId, String name) {
        Objects.requireNonNull(name, "name");
        Holder<FrogVariant> variant = dynamicHolder(Registries.FROG_VARIANT, name);
        return variant == null
                ? null
                : dataPacket(entityId, SynchedEntityData.DataValue.create(frogVariantAccessor, variant));
    }

    @Override
    public Object axolotlPlayingDead(int entityId, boolean playingDead) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(axolotlPlayingDeadAccessor, playingDead));
    }

    @Override
    public Object raiderCelebrating(int entityId, boolean celebrating) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(raiderCelebratingAccessor, celebrating));
    }

    @Override
    public Object tameableSitting(int entityId, boolean sitting) {
        byte flags = sitting ? tameableSittingFlag : 0;
        return dataPacket(entityId, SynchedEntityData.DataValue.create(tameableFlagsAccessor, flags));
    }

    @Override
    public Object foxFlags(int entityId, boolean sitting, boolean sleeping, boolean crouching) {
        byte flags = 0;
        if (sitting) {
            flags |= foxSittingFlag;
        }
        if (sleeping) {
            flags |= foxSleepingFlag;
        }
        if (crouching) {
            flags |= foxCrouchingFlag;
        }
        return dataPacket(entityId, SynchedEntityData.DataValue.create(foxFlagsAccessor, flags));
    }

    @Override
    public Object pandaEating(int entityId, boolean eating) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(pandaEatCounterAccessor, eating ? 1 : 0));
    }

    @Override
    public @Nullable Object snifferState(int entityId, String state) {
        Objects.requireNonNull(state, "state");
        Sniffer.State resolved = enumByName(Sniffer.State.values(), state);
        if (resolved == null) {
            return null;
        }
        return dataPacket(entityId, SynchedEntityData.DataValue.create(snifferStateAccessor, resolved));
    }

    @Override
    public @Nullable Object armadilloState(int entityId, String state) {
        Objects.requireNonNull(state, "state");
        Armadillo.ArmadilloState resolved = enumByName(Armadillo.ArmadilloState.values(), state);
        if (resolved == null) {
            return null;
        }
        return dataPacket(entityId, SynchedEntityData.DataValue.create(armadilloStateAccessor, resolved));
    }

    @Override
    public Object usingItem(int entityId, boolean using, boolean offHand) {
        byte flags = 0;
        if (using) {
            flags |= usingItemFlag;
        }
        if (offHand) {
            flags |= offHandFlag;
        }
        return dataPacket(entityId, SynchedEntityData.DataValue.create(livingEntityFlagsAccessor, flags));
    }

    @Override
    public Object spinAttack(int entityId, boolean spinning) {
        byte flags = spinning ? spinAttackFlag : 0;
        return dataPacket(entityId, SynchedEntityData.DataValue.create(livingEntityFlagsAccessor, flags));
    }

    @Override
    public Object sleepingPosition(int entityId, int x, int y, int z) {
        SynchedEntityData.DataValue<Optional<BlockPos>> value =
                SynchedEntityData.DataValue.create(sleepingPosAccessor, Optional.of(new BlockPos(x, y, z)));
        return dataPacket(entityId, value);
    }

    @Override
    public Object noSleepingPosition(int entityId) {
        SynchedEntityData.DataValue<Optional<BlockPos>> value =
                SynchedEntityData.DataValue.create(sleepingPosAccessor, Optional.empty());
        return dataPacket(entityId, value);
    }

    @Override
    public Object frozenTicks(int entityId, int ticks) {
        return dataPacket(entityId, SynchedEntityData.DataValue.create(frozenTicksAccessor, ticks));
    }

    /** Resolve {@code name} (case-insensitive) against an enum's constants, or {@code null} if none match. */
    private static <E extends Enum<E>> @Nullable E enumByName(E[] values, String name) {
        for (E value : values) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    @Override
    public @Nullable Object wolfVariant(int entityId, String name) {
        Objects.requireNonNull(name, "name");
        Holder<WolfVariant> variant = dynamicHolder(Registries.WOLF_VARIANT, name);
        return variant == null
                ? null
                : dataPacket(entityId, SynchedEntityData.DataValue.create(wolfVariantAccessor, variant));
    }

    @Override
    public @Nullable Object chickenVariant(int entityId, String name) {
        Objects.requireNonNull(name, "name");
        Holder<ChickenVariant> variant = dynamicHolder(Registries.CHICKEN_VARIANT, name);
        return variant == null
                ? null
                : dataPacket(entityId, SynchedEntityData.DataValue.create(chickenVariantAccessor, variant));
    }

    @Override
    public @Nullable Object cowVariant(int entityId, String name) {
        Objects.requireNonNull(name, "name");
        Holder<CowVariant> variant = dynamicHolder(Registries.COW_VARIANT, name);
        return variant == null
                ? null
                : dataPacket(entityId, SynchedEntityData.DataValue.create(cowVariantAccessor, variant));
    }

    @Override
    public @Nullable Object pigVariant(int entityId, String name) {
        Objects.requireNonNull(name, "name");
        Holder<PigVariant> variant = dynamicHolder(Registries.PIG_VARIANT, name);
        return variant == null
                ? null
                : dataPacket(entityId, SynchedEntityData.DataValue.create(pigVariantAccessor, variant));
    }

    /**
     * Resolve {@code name} (plain or namespaced) to a {@link Holder} off a dynamic registry reached through the
     * live server's {@link MinecraftServer#registryAccess() registry access}: the cat- and frog-variant registries
     * are data-driven and live only on a running server, not in {@code BuiltInRegistries}, so they cannot be reached
     * at construction the way the villager registries are. Returns {@code null} (rather than throwing) when the
     * server is not yet up, the name is unparseable, or the registry has no such entry; the caller drops a
     * {@code null} packet so the render thread never throws on a typo or a too-early call.
     */
    private static <T> @Nullable Holder<T> dynamicHolder(ResourceKey<Registry<T>> registryKey, String name) {
        try {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) {
                return null;
            }
            Identifier id = name.indexOf(Identifier.NAMESPACE_SEPARATOR) < 0
                    ? Identifier.withDefaultNamespace(name)
                    : Identifier.tryParse(name);
            if (id == null) {
                return null;
            }
            Registry<T> registry = server.registryAccess().lookupOrThrow(registryKey);
            return registry.get(id).map(reference -> (Holder<T>) reference).orElse(null);
        } catch (RuntimeException registryUnavailable) {
            // The server registries can be missing or mid-build very early in boot; a variant is cosmetic, so a
            // failed lookup falls back to the entity's default variant rather than failing the spawn.
            return null;
        }
    }

    /** Wrap one already-built {@link SynchedEntityData.DataValue} into a single-field metadata packet. */
    private static ClientboundSetEntityDataPacket dataPacket(int entityId, SynchedEntityData.DataValue<?> value) {
        return new ClientboundSetEntityDataPacket(entityId, List.of(value));
    }

    /** Wrap several already-built {@link SynchedEntityData.DataValue}s into one metadata packet (e.g. a panda's pair). */
    private static ClientboundSetEntityDataPacket dataPacket(
            int entityId, List<SynchedEntityData.DataValue<?>> values) {
        return new ClientboundSetEntityDataPacket(entityId, values);
    }

    /**
     * Resolve {@code name} (plain or namespaced) to a {@link Holder} off the defaulted {@code registry}, falling
     * back to the registry default when the name is unparseable or unknown: the registry's own default holder, so
     * a typo renders the default appearance rather than failing the spawn.
     */
    private static <T> Holder<T> holder(net.minecraft.core.DefaultedRegistry<T> registry, String name) {
        Identifier id = name.indexOf(Identifier.NAMESPACE_SEPARATOR) < 0
                ? Identifier.withDefaultNamespace(name)
                : Identifier.tryParse(name);
        if (id != null) {
            Optional<? extends Holder<T>> found = registry.get(id);
            if (found.isPresent()) {
                return found.get();
            }
        }
        return registry.get(registry.getDefaultKey()).orElseThrow();
    }

    @Override
    public Object glowColor(String teamName, String memberName, @Nullable NamedColor color) {
        Objects.requireNonNull(teamName, "teamName");
        Objects.requireNonNull(memberName, "memberName");
        // A throwaway scoreboard is fine: the packet copies the team's parameters and member list off it, and a
        // PlayerTeam needs a Scoreboard only to construct.
        PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName);
        if (color != null) {
            ServerInternals.applyTeamColor(team, color.name());
        }
        team.getPlayers().add(memberName);
        return ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true);
    }

    @Override
    public Object glowColorRemove(String teamName) {
        Objects.requireNonNull(teamName, "teamName");
        // The remove packet carries only the team name off the team object, so a throwaway one suffices; the
        // client drops the named team if it has it and ignores the packet otherwise.
        PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName);
        return ClientboundSetPlayerTeamPacket.createRemovePacket(team);
    }

    @Override
    public Object team(
            String teamName, String memberName, @Nullable NamedColor color, boolean collidable, boolean hideNametag) {
        Objects.requireNonNull(teamName, "teamName");
        Objects.requireNonNull(memberName, "memberName");
        // The client honours the team's collision rule, outline colour and nametag visibility for an entity whose
        // name is on the team, the same team mechanism glowColor tints through; ALWAYS collides, NEVER passes
        // through, and NEVER visibility stops the nametag being drawn at all. All three ride one packet because an
        // entity is on only one team. A throwaway scoreboard is fine: the packet copies the team's parameters and
        // member list off it.
        PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName);
        team.setCollisionRule(collidable ? Team.CollisionRule.ALWAYS : Team.CollisionRule.NEVER);
        team.setNameTagVisibility(hideNametag ? Team.Visibility.NEVER : Team.Visibility.ALWAYS);
        if (color != null) {
            ServerInternals.applyTeamColor(team, color.name());
        }
        team.getPlayers().add(memberName);
        return ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true);
    }

    @Override
    public Object bundle(List<Object> packets) {
        return Bundles.of(packets);
    }

    @Override
    public void send(Player viewer, Object packet) {
        sender.send(viewer, packet);
    }

    /** Map a port-side equipment slot onto the matching server slot: the single place the two names meet. */
    private static net.minecraft.world.entity.EquipmentSlot toNmsSlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case OFFHAND -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case HEAD -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case CHEST -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case LEGS -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case FEET -> net.minecraft.world.entity.EquipmentSlot.FEET;
        };
    }

    /**
     * The {@code GameProfile} for the NPC, always carrying a {@code textures} property: the NPC's own skin when it
     * has one, otherwise the default Steve texture ({@link TabSkin#DEFAULT}). The property is never omitted because
     * a fake player spawned through {@code ClientboundAddEntityPacket} on 1.20.2+ links its body to this profile,
     * and clients drop a profile-less fake player, so a skinless NPC would otherwise never render.
     */
    private static GameProfile profileFor(UUID profileId, String name, @Nullable TabSkin skin) {
        TabSkin textures = TabSkin.orDefault(skin);
        return GameProfiles.withTextures(profileId, name, textures.textureValue(), textures.signature());
    }
}
