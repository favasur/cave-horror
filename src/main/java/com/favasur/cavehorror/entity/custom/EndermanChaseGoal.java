package com.favasur.cavehorror.entity.custom;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Random;

public class EndermanChaseGoal extends Goal {
    protected final PathfinderMob mob;
    private final EndermanEntity enderman;
    private final double speedModifier;
    private double speedInLavaPerTick = 0.6;
    private final boolean followingTargetEvenIfNotSeen;
    private Path path;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private long lastCanUseCheck;
    private static final long COOLDOWN_BETWEEN_CAN_USE_CHECKS = 20L;
    private int failedPathFindingPenalty = 0;
    private boolean canPenalize = false;
    private final float ticksTillChase;
    private float currentTicksTillChase;
    private boolean shouldUseShortPath = false;
    private boolean squeezing = false;
    private Path shortPath;
    private Vec3 vecNodePos;
    private Vec3 vecMobPos;
    private final int ticksToSqueeze;
    private int currentTicksToSqueeze;
    private final int ticksTillLeave;
    private int currentTicksTillLeave;
    Vec3 xPathStartVec;
    Vec3 zPathStartVec;
    Vec3 xPathTargetVec;
    Vec3 zPathTargetVec;
    Vec3 vecTargetPos;
    Vec3 nodePositionCooldownPos;
    BlockPos nodePos;
    private boolean shortPathAvailable;
    private boolean normalPathAvailable;
    final Random rand = new Random();
    BlockPos currentBlock = new BlockPos(0, 0, 0);
    BlockPos oldBlock = new BlockPos(0, 0, 0);
    int torchDestructionRadius = 1;
    BlockPos checkBlockForTorch;

    public EndermanChaseGoal(PathfinderMob pMob, EndermanEntity pEnderman, double pSpeedModifier,
                            boolean pFollowingTargetEvenIfNotSeen, float pTicksTillChase) {
        this.mob = pMob;
        this.speedModifier = pSpeedModifier;
        this.followingTargetEvenIfNotSeen = pFollowingTargetEvenIfNotSeen;
        this.enderman = pEnderman;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.ticksTillChase = pTicksTillChase;
        this.currentTicksTillChase = pTicksTillChase;
        this.vecNodePos = null;
        this.ticksToSqueeze = 15;
        this.nodePos = null;
        this.ticksTillLeave = 600;
        this.currentTicksTillLeave = this.ticksTillLeave;
    }

    @Override
    public boolean canUse() {
        if (this.enderman.isInvisible()) {
            return false;
        } else if (this.enderman.rRollResult == 0 && !this.enderman.forcedStalk) {
            long i = this.mob.level().getGameTime();
            if (i - this.lastCanUseCheck < 20L) {
                return false;
            } else {
                this.lastCanUseCheck = i;
                LivingEntity livingentity = this.mob.getTarget();
                if (livingentity == null) {
                    return false;
                } else if (!livingentity.isAlive()) {
                    return false;
                } else if (this.canPenalize) {
                    if (--this.ticksUntilNextPathRecalculation <= 0) {
                        this.path = this.mob.getNavigation().createPath(livingentity, 0);
                        this.ticksUntilNextPathRecalculation = 2;
                        return this.path != null;
                    } else {
                        return true;
                    }
                } else {
                    this.path = this.mob.getNavigation().createPath(livingentity, 0);
                    return this.path != null
                            || this.getAttackReachSqr(livingentity) >= this.mob.distanceToSqr(
                            livingentity.getX(), livingentity.getY(), livingentity.getZ());
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity livingentity = this.mob.getTarget();
        if (livingentity == null) {
            return false;
        } else if (!livingentity.isAlive()) {
            this.enderman.discard();
            return false;
        } else if (!this.followingTargetEvenIfNotSeen) {
            return !this.mob.getNavigation().isDone();
        } else {
            return !(livingentity instanceof Player p) || (!p.isCreative() && !p.isSpectator());
        }
    }

    @Override
    public void start() {
        this.ticksUntilNextPathRecalculation = 0;
        this.ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        LivingEntity livingentity = this.mob.getTarget();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(livingentity)) {
            this.mob.setTarget(null);
        }
        this.enderman.squeezeCrawling = false;
        this.enderman.getEntityData().set(EndermanEntity.AGGRO_ACCESSOR, false);
        this.enderman.isAggro = false;
        this.enderman.refreshDimensions();
        this.currentTicksTillChase = this.ticksTillChase;
        this.mob.getNavigation().stop();
        this.enderman.setNoGravity(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tickAggroClock() {
        this.currentTicksTillChase--;
        if (this.currentTicksTillChase <= 0.0F) {
            this.enderman.getEntityData().set(EndermanEntity.AGGRO_ACCESSOR, true);
        }
        this.enderman.isAggro = true;
        this.enderman.refreshDimensions();
    }

    public Path getShortPath(LivingEntity livingentity) {
        return this.shortPath = this.enderman.createShortPath(livingentity);
    }

    public static double lerp(double a, double b, double f) {
        return (b - a) * f + a;
    }

    public void squeezingTick() {
        this.enderman.setNoGravity(true);
        if (this.mob.getNavigation().getPath() != null) {
            this.nodePos = this.mob.getNavigation().getPath().getTarget();
        }
        this.mob.getNavigation().stop();
        if (this.nodePos == null) {
            this.stopSqueezing();
        } else {
            if (this.vecNodePos == null) {
                this.vecNodePos = new Vec3(this.nodePos.getX(), this.nodePos.getY(), this.nodePos.getZ());
            }
            this.nodePositionCooldownPos = this.vecNodePos;
            Vec3 vecOldMobPos = this.enderman.getViewVector(1.0F);
            if (this.xPathStartVec == null) {
                if (vecOldMobPos.x < this.vecNodePos.x) {
                    this.xPathStartVec = new Vec3(this.vecNodePos.x - 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
                    this.xPathTargetVec = new Vec3(this.vecNodePos.x + 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
                } else {
                    this.xPathStartVec = new Vec3(this.vecNodePos.x + 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
                    this.xPathTargetVec = new Vec3(this.vecNodePos.x - 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
                }
            }
            if (this.zPathStartVec == null) {
                if (vecOldMobPos.z < this.vecNodePos.z) {
                    this.zPathStartVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z - 1.0);
                    this.zPathTargetVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z + 1.0);
                } else {
                    this.zPathStartVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z + 1.0);
                    this.zPathTargetVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z - 1.0);
                }
            }
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(
                    this.xPathTargetVec.x, this.xPathTargetVec.y, this.xPathTargetVec.z);
            BlockState blockstate = this.enderman.level().getBlockState(mutableBlockPos);
            boolean xBlocked = blockstate.isSolid();
            mutableBlockPos = new BlockPos.MutableBlockPos(this.zPathTargetVec.x, this.zPathTargetVec.y, this.zPathTargetVec.z);
            blockstate = this.enderman.level().getBlockState(mutableBlockPos);
            boolean zBlocked = blockstate.isSolid();
            if (xBlocked) {
                this.vecMobPos = this.zPathStartVec;
                this.vecTargetPos = this.zPathTargetVec;
            }
            if (zBlocked) {
                this.vecMobPos = this.xPathStartVec;
                this.vecTargetPos = this.xPathTargetVec;
            }
            if (this.vecTargetPos != null && this.vecMobPos != null) {
                this.currentTicksToSqueeze++;
                float tickF = (float) this.currentTicksToSqueeze / (float) this.ticksToSqueeze;
                Vec3 vecCurrentMobPos = new Vec3(
                        lerp(this.vecMobPos.x, this.vecTargetPos.x, tickF),
                        this.vecMobPos.y,
                        lerp(this.vecMobPos.z, this.vecTargetPos.z, tickF));
                Vec3 rotAxis = new Vec3(this.vecTargetPos.x - this.vecMobPos.x, 0.0, this.vecTargetPos.z - this.vecMobPos.z);
                rotAxis = rotAxis.normalize();
                double rotAngle = Math.toDegrees(Math.atan2(-rotAxis.x, rotAxis.z));
                this.enderman.setYRot((float) rotAngle);
                this.enderman.moveTo(vecCurrentMobPos.x, vecCurrentMobPos.y, vecCurrentMobPos.z, (float) rotAngle, (float) rotAngle);
                if (tickF >= 1.0F) {
                    this.enderman.setPos(this.vecTargetPos.x, this.vecTargetPos.y, this.vecTargetPos.z);
                    this.stopSqueezing();
                }
            } else {
                this.stopSqueezing();
            }
        }
    }

    public void stopSqueezing() {
        this.squeezing = false;
        this.enderman.getEntityData().set(EndermanEntity.SQUEEZING_ACCESSOR, false);
        this.enderman.setNoGravity(false);
    }

    public void startSqueezing() {
        this.vecNodePos = null;
        this.vecMobPos = null;
        this.xPathStartVec = null;
        this.zPathStartVec = null;
        this.xPathTargetVec = null;
        this.zPathTargetVec = null;
        this.vecTargetPos = null;
        this.currentTicksToSqueeze = 0;
        this.squeezing = true;
        this.enderman.getEntityData().set(EndermanEntity.SQUEEZING_ACCESSOR, true);
        this.nodePos = null;
    }

    public boolean checkIfShouldSqueeze(Path pathToCheck) {
        if (pathToCheck == null) {
            return false;
        } else {
            if (!pathToCheck.isDone()) {
                BlockPos blockpos = pathToCheck.getTarget();
                if (this.nodePositionCooldownPos != null
                        && blockpos.getX() == (int) this.nodePositionCooldownPos.x
                        && blockpos.getY() == (int) this.nodePositionCooldownPos.y
                        && blockpos.getZ() == (int) this.nodePositionCooldownPos.z) {
                    return false;
                } else {
                    BlockPos checkPos = new BlockPos(blockpos.getX(), blockpos.getY() + 1, blockpos.getZ());
                    BlockState blockstate = this.enderman.level().getBlockState(checkPos);
                    return blockstate.isSolid();
                }
            } else {
                return false;
            }
        }
    }

    public void aggroTick() {
        this.enderman.playChaseSound();
        LivingEntity livingentity = this.mob.getTarget();
        if (this.mob.getNavigation().getPath() != null && this.checkIfShouldSqueeze(this.mob.getNavigation().getPath()) && this.shouldUseShortPath) {
            this.startSqueezing();
            this.squeezing = true;
            this.enderman.getEntityData().set(EndermanEntity.SQUEEZING_ACCESSOR, true);
        } else {

            if (livingentity != null) {
                double d0 = this.mob.distanceToSqr(livingentity);
                this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
                if ((this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight(livingentity))
                        && this.ticksUntilNextPathRecalculation <= 0
                        && (this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0
                        || livingentity.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                        || this.mob.getRandom().nextFloat() < 0.05F)) {
                    this.pathedTargetX = livingentity.getX();
                    this.pathedTargetY = livingentity.getY();
                    this.pathedTargetZ = livingentity.getZ();
                    this.ticksUntilNextPathRecalculation = 2;
                    if (this.canPenalize) {
                        this.ticksUntilNextPathRecalculation += this.failedPathFindingPenalty;
                        if (this.mob.getNavigation().getPath() != null) {
                            Node finalPathPoint = this.mob.getNavigation().getPath().getEndNode();
                            if (finalPathPoint != null
                                    && livingentity.distanceToSqr(finalPathPoint.x, finalPathPoint.y, finalPathPoint.z) < 1.0) {
                                this.failedPathFindingPenalty = 0;
                            } else {
                                this.failedPathFindingPenalty += 10;
                            }
                        } else {
                            this.failedPathFindingPenalty += 10;
                        }
                    }

                    this.getShortPath(livingentity);
                    this.shortPathAvailable = this.shortPath != null && this.shortPath.getEndNode() != null
                            && livingentity.distanceToSqr(this.shortPath.getEndNode().x, this.shortPath.getEndNode().y, this.shortPath.getEndNode().z) < 2.0;

                    this.shouldUseShortPath = this.shortPathAvailable;
                    if (d0 > 1024.0) {
                        this.ticksUntilNextPathRecalculation += 10;
                    } else if (d0 > 256.0) {
                        this.ticksUntilNextPathRecalculation += 5;
                    }

                    if (!this.shouldUseShortPath) {
                        if (!this.mob.getNavigation().moveTo(livingentity, this.speedModifier)) {
                            this.enderman.startedMovingChase = true;
                            this.ticksUntilNextPathRecalculation += 8;
                        }
                    } else {
                        if (!this.mob.getNavigation().moveTo(this.shortPath, this.speedModifier)) {
                            this.enderman.startedMovingChase = true;
                            this.ticksUntilNextPathRecalculation += 8;
                        }
                    }

                    this.ticksUntilNextPathRecalculation = this.reducedTickDelay(this.ticksUntilNextPathRecalculation);
                }

                this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
                this.checkAndPerformAttack(livingentity, d0);
            }

            if (this.enderman.isInLava() && this.enderman.getNavigation().getPath() != null
                    && this.enderman.getNavigation().getPath().getNextNodeIndex() < this.enderman.getNavigation().getPath().getNodeCount()) {
                Vec3 a = this.enderman.position();
                BlockPos b = this.enderman.getNavigation().getPath().getTarget();
                Vec3 dir = new Vec3(b.getX() - a.x, b.getY() - a.y, b.getZ() - a.z).normalize();
                double dist = dir.length();
                if (dist > this.speedInLavaPerTick) {
                    this.enderman.setPos(this.enderman.position().add(
                            new Vec3(dir.x * this.speedInLavaPerTick, dir.y * this.speedInLavaPerTick, dir.z * this.speedInLavaPerTick)));
                } else {
                    this.enderman.setPos(new Vec3(b.getX(), b.getY(), b.getZ()));
                }
            }
        }
    }

    // Spider-like climbing is handled in EndermanEntity.tick() via setClimbing(horizontalCollision)

    @Override
    public void tick() {
        this.enderman.squeezeCrawling = this.squeezing;
        LivingEntity livingentity = this.enderman.getTarget();

        this.tickAggroClock();
        if (!this.squeezing && livingentity != null) {
            if (this.enderman.isAggro) {
                this.mob.getLookControl().setLookAt(livingentity, 90.0F, 90.0F);
            } else {
                this.mob.getLookControl().setLookAt(livingentity, 180.0F, 1.0F);
            }
        }

        if (this.enderman.getEntityData().get(EndermanEntity.AGGRO_ACCESSOR)) {
            if (this.squeezing) {
                this.squeezingTick();
            } else {
                this.aggroTick();
            }
        }

        this.currentTicksTillLeave--;
        if (this.currentTicksTillLeave <= 0 && (!this.isPlayerLookingTowards() || !this.inPlayerLineOfSight())) {
            this.enderman.discard();
        }

        this.currentBlock = new BlockPos(
                (int) Math.floor(this.enderman.getX()), (int) Math.floor(this.enderman.getY()), (int) Math.floor(this.enderman.getZ()));
        if (!this.currentBlock.equals(this.oldBlock)) {
            for (int dX = -this.torchDestructionRadius; dX < this.torchDestructionRadius + 1; dX++) {
                for (int dY = -this.torchDestructionRadius; dY < this.torchDestructionRadius + 1; dY++) {
                    for (int dZ = -this.torchDestructionRadius; dZ < this.torchDestructionRadius + 1; dZ++) {
                        this.checkBlockForTorch = new BlockPos(
                                this.currentBlock.getX() + dX, this.currentBlock.getY() + dY, this.currentBlock.getZ() + dZ);
                        BlockState targetState = this.enderman.level().getBlockState(this.checkBlockForTorch);
                        if (targetState.is(Blocks.TORCH) || targetState.is(Blocks.WALL_TORCH)
                                || targetState.is(Blocks.SOUL_TORCH) || targetState.is(Blocks.SOUL_WALL_TORCH)
                                || targetState.is(Blocks.REDSTONE_TORCH) || targetState.is(Blocks.REDSTONE_WALL_TORCH)) {
                            this.enderman.level().destroyBlock(this.checkBlockForTorch, true);
                        }
                    }
                }
            }
        }
        this.oldBlock = this.currentBlock;
    }

    public boolean isPlayerLookingTowards() {
        LivingEntity pendingTarget = this.enderman.getTarget();
        if (pendingTarget == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        float fov = (float) (Integer) minecraft.options.fov().get();
        float yFovMod = 0.65F;
        float fovMod = (35.0F / fov - 1.0F) * 0.4F + 1.0F;
        fov *= fovMod;

        Vec3 a = pendingTarget.position();
        Vec3 b = this.enderman.position();
        Vec2 dist = new Vec2((float) (b.x - a.x), (float) (b.z - a.z));
        dist = dist.normalized();
        double newAngle = Math.toDegrees(Math.atan2(dist.x, dist.y));
        float lookX = (float) pendingTarget.getViewVector(1.0F).x;
        float lookZ = (float) pendingTarget.getViewVector(1.0F).z;
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
        float lookY = (float) pendingTarget.getViewVector(1.0F).y;
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
        return angle > 360.0 ? angle - 360.0 : (angle < 0.0 ? angle + 360.0 : angle);
    }

    public boolean inPlayerLineOfSight() {
        LivingEntity pendingTarget = this.enderman.getTarget();
        return pendingTarget != null && pendingTarget.hasLineOfSight(this.enderman);
    }

    protected void checkAndPerformAttack(LivingEntity pEnemy, double pDistToEnemySqr) {
        double d0 = this.getAttackReachSqr(pEnemy);
        if (pDistToEnemySqr <= d0 && this.ticksUntilNextAttack <= 0) {
            this.resetAttackCooldown();
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.mob.doHurtTarget(pEnemy);
            pEnemy.hurt(this.mob.damageSources().mobAttack(this.mob),
                    (float) this.enderman.getAttributeValue(Attributes.ATTACK_DAMAGE));
            if (pEnemy.getOffhandItem().is(Items.SHIELD)) {
                pEnemy.getOffhandItem().hurtAndBreak(10000, pEnemy, EquipmentSlot.OFFHAND);
            }
            if (pEnemy.getMainHandItem().is(Items.SHIELD)) {
                pEnemy.getMainHandItem().hurtAndBreak(10000, pEnemy, EquipmentSlot.MAINHAND);
            }
        }
    }

    protected void resetAttackCooldown() {
        this.ticksUntilNextAttack = this.reducedTickDelay(20);
    }

    protected boolean isTimeToAttack() {
        return this.ticksUntilNextAttack <= 0;
    }

    protected int getTicksUntilNextAttack() {
        return this.ticksUntilNextAttack;
    }

    protected int getAttackInterval() {
        return this.reducedTickDelay(20);
    }

    protected double getAttackReachSqr(LivingEntity pAttackTarget) {
        return (double) (this.mob.getBbWidth() * 4.0F * this.mob.getBbWidth() * 4.0F + pAttackTarget.getBbWidth());
    }
}
