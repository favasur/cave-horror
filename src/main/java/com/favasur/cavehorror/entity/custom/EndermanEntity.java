package com.favasur.cavehorror.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.Animation.LoopType;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;

public class EndermanEntity extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    public int rRollResult = 4;
    public boolean forcedStalk = false;
    public boolean isAggro;
    private boolean returnShort = false;
    private boolean inTwoBlockSpace = false;
    public boolean spottedByPlayer = false;
    public boolean squeezeCrawling = false;
    public boolean isFleeing;
    public boolean startedMovingChase = false;
    private Vec3 oldPos;
    private final RawAnimation OLD_RUN = RawAnimation.begin().then("animation.enderman.run", LoopType.LOOP);
    private final RawAnimation IDLE = RawAnimation.begin().then("animation.enderman.idle", LoopType.LOOP);
    private final RawAnimation CHASE = RawAnimation.begin().then("animation.enderman.new_run", LoopType.LOOP);
    private final RawAnimation CHASE_IDLE = RawAnimation.begin().then("animation.enderman.run_idle", LoopType.LOOP);
    private final RawAnimation CROUCH_RUN = RawAnimation.begin().then("animation.enderman.crouch_run_new", LoopType.LOOP);
    private final RawAnimation CROUCH_IDLE = RawAnimation.begin().then("animation.enderman.crouch_idle", LoopType.LOOP);
    private final RawAnimation CALM_RUN = RawAnimation.begin().then("animation.enderman.calm_move", LoopType.LOOP);
    private final RawAnimation CALM_STILL = RawAnimation.begin().then("animation.enderman.calm_idle", LoopType.LOOP);
    private final RawAnimation IS_SPOTTED = RawAnimation.begin().then("animation.enderman.spotted", LoopType.HOLD_ON_LAST_FRAME);
    private final RawAnimation CRAWL = RawAnimation.begin().then("animation.enderman.crawl", LoopType.HOLD_ON_LAST_FRAME);
    private final RawAnimation FLEE = RawAnimation.begin().then("animation.enderman.flee", LoopType.LOOP);
    private final RawAnimation STALK = RawAnimation.begin().then("animation.enderman.stalking", LoopType.LOOP);
    private final RawAnimation STALK_IDLE = RawAnimation.begin().then("animation.enderman.stalking_idle", LoopType.HOLD_ON_LAST_FRAME);
    private final RawAnimation CLIMB = RawAnimation.begin().then("animation.enderman.climb", LoopType.LOOP);
    private RawAnimation currentAnim;

    // Structurally important blocks the entity steals to weaken player defenses
    // Only doors, windows (glass panes), fence gates, and iron bars — things that
    // let mobs get in. Creates the impression the entity is preparing for future raids.
    public static final Set<Block> STEALABLE_BLOCKS = Set.of(
            // Doors — player's main defense against mobs
            Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR,
            Blocks.JUNGLE_DOOR, Blocks.ACACIA_DOOR, Blocks.DARK_OAK_DOOR,
            Blocks.MANGROVE_DOOR, Blocks.CHERRY_DOOR, Blocks.BAMBOO_DOOR,
            Blocks.CRIMSON_DOOR, Blocks.WARPED_DOOR, Blocks.IRON_DOOR,
            // Glass panes — windows the player looks through
            Blocks.GLASS_PANE, Blocks.WHITE_STAINED_GLASS_PANE,
            Blocks.ORANGE_STAINED_GLASS_PANE, Blocks.MAGENTA_STAINED_GLASS_PANE,
            Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Blocks.YELLOW_STAINED_GLASS_PANE,
            Blocks.LIME_STAINED_GLASS_PANE, Blocks.PINK_STAINED_GLASS_PANE,
            Blocks.GRAY_STAINED_GLASS_PANE, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE,
            Blocks.CYAN_STAINED_GLASS_PANE, Blocks.PURPLE_STAINED_GLASS_PANE,
            Blocks.BLUE_STAINED_GLASS_PANE, Blocks.BROWN_STAINED_GLASS_PANE,
            Blocks.GREEN_STAINED_GLASS_PANE, Blocks.RED_STAINED_GLASS_PANE,
            Blocks.BLACK_STAINED_GLASS_PANE,
            // Fence gates — another common entry point
            Blocks.OAK_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE, Blocks.BIRCH_FENCE_GATE,
            Blocks.JUNGLE_FENCE_GATE, Blocks.ACACIA_FENCE_GATE, Blocks.DARK_OAK_FENCE_GATE,
            Blocks.MANGROVE_FENCE_GATE, Blocks.CHERRY_FENCE_GATE, Blocks.BAMBOO_FENCE_GATE,
            Blocks.CRIMSON_FENCE_GATE, Blocks.WARPED_FENCE_GATE,
            // Iron bars — prison-like barriers
            Blocks.IRON_BARS,
            // Regular glass blocks too
            Blocks.GLASS, Blocks.WHITE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS,
            Blocks.YELLOW_STAINED_GLASS, Blocks.LIME_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS,
            Blocks.LIGHT_GRAY_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS,
            Blocks.PURPLE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS,
            Blocks.RED_STAINED_GLASS, Blocks.BLACK_STAINED_GLASS
    );

    public static final EntityDataAccessor<Boolean> FLEEING_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CROUCHING_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> AGGRO_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SQUEEZING_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SPOTTED_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> STALKING_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> EYES_VISIBLE = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);

    private float twoBlockSpaceCooldown;
    private float twoBlockSpaceTimer = 0.0F;
    private int chaseSoundClockReset = 80;
    private int climbSoundClockReset = 10;
    private int climbSoundClock = 0;
    private int chaseSoundClock = 0;
    private boolean alreadyPlayedSpottedSound = false;
    private boolean startedPlayingChaseSound = false;
    private boolean alreadyPlayedDeathSound = false;
    private float currentMaxUpStep = 1.0F;

    // Mold patch spreading after wall emergence
    private final List<BlockPos> moldSpreadPositions = new ArrayList<>();
    private int moldSpreadTimer = 0;
    private int moldSpreadIndex = 0;
    private int emergeSqueezeTimer = 0;
    private boolean emergeLowCeiling = false;

    // Record 13 playback when player stares at the eyes
    private int stareTicksForRecord = 0;
    private int record13Timer = -1; // -1 = not playing, otherwise ticks since start

    @Override
    public float maxUpStep() {
        return this.currentMaxUpStep;
    }

    public EndermanEntity(EntityType<? extends EndermanEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.currentMaxUpStep = 1.0F;
        this.refreshDimensions();
        this.twoBlockSpaceCooldown = 5.0F;
        this.oldPos = this.position();
        ItemStack enchantedBoots = new ItemStack(Items.LEATHER_BOOTS);
        this.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.DEPTH_STRIDER).ifPresent(h -> enchantedBoots.enchant(h, 3));
        this.setItemSlot(EquipmentSlot.FEET, enchantedBoots);
        this.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 999999, 100, true, false));
        this.setPersistenceRequired();
        this.noPhysics = true;
        this.forcedStalk = true;
    }

    public static AttributeSupplier setAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 65.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_KNOCKBACK, 0.6)
                .add(Attributes.FOLLOW_RANGE, 100.0)
                .add(Attributes.ARMOR, 2.0)
                .build();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLEEING_ACCESSOR, false);
        builder.define(CROUCHING_ACCESSOR, false);
        builder.define(AGGRO_ACCESSOR, false);
        builder.define(SQUEEZING_ACCESSOR, false);
        builder.define(SPOTTED_ACCESSOR, false);
        builder.define(STALKING_ACCESSOR, false);
        builder.define(EYES_VISIBLE, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new EndermanBreakInvisGoal(this));
        this.goalSelector.addGoal(2, new EndermanChaseGoal(this, this, 0.85F, true, 20.0F));
        this.goalSelector.addGoal(2, new EndermanStalkGoal(this, 0.5, 15.0F));
        this.targetSelector.addGoal(1, new EndermanTargetTooCloseGoal(this, 12.0F));
        this.targetSelector.addGoal(2, new EndermanTargetSeesMeGoal(this));
    }

    @Override
    public boolean canRide(Entity pVehicle) {
        return false;
    }

    public Vec3 generatePos(Entity player) {
        Vec3 playerPos = player.position();
        Random rand = new Random();

        // Calculate direction behind the player (opposite of where they're looking)
        Vec3 lookVec = player.getLookAngle();
        Vec3 behindDir = new Vec3(-lookVec.x, 0, -lookVec.z).normalize();
        // Add some random angle variation so it's not always directly behind
        double angleVariation = (rand.nextDouble() - 0.5) * 0.8;
        double cos = Math.cos(angleVariation);
        double sin = Math.sin(angleVariation);
        double dirX = behindDir.x * cos - behindDir.z * sin;
        double dirZ = behindDir.x * sin + behindDir.z * cos;

        for (int attempt = 0; attempt < 30; attempt++) {
            // Spawn 20-40 blocks behind the player (with variation)
            double distance = 20.0 + rand.nextDouble() * 20.0;
            double posX = playerPos.x + dirX * distance;
            double posZ = playerPos.z + dirZ * distance;
            // Start search from player's Y level, capped underground
            double posY = Math.min(playerPos.y + 5.0, 45.0);

            for (int runFor = 100; runFor >= 0; posY--) {
                BlockPos blockPosition = new BlockPos((int) posX, (int) posY, (int) posZ);
                BlockPos blockPosition2 = new BlockPos((int) posX, (int) (posY + 1.0), (int) posZ);
                BlockPos blockPosition3 = new BlockPos((int) posX, (int) (posY + 2.0), (int) posZ);
                BlockPos blockPosition4 = new BlockPos((int) posX, (int) (posY - 1.0), (int) posZ);
                runFor--;
                if (!this.level().getBlockState(blockPosition).isSolid()
                        && !this.level().getBlockState(blockPosition2).isSolid()
                        && !this.level().getBlockState(blockPosition3).isSolid()
                        && this.level().getBlockState(blockPosition4).isSolid()
                        && posY < 40.0                     // Must be underground
                        && !this.level().canSeeSky(blockPosition)) { // No sky access
                    return new Vec3(posX, posY, posZ);
                }
            }
        }

        // Fallback: spawn directly behind the player underground
        double fallbackX = playerPos.x + behindDir.x * 15.0;
        double fallbackZ = playerPos.z + behindDir.z * 15.0;
        return new Vec3(fallbackX, 15.0, fallbackZ);
    }

    @Override
    public void tick() {
        MutableBlockPos blockPos = new MutableBlockPos((int) this.position().x, (int) (this.position().y + 2.0), (int) this.position().z);
        BlockState blockstate = this.level().getBlockState(blockPos);
        boolean flag = blockstate.isSolid();
        if (flag) {
            this.twoBlockSpaceTimer = this.twoBlockSpaceCooldown;
            this.inTwoBlockSpace = true;
        } else {
            this.twoBlockSpaceTimer--;
            if (this.twoBlockSpaceTimer <= 0.0F) {
                this.inTwoBlockSpace = false;
            }
        }

        if (this.isAggro || this.isFleeing) {
            this.spottedByPlayer = false;
            this.entityData.set(SPOTTED_ACCESSOR, false);
        }

        super.tick();
        this.entityData.set(CROUCHING_ACCESSOR, this.inTwoBlockSpace);

        // Spawn black enderman-style dust particles around the entity
        if (this.level().isClientSide()) {
            Random particleRand = new Random();
            for (int i = 0; i < 2; i++) {
                this.level().addParticle(
                        new DustParticleOptions(new Vector3f(0.05F, 0.05F, 0.05F), 0.6F),
                        this.getX() + (particleRand.nextDouble() - 0.5) * (double) this.getBbWidth(),
                        this.getY() + particleRand.nextDouble() * (double) this.getBbHeight(),
                        this.getZ() + (particleRand.nextDouble() - 0.5) * (double) this.getBbWidth(),
                        (particleRand.nextDouble() - 0.5) * 0.5,
                        -particleRand.nextDouble() * 0.5,
                        (particleRand.nextDouble() - 0.5) * 0.5);
            }
        }

        // Extinguish torches bound to entity position — only when player is deep enough (30+ blocks below surface)
        // Creates the illusion of torches being snuffed out behind the player without them noticing
        if ((this.isInvisible() || this.entityData.get(STALKING_ACCESSOR)) && !this.level().isClientSide()
                && this.tickCount % 10 == 0 && this.getTarget() != null) {  // Check every ~0.5 seconds
            LivingEntity extTarget = this.getTarget();
            BlockPos targetPos = extTarget.blockPosition();
            int surfaceY = this.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, targetPos.getX(), targetPos.getZ());
            if (targetPos.getY() <= surfaceY - 30) {
                Random rand = new Random();
                int extinguishRadius = 20 + rand.nextInt(21); // 20-40 blocks
                BlockPos entityPos = this.blockPosition();
                for (int dx = -extinguishRadius; dx <= extinguishRadius; dx++) {
                    for (int dz = -extinguishRadius; dz <= extinguishRadius; dz++) {
                        if (dx * dx + dz * dz > extinguishRadius * extinguishRadius) continue;
                        BlockPos torchPos = entityPos.offset(dx, 0, dz);
                        BlockState state = this.level().getBlockState(torchPos);
                        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)
                                || state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)) {
                            this.level().destroyBlock(torchPos, false, this);
                        }
                    }
                }
            }
        }

        // Always face the target player — creates the feeling of being watched
        if (this.getTarget() != null) {
            this.getLookControl().setLookAt(this.getTarget(), 30.0F, 30.0F);
        }

        // Eyes visibility: hidden while invisible until cave sound plays or player is deep enough
        // Eyes ONLY reveal when it's pitch dark — creates the scary effect of glowing eyes in the void
        if (this.isInvisible() && this.getTarget() != null && !this.entityData.get(EYES_VISIBLE)) {
            // Only reveal in complete darkness (no light sources nearby)
            LivingEntity target = this.getTarget();
            BlockPos targetPos = target.blockPosition();
            if (this.level().getMaxLocalRawBrightness(targetPos) == 0) {
                // Check if the target player is 30+ blocks below the surface
                int surfaceY = this.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, targetPos.getX(), targetPos.getZ());
                if (targetPos.getY() <= surfaceY - 30) {
                    this.entityData.set(EYES_VISIBLE, true);
                }
            }
        }

        // Record 13 — play when player stares at the white eyes, loop while tense
        if (!this.level().isClientSide() && this.entityData.get(EYES_VISIBLE) && this.getTarget() != null) {
            LivingEntity target = this.getTarget();
            boolean isStaring = target.hasLineOfSight(this) && this.isPlayerLookingTowards();

            if (isStaring) {
                this.stareTicksForRecord = Math.min(this.stareTicksForRecord + 1, 400);
            } else if (this.tickCount % 5 == 0) {
                // Decay slower — tense state lingers ~5 seconds after player looks away
                this.stareTicksForRecord = Math.max(0, this.stareTicksForRecord - 1);
            }

            // Tense = player has stared at eyes within the last ~20 seconds
            boolean isTense = this.stareTicksForRecord > 0;

            if (isTense) {
                if (this.record13Timer < 0) {
                    // Start playing record 13
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.MUSIC_DISC_13, SoundSource.RECORDS, 4.0F, 1.0F);
                    this.record13Timer = 0;
                } else {
                    this.record13Timer++;
                    // Record 13 is ~178 seconds (3560 ticks) — replay if still tense
                    if (this.record13Timer >= 3500) {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                SoundEvents.MUSIC_DISC_13, SoundSource.RECORDS, 4.0F, 1.0F);
                        this.record13Timer = 0;
                    }
                }
            } else {
                // Not tense anymore — let current playthrough end naturally
                this.record13Timer = -1;
            }
        }

        // Handle mold particle spreading after wall emergence
        if (this.moldSpreadTimer > 0) {
            this.moldSpreadTimer--;
            if (this.moldSpreadTimer % 2 == 0 && this.moldSpreadIndex < this.moldSpreadPositions.size()) {
                BlockPos placePos = this.moldSpreadPositions.get(this.moldSpreadIndex++);
                Random moldRand = new Random();
                // Spawn black dust particles on the block surface (naturally fades over time)
                this.level().addParticle(
                        new DustParticleOptions(new Vector3f(0.02F, 0.02F, 0.02F), 1.8F),
                        placePos.getX() + 0.5 + (moldRand.nextDouble() - 0.5) * 0.8,
                        placePos.getY() + 0.5 + (moldRand.nextDouble() - 0.5) * 0.8,
                        placePos.getZ() + 0.5 + (moldRand.nextDouble() - 0.5) * 0.8,
                        0.0, -0.01, 0.0);
                // Spawn extra particles for a denser mold patch
                for (int p = 0; p < 4; p++) {
                    this.level().addParticle(
                            new DustParticleOptions(new Vector3f(0.05F, 0.05F, 0.05F), 1.2F),
                            placePos.getX() + moldRand.nextDouble(),
                            placePos.getY() + moldRand.nextDouble(),
                            placePos.getZ() + moldRand.nextDouble(),
                            (moldRand.nextDouble() - 0.5) * 0.02,
                            -moldRand.nextDouble() * 0.03,
                            (moldRand.nextDouble() - 0.5) * 0.02);
                }
            }
            if (this.moldSpreadTimer <= 0) {
                this.moldSpreadPositions.clear();
                this.moldSpreadIndex = 0;
            }
        }

        // Handle emergence squeeze animation cleanup
        if (this.emergeSqueezeTimer > 0) {
            this.emergeSqueezeTimer--;
            if (this.emergeSqueezeTimer <= 5) {
                // Smoothly transition out of squeeze
                this.entityData.set(SQUEEZING_ACCESSOR, false);
                this.entityData.set(CROUCHING_ACCESSOR, this.emergeLowCeiling && this.inTwoBlockSpace);
            }
        }

        // Ground-snap for noPhysics: prevent falling through the world
        // Searches both upward (when inside blocks after falling through) and downward (when airborne)
        if (this.noPhysics) {
            BlockPos feetPos = this.blockPosition();
            BlockState feetState = this.level().getBlockState(feetPos);

            if (feetState.isSolid()) {
                // Inside a solid block — search upward to reach the surface
                for (int dy = 0; dy < 10; dy++) {
                    BlockPos checkPos = feetPos.offset(0, dy, 0);
                    if (!this.level().getBlockState(checkPos).isSolid()) {
                        BlockPos groundPos = checkPos.below();
                        double desiredY = groundPos.getY() + 1.0;
                        this.setPos(this.getX(), desiredY, this.getZ());
                        break;
                    }
                }
            } else {
                // In air — search downward for ground (extended range for safety)
                for (int dy = 0; dy > -30; dy--) {
                    BlockPos checkPos = feetPos.offset(0, dy, 0);
                    if (this.level().getBlockState(checkPos).isSolid()) {
                        double desiredY = checkPos.getY() + 1.0;
                        if (Math.abs(this.getY() - desiredY) > 0.2) {
                            this.setPos(this.getX(), desiredY, this.getZ());
                        }
                        break;
                    }
                }
            }
        }

        if (this.entityData.get(SPOTTED_ACCESSOR)) {
            this.playSpottedSound();
        }
    }

    public boolean isMoving() {
        Vec3 velocity = this.getDeltaMovement();
        float avgVelocity = (float) (Math.abs(velocity.x) + Math.abs(velocity.z)) / 2.0F;
        return avgVelocity > 0.03F;
    }

    public void rRoll() {
        Random rand = new Random();
        this.forcedStalk = false;
        this.rRollResult = rand.nextInt(4);
    }

    public Path createShortPath(LivingEntity pathTarget) {
        this.returnShort = true;
        this.refreshDimensions();
        this.currentMaxUpStep = 100.0F;
        Path shortPath = this.getNavigation().createPath(pathTarget, 0);
        this.currentMaxUpStep = 1.0F;
        this.returnShort = false;
        this.refreshDimensions();
        return shortPath;
    }



    // Spider-like wall climbing - override onClimbable to return true when aggro and touching a wall
    @Override
    public boolean onClimbable() {
        return this.isAggro && this.horizontalCollision;
    }

    private PlayState predicate(AnimationState tAnimationState) {
        if (this.entityData.get(AGGRO_ACCESSOR)) {
            if (this.onClimbable()) {
                return tAnimationState.setAndContinue(this.CLIMB);
            } else if (this.entityData.get(SQUEEZING_ACCESSOR)) {
                return tAnimationState.setAndContinue(this.CRAWL);
            } else if (this.entityData.get(CROUCHING_ACCESSOR)) {
                return tAnimationState.isMoving() ? tAnimationState.setAndContinue(this.CROUCH_RUN) : tAnimationState.setAndContinue(this.CROUCH_IDLE);
            } else {
                return tAnimationState.isMoving() ? tAnimationState.setAndContinue(this.CHASE) : tAnimationState.setAndContinue(this.CHASE_IDLE);
            }
        } else if (this.entityData.get(FLEEING_ACCESSOR)) {
            return tAnimationState.isMoving() ? tAnimationState.setAndContinue(this.FLEE) : tAnimationState.setAndContinue(this.CHASE_IDLE);
        } else if (this.entityData.get(STALKING_ACCESSOR)) {
            return tAnimationState.isMoving() ? tAnimationState.setAndContinue(this.STALK) : tAnimationState.setAndContinue(this.STALK_IDLE);
        } else if (this.entityData.get(SPOTTED_ACCESSOR)) {
            return tAnimationState.setAndContinue(this.IS_SPOTTED);
        } else {
            return tAnimationState.isMoving() ? tAnimationState.setAndContinue(this.CALM_RUN) : tAnimationState.setAndContinue(this.CALM_STILL);
        }
    }

    @Override
    public void registerControllers(ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(
                new AnimationController<>(this, "controller", 3, this::predicate)
                        .triggerableAnim("calm_run", this.CALM_RUN)
                        .triggerableAnim("calm_still", this.CALM_STILL)
                        .triggerableAnim("chase", this.CHASE)
                        .triggerableAnim("chase_idle", this.CHASE_IDLE)
                        .triggerableAnim("crouch_run", this.CROUCH_RUN)
                        .triggerableAnim("crouch_idle", this.CROUCH_IDLE)
                        .triggerableAnim("is_spotted", this.IS_SPOTTED)
                        .triggerableAnim("crawl", this.CRAWL)
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public void playChaseSound() {
        if (this.startedPlayingChaseSound || this.isMoving()) {
            if (this.chaseSoundClock <= 0) {
                Random rand = new Random();
                switch (rand.nextInt(2)) {
                    case 0 -> this.level().playSound(null, this, SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 3.0F, 1.0F);
                    case 1 -> this.level().playSound(null, this, SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 3.0F, 1.0F);
                }
                this.startedPlayingChaseSound = true;
                this.resetChaseSoundClock();
            }
            this.chaseSoundClock--;
        }
    }

    public void playClimbSound() {
        if (this.climbSoundClock <= 0) {
            // No teleport sound - silent climb
            this.resetClimbSoundClock();
        }
        this.climbSoundClock--;
    }

    public void playSpottedSound() {
        if (!this.alreadyPlayedSpottedSound) {
            this.level().playSound(null, this, SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 3.0F, 1.0F);
            this.alreadyPlayedSpottedSound = true;
        }
    }

    /**
     * Called by CaveNoise when a cave ambient sound plays — reveals the eyes.
     */
    public void setEyesVisible(boolean visible) {
        this.entityData.set(EYES_VISIBLE, visible);
    }

    /**
     * Check if the eyes should render (called from render layer on client).
     */
    public boolean areEyesVisible() {
        return this.entityData.get(EYES_VISIBLE);
    }

    public boolean inPlayerLineOfSight() {
        return this.getTarget() != null && this.getTarget().hasLineOfSight(this);
    }

    public boolean isPlayerLookingTowards() {
        if (this.getTarget() == null) {
            return false;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        float fov = (float) (Integer) minecraft.options.fov().get();
        float yFovMod = 0.65F;
        float fovMod = (35.0F / fov - 1.0F) * 0.4F + 1.0F;
        fov *= fovMod;

        Vec3 a = this.getTarget().position();
        Vec3 b = this.position();
        Vec2 dist = new Vec2((float) (b.x - a.x), (float) (b.z - a.z));
        dist = dist.normalized();
        double newAngle = Math.toDegrees(Math.atan2(dist.x, dist.y));
        float lookX = (float) this.getTarget().getViewVector(1.0F).x;
        float lookZ = (float) this.getTarget().getViewVector(1.0F).z;
        double newLookAngle = Math.toDegrees(Math.atan2(lookX, lookZ));
        double newNewAngle = this.loopAngle(newAngle - newLookAngle) + fov;
        newNewAngle = this.loopAngle(newNewAngle);
        boolean yawPlayerLookingTowards = newNewAngle > 0.0 && newNewAngle < fov * 2.0F;

        float yFov = fov * yFovMod;
        Vec2 yDist = new Vec2(
                (float) Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z)),
                (float) (b.y - a.y)
        );
        yDist = yDist.normalized();
        double yAngle = Math.toDegrees(Math.atan2(yDist.x, yDist.y));
        float lookY = (float) this.getTarget().getViewVector(1.0F).y;
        Vec2 lookDist = new Vec2((float) Math.sqrt(lookX * lookX + lookZ * lookZ), lookY);
        lookDist = lookDist.normalized();
        double yLookAngle = Math.toDegrees(Math.atan2(lookDist.x, lookDist.y));
        double newYAngle = this.loopAngle(yAngle - yLookAngle) + yFov;
        newYAngle = this.loopAngle(newYAngle);
        boolean pitchPlayerLookingTowards = newYAngle > 0.0 && newYAngle < yFov * 2.0F;

        boolean shouldOnlyUsePitch = !(yLookAngle < 180.0F - yFov) || !(yLookAngle > yFov);

        return (yawPlayerLookingTowards || shouldOnlyUsePitch) && pitchPlayerLookingTowards;
    }

    public double loopAngle(double angle) {
        if (angle > 360.0) {
            return angle - 360.0;
        } else {
            return angle < 0.0 ? angle + 360.0 : angle;
        }
    }

    public void playDisappearSound() {
        // No teleport sound on disappear
    }

    private void resetChaseSoundClock() {
        this.chaseSoundClock = this.chaseSoundClockReset;
    }

    private void resetClimbSoundClock() {
        this.climbSoundClock = this.climbSoundClockReset;
    }

    /**
     * Find a stealable block near the given center and break it with teleport effects.
     * Less aggressive: prefers blocks near the entity over blocks near the center point,
     * uses fewer attempts, and only steals within a moderate radius.
     * @return true if a block was stolen
     */
    public boolean stealBlockNear(BlockPos center, int radius) {
        Random rand = new Random();
        // Prioritize stealing doors first (they're the most important entry point),
        // then glass panes, then fence gates, then other structural blocks
        int maxAttempts = Math.max(12, radius * 3);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = center.getX() + rand.nextInt(radius * 2 + 1) - radius;
            int y = center.getY() + rand.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + rand.nextInt(radius * 2 + 1) - radius;
            BlockPos targetPos = new BlockPos(x, y, z);
            BlockState targetState = this.level().getBlockState(targetPos);
            Block targetBlock = targetState.getBlock();

            // Skip blocks that are too close to the player
            if (center.distSqr(targetPos) < 9.0) continue;

            // Skip air, fluids, torches, bedrock, obsidian
            if (targetState.isAir() || targetState.liquid()) continue;
            if (targetState.is(Blocks.BEDROCK) || targetState.is(Blocks.OBSIDIAN)) continue;
            if (targetState.is(Blocks.TORCH) || targetState.is(Blocks.WALL_TORCH)) continue;
            if (!STEALABLE_BLOCKS.contains(targetBlock)) continue;

            // Break the block silently with no item drops, and hold it in hand
            this.level().destroyBlock(targetPos, false, this);
            this.setItemSlot(EquipmentSlot.MAINHAND, targetBlock.asItem().getDefaultInstance());
            return true;
        }
        return false;
    }

    // ===== STRUCTURE BUILDING =====

    private enum StructureType {
        SAND_PILLAR,
        SAND_PYRAMID,
        BRICK_PYRAMID,
        MOSSY_DUNGEON
    }

    // Track built structure positions so the entity spawns more frequently near them
    public static final List<BlockPos> BUILT_STRUCTURES = new ArrayList<>();

    // Blocks that indicate the player is in a mineshaft — entity stalks closer
    public static final Set<Block> MINESHAFT_BLOCKS = Set.of(
            Blocks.OAK_PLANKS, Blocks.OAK_FENCE, Blocks.RAIL,
            Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBWEB
    );

    /**
     * Builds a random simple structure near the given position.
     * Structures include sand pillars, sand pyramids, brick pyramids,
     * and mossy cobblestone dungeons with tunnels.
     * @return true if a structure was built
     */
    public boolean buildStructure(BlockPos nearPos) {
        Random rand = new Random();
        StructureType type = StructureType.values()[rand.nextInt(StructureType.values().length)];
        int range = 25;

        if (type == StructureType.MOSSY_DUNGEON) {
            // Underground dungeons — find a cave floor
            return buildMossyDungeon(nearPos, rand, range);
        } else {
            // Surface structures
            return buildSurfaceStructure(nearPos, rand, range, type);
        }
    }

    /**
     * Builds a surface structure (sand pillar or pyramid) near the given position.
     */
    private boolean buildSurfaceStructure(BlockPos nearPos, Random rand, int range, StructureType type) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = nearPos.getX() + rand.nextInt(range * 2 + 1) - range;
            int z = nearPos.getZ() + rand.nextInt(range * 2 + 1) - range;
            BlockPos surfacePos = findSurfaceAt(x, z);
            if (surfacePos == null) continue;

            BlockPos basePos = surfacePos.above();
            // Don't build too close to the player
            if (basePos.distSqr(nearPos) < 144.0) continue; // 12 blocks

            switch (type) {
                case SAND_PILLAR -> {
                    if (buildSandPillar(basePos, rand)) {
                        BUILT_STRUCTURES.add(basePos);
                        return true;
                    }
                }
                case SAND_PYRAMID -> {
                    if (buildSmallPyramid(basePos, rand, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE)) {
                        BUILT_STRUCTURES.add(basePos);
                        return true;
                    }
                }
                case BRICK_PYRAMID -> {
                    if (buildSmallPyramid(basePos, rand, Blocks.BRICKS, Blocks.BRICK_SLAB)) {
                        BUILT_STRUCTURES.add(basePos);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Builds a sand pillar: a 1-wide column of sand, 3-5 blocks tall.
     */
    private boolean buildSandPillar(BlockPos basePos, Random rand) {
        int height = 3 + rand.nextInt(3); // 3-5 blocks

        // Check all positions are clear
        for (int i = 0; i < height; i++) {
            if (!this.level().getBlockState(basePos.above(i)).isAir()) return false;
        }

        // Build the pillar
        for (int i = 0; i < height; i++) {
            this.level().setBlock(basePos.above(i), Blocks.SAND.defaultBlockState(), 3);
        }
        return true;
    }

    /**
     * Builds a small 3-tier pyramid (3x3 base, 2x2 middle, 1x1 top).
     */
    private boolean buildSmallPyramid(BlockPos basePos, Random rand, Block primaryBlock, Block secondaryBlock) {
        // Check all positions are clear
        BlockPos[] layer1 = {
                basePos, basePos.north(), basePos.south(), basePos.east(), basePos.west(),
                basePos.north().east(), basePos.north().west(), basePos.south().east(), basePos.south().west()
        };
        BlockPos[] layer2 = {
                basePos.above(), basePos.above().north(), basePos.above().south(),
                basePos.above().east(), basePos.above().west()
        };
        BlockPos[] layer3 = { basePos.above(2) };

        for (BlockPos pos : layer1) {
            if (!this.level().getBlockState(pos).isAir()) return false;
        }
        for (BlockPos pos : layer2) {
            if (!this.level().getBlockState(pos).isAir()) return false;
        }
        for (BlockPos pos : layer3) {
            if (!this.level().getBlockState(pos).isAir()) return false;
        }

        // Build layer 1 (3x3)
        for (BlockPos pos : layer1) {
            this.level().setBlock(pos, primaryBlock.defaultBlockState(), 3);
        }
        // Build layer 2 (2x2)
        for (BlockPos pos : layer2) {
            this.level().setBlock(pos, secondaryBlock.defaultBlockState(), 3);
        }
        // Build layer 3 (1x1 — single block on top)
        this.level().setBlock(layer3[0], secondaryBlock.defaultBlockState(), 3);

        return true;
    }

    /**
     * Builds a mossy cobblestone dungeon with redstone torches and a long tunnel.
     * This is an underground structure — finds a cave floor to build on.
     */
    private boolean buildMossyDungeon(BlockPos nearPos, Random rand, int range) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = nearPos.getX() + rand.nextInt(range * 2 + 1) - range;
            int z = nearPos.getZ() + rand.nextInt(range * 2 + 1) - range;
            BlockPos floorPos = findCaveFloor(x, z);
            if (floorPos == null) continue;

            BlockPos dungeonBase = floorPos.above();
            // Don't build too close to player
            if (dungeonBase.distSqr(nearPos) < 144.0) continue;
            // Keep dungeons deep
            if (dungeonBase.getY() > 30) continue;

            // Check for enough space (5x5 area, 3 blocks tall) — allow clearing stone
            boolean hasSpace = true;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy < 3; dy++) {
                        BlockPos checkPos = dungeonBase.offset(dx, dy, dz);
                        BlockState state = this.level().getBlockState(checkPos);
                        // Allow air, replaceable blocks, or solid blocks (we'll dig through)
                        if (state.isAir()) continue;
                        // Allow digging through stone, dirt, gravel, etc.
                        if (state.getBlock() == Blocks.STONE || state.getBlock() == Blocks.DIRT
                                || state.getBlock() == Blocks.GRAVEL || state.getBlock() == Blocks.ANDESITE
                                || state.getBlock() == Blocks.GRANITE || state.getBlock() == Blocks.DIORITE
                                || state.getBlock() == Blocks.TUFF || state.getBlock() == Blocks.DEEPSLATE) {
                            continue;
                        }
                        // For the floor and ceiling levels, allow any solid block
                        if (dy == 0 || dy == 2) continue;
                        hasSpace = false;
                        break;
                    }
                    if (!hasSpace) break;
                }
                if (!hasSpace) break;
            }
            if (!hasSpace) continue;

            // Build the dungeon!
            Block mossyCobble = Blocks.MOSSY_COBBLESTONE;
            Block redstoneTorch = Blocks.REDSTONE_TORCH;

            // Choose a random direction for the entrance tunnel
            Direction[] horizontals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            Direction tunnelDir = horizontals[rand.nextInt(4)];

            // Floor: 5x5 of mossy cobblestone at floor level
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos floor = dungeonBase.offset(dx, -1, dz);
                    this.level().setBlock(floor, mossyCobble.defaultBlockState(), 3);
                }
            }

            // Walls and ceiling: mossy cobblestone
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    // Skip interior walls
                    if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;

                    // Leave entrance open (1x2 opening in the middle of one wall)
                    boolean isEntrance = switch (tunnelDir) {
                        case NORTH -> dz == -2 && Math.abs(dx) <= 0;
                        case SOUTH -> dz == 2 && Math.abs(dx) <= 0;
                        case WEST -> dx == -2 && Math.abs(dz) <= 0;
                        case EAST -> dx == 2 && Math.abs(dz) <= 0;
                        default -> false;
                    };
                    if (isEntrance) continue;

                    for (int dy = 1; dy < 3; dy++) {
                        BlockPos wallPos = dungeonBase.offset(dx, dy, dz);
                        this.level().setBlock(wallPos, mossyCobble.defaultBlockState(), 3);
                    }
                }
            }

            // Ceiling: 5x5 mossy cobblestone at y+2 (except above entrance opening)
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    // Skip the entrance column for the ceiling too
                    boolean isEntrance = switch (tunnelDir) {
                        case NORTH -> dz == -2 && Math.abs(dx) <= 0;
                        case SOUTH -> dz == 2 && Math.abs(dx) <= 0;
                        case WEST -> dx == -2 && Math.abs(dz) <= 0;
                        case EAST -> dx == 2 && Math.abs(dz) <= 0;
                        default -> false;
                    };
                    if (isEntrance) continue;

                    BlockPos ceilPos = dungeonBase.offset(dx, 2, dz);
                    this.level().setBlock(ceilPos, mossyCobble.defaultBlockState(), 3);
                }
            }

            // Place redstone torches inside (on 2 opposite walls, 1 block up from floor)
            this.level().setBlock(dungeonBase.offset(-1, 1, 0), redstoneTorch.defaultBlockState(), 3);
            this.level().setBlock(dungeonBase.offset(1, 1, 0), redstoneTorch.defaultBlockState(), 3);

            // Dig tunnel: 2-tall, 1-wide, 6-10 blocks long in the entrance direction
            int tunnelLength = 6 + rand.nextInt(5);
            for (int t = 1; t <= tunnelLength; t++) {
                BlockPos tunnelBottom = dungeonBase.offset(
                        tunnelDir.getStepX() * (t + 1), -1, tunnelDir.getStepZ() * (t + 1));
                BlockPos tunnelTop = tunnelBottom.above();
                // Carve through everything
                this.level().setBlock(tunnelBottom, Blocks.AIR.defaultBlockState(), 3);
                this.level().setBlock(tunnelTop, Blocks.AIR.defaultBlockState(), 3);
            }

            // Line the tunnel walls with mossy cobblestone
            Direction perpDir = tunnelDir.getClockWise();
            for (int t = 1; t <= tunnelLength; t++) {
                BlockPos tunnelPos = dungeonBase.offset(
                        tunnelDir.getStepX() * (t + 1), -1, tunnelDir.getStepZ() * (t + 1));
                for (int side : new int[]{-1, 1}) {
                    BlockPos sidePos = tunnelPos.relative(perpDir, side);
                    if (this.level().getBlockState(sidePos).isAir()
                            || !this.level().getBlockState(sidePos).isSolid()) {
                        this.level().setBlock(sidePos, mossyCobble.defaultBlockState(), 3);
                    }
                    BlockPos sidePosTop = tunnelPos.relative(perpDir, side).above();
                    if (this.level().getBlockState(sidePosTop).isAir()
                            || !this.level().getBlockState(sidePosTop).isSolid()) {
                        this.level().setBlock(sidePosTop, mossyCobble.defaultBlockState(), 3);
                    }
                }
            }

            BUILT_STRUCTURES.add(dungeonBase);
            return true;
        }
        return false;
    }

    /**
     * Finds a surface block (solid ground with air above) by searching from the top down.
     */
    private BlockPos findSurfaceAt(int x, int z) {
        int startY = Math.min(this.level().getMaxBuildHeight(), 320);
        for (int y = startY; y > this.level().getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = this.level().getBlockState(pos);
            if (state.isSolid() && this.level().getBlockState(pos.above()).isAir()) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Finds a cave floor underground by searching from y=40 to y=5.
     * Returns a position where there's solid ground, at least 2 blocks of air above,
     * and no sky access (truly underground).
     */
    private BlockPos findCaveFloor(int x, int z) {
        for (int y = 40; y > 5; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = this.level().getBlockState(pos);
            BlockState above = this.level().getBlockState(pos.above());
            BlockState above2 = this.level().getBlockState(pos.above(2));
            // A cave floor: solid block below, air above (at least 2 blocks of headroom),
            // and no sky access
            if (state.isSolid() && above.isAir() && above2.isAir() && !this.level().canSeeSky(pos.above())) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Checks if any built structures exist near the given position.
     */
    public static boolean hasStructuresNearby(BlockPos pos, int range) {
        for (BlockPos structPos : BUILT_STRUCTURES) {
            if (structPos.distSqr(pos) < range * range) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the player is near a mineshaft or Enderman-built tunnel.
     * Scans for mineshaft blocks (planks, fences, rails) and built structures nearby.
     */
    public boolean isNearMineshaftOrTunnel(BlockPos playerPos, int scanRadius) {
        // First check: is this near a built structure?
        if (hasStructuresNearby(playerPos, scanRadius)) return true;

        // Second check: scan for mineshaft blocks around the player
        Level level = this.level();
        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                if (dx * dx + dz * dz > scanRadius * scanRadius) continue;
                // Check at player's Y level and one above/below
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos checkPos = playerPos.offset(dx, dy, dz);
                    Block block = level.getBlockState(checkPos).getBlock();
                    if (MINESHAFT_BLOCKS.contains(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Emerge from a wall with squeeze animation and mold spreading.
     * @param wallPos the wall block to emerge from
     * @param lowCeiling whether the ceiling is too low for full height
     * @param facingYaw the yaw rotation the entity should face when emerging
     */
    public void emergeFromWall(BlockPos wallPos, boolean lowCeiling, float facingYaw) {
        // 1. Teleport to the wall position
        this.teleportTo(wallPos.getX() + 0.5, wallPos.getY(), wallPos.getZ() + 0.5);
        this.setYRot(facingYaw);
        this.yRotO = facingYaw;

        // 2. Set emergence animation state
        this.emergeLowCeiling = lowCeiling;
        this.emergeSqueezeTimer = 20; // ~1 second of emergence animation
        this.entityData.set(SQUEEZING_ACCESSOR, true);
        if (lowCeiling) {
            this.entityData.set(CROUCHING_ACCESSOR, true);
        }

        // 3. Determine mold area (1x2 for low ceiling, 1x3 for high ceiling)
        this.moldSpreadPositions.clear();
        this.moldSpreadIndex = 0;
        int moldHeight = lowCeiling ? 2 : 3;

        // Find nearby wall blocks in a vertical column to place mold on
        for (int dy = 0; dy < moldHeight; dy++) {
            BlockPos checkPos = wallPos.offset(0, dy, 0);
            BlockState state = this.level().getBlockState(checkPos);
            if (state.isSolid() && !state.isAir()) {
                this.moldSpreadPositions.add(checkPos.immutable());
            }
            // Also check adjacent horizontal blocks (same Y) for more mold coverage
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos adjacentPos = checkPos.relative(dir);
                BlockState adjState = this.level().getBlockState(adjacentPos);
                if (adjState.isSolid() && !adjState.isAir() && !this.moldSpreadPositions.contains(adjacentPos)) {
                    // Don't place mold on every adjacent block — only some for organic look
                    if (this.getRandom().nextFloat() < 0.4F) {
                        this.moldSpreadPositions.add(adjacentPos.immutable());
                    }
                }
            }
        }

        this.moldSpreadTimer = this.moldSpreadPositions.size() * 2; // 2 ticks between particle bursts

        // 4. Play emergence sound
        this.level().playSound(null, this, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.HOSTILE, 1.0F, 0.5F);
    }

    /**
     * Spawn smoke/flame particles when a torch is destroyed by the entity.
     */
    public void spawnTorchDestroyParticles(BlockPos torchPos) {
        if (this.level().isClientSide()) {
            Random rand = new Random();
            for (int i = 0; i < 6; i++) {
                this.level().addParticle(
                        new DustParticleOptions(new Vector3f(1.0F, 0.5F, 0.0F), 1.0F),
                        torchPos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.8,
                        torchPos.getY() + 0.5 + (rand.nextDouble() - 0.5) * 0.8,
                        torchPos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 0.8,
                        (rand.nextDouble() - 0.5) * 0.2,
                        rand.nextDouble() * 0.3,
                        (rand.nextDouble() - 0.5) * 0.2
                );
            }
        }
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        // Ghost-like: completely immune to all damage
        return false;
    }

    @Override
    protected void playHurtSound(DamageSource pSource) {
        this.level().playSound(null, this, SoundEvents.ENDERMAN_HURT, SoundSource.HOSTILE, 2.0F, 1.0F);
    }

    @Override
    protected void dropEquipment() {
        this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        super.dropEquipment();
        if (!this.alreadyPlayedDeathSound) {
            this.level().playSound(null, this, SoundEvents.ENDERMAN_DEATH, SoundSource.HOSTILE, 2.0F, 1.0F);
            this.alreadyPlayedDeathSound = true;
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }
}
