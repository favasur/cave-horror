package com.favasur.cavehorror.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.minecraft.core.particles.DustParticleOptions;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.Animation.LoopType;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;

import java.util.Random;

public class EndermanEntity extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    public int rRollResult = 4;
    public boolean forcedStalk = false;
    public boolean isAggro;
    private final float chanceOfSpawningAsStalker = 0.6F;
    private boolean returnShort = false;
    private boolean inTwoBlockSpace = false;
    public boolean spottedByPlayer = false;
    public boolean squeezeCrawling = false;
    public boolean isFleeing;
    public boolean startedMovingChase = false;
    private Vec3 oldPos;
    private int ticksTillRemove;
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

    public static final EntityDataAccessor<Boolean> FLEEING_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CROUCHING_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> AGGRO_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SQUEEZING_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SPOTTED_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> STALKING_ACCESSOR = SynchedEntityData.defineId(EndermanEntity.class, EntityDataSerializers.BOOLEAN);

    private float twoBlockSpaceCooldown;
    private float twoBlockSpaceTimer = 0.0F;
    private int chaseSoundClockReset = 80;
    private int climbSoundClockReset = 10;
    private int climbSoundClock = 0;
    private int chaseSoundClock = 0;
    private boolean alreadyPlayedFleeSound = false;
    private boolean alreadyPlayedSpottedSound = false;
    private boolean startedPlayingChaseSound = false;
    private boolean alreadyPlayedDeathSound = false;
    private float currentMaxUpStep = 1.0F;

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
        this.ticksTillRemove = 6000;
        ItemStack enchantedBoots = new ItemStack(Items.LEATHER_BOOTS);
        this.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.DEPTH_STRIDER).ifPresent(h -> enchantedBoots.enchant(h, 3));
        this.setItemSlot(EquipmentSlot.FEET, enchantedBoots);
        this.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 999999, 100, true, false));
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
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new EndermanStareGoal(this, 100.0F));
        this.goalSelector.addGoal(2, new EndermanChaseGoal(this, this, 0.85F, true, 20.0F));
        this.goalSelector.addGoal(2, new EndermanFleeGoal(this, 20.0F, 1.0));
        this.goalSelector.addGoal(2, new EndermanStalkGoal(this, 0.5, 15.0F));
        this.goalSelector.addGoal(2, new EndermanStrollGoal(this, 0.7));
        this.goalSelector.addGoal(2, new EndermanBreakInvisGoal(this));
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
        double randX = rand.nextInt(70) - 35;
        double randZ = rand.nextInt(70) - 35;
        double posX = playerPos.x + randX;
        double posY = playerPos.y + 10.0;
        double posZ = playerPos.z + randZ;

        for (int runFor = 100; runFor >= 0; posY--) {
            BlockPos blockPosition = new BlockPos((int) posX, (int) posY, (int) posZ);
            BlockPos blockPosition2 = new BlockPos((int) posX, (int) (posY + 1.0), (int) posZ);
            BlockPos blockPosition3 = new BlockPos((int) posX, (int) (posY + 2.0), (int) posZ);
            BlockPos blockPosition4 = new BlockPos((int) posX, (int) (posY - 1.0), (int) posZ);
            runFor--;
            if (!this.level().getBlockState(blockPosition).isSolid()
                    && !this.level().getBlockState(blockPosition2).isSolid()
                    && !this.level().getBlockState(blockPosition3).isSolid()
                    && this.level().getBlockState(blockPosition4).isSolid()) {
                break;
            }
        }

        return new Vec3(posX, posY, posZ);
    }

    @Override
    public void tick() {
        this.ticksTillRemove--;

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

        if (this.ticksTillRemove <= 0 && (!this.isPlayerLookingTowards() || !this.inPlayerLineOfSight())) {
            this.discard();
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

    public boolean shouldSpawnAsStalker() {
        Random rand = new Random();
        float stalkerResult = rand.nextFloat();
        return stalkerResult < this.chanceOfSpawningAsStalker;
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

    @Override
    protected void playStepSound(BlockPos pPos, BlockState pState) {
        super.playStepSound(pPos, pState);
        this.level().playSound(null, this, SoundEvents.ENDERMAN_AMBIENT, SoundSource.HOSTILE, 0.4F, 1.0F);
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
            this.level().playSound(null, this, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 3.0F, 1.0F);
            this.resetClimbSoundClock();
        }
        this.climbSoundClock--;
    }

    public void playFleeSound() {
        if (!this.alreadyPlayedFleeSound) {
            this.level().playSound(null, this, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 3.0F, 1.0F);
            this.alreadyPlayedFleeSound = true;
        }
    }

    public void playSpottedSound() {
        if (!this.alreadyPlayedSpottedSound) {
            this.level().playSound(null, this, SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 3.0F, 1.0F);
            this.alreadyPlayedSpottedSound = true;
        }
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
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private void resetChaseSoundClock() {
        this.chaseSoundClock = this.chaseSoundClockReset;
    }

    private void resetClimbSoundClock() {
        this.climbSoundClock = this.climbSoundClockReset;
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
