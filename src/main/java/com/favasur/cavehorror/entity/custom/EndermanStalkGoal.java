package com.favasur.cavehorror.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;
import java.util.Random;

public class EndermanStalkGoal extends Goal {
    private final EndermanEntity enderman;
    private final double speedModifier;
    private int minTicksTillFlip = 400;
    private int maxTicksTillFlip = 600;
    private int ticksTillFlip;
    private int flipClock = 0;
    private Path path;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private int failedPathFindingPenalty = 0;
    private final boolean followingTargetEvenIfNotSeen = true;
    private final boolean canPenalize = true;
    private final float distanceForAggro;
    private LivingEntity stalkingTarget;
    private final Random rand = new Random();
    private int ticksUntilNextSteal;

    public EndermanStalkGoal(EndermanEntity pEnderman, double pSpeedModifier, float pDistanceForAggro) {
        this.distanceForAggro = pDistanceForAggro;
        this.enderman = pEnderman;
        this.speedModifier = pSpeedModifier;
        this.ticksUntilNextSteal = 200 + this.rand.nextInt(200);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.enderman.isInvisible()) {
            return false;
        } else {
            if (this.enderman.getTarget() == null) {
                this.stalkingTarget = this.getTargetToStalk();
            } else {
                this.stalkingTarget = this.enderman.getTarget();
            }
            return this.stalkingTarget != null && (this.enderman.rRollResult == 3 || this.enderman.forcedStalk);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (this.enderman.isInvisible()) {
            return false;
        } else {
            return this.stalkingTarget != null && (this.enderman.rRollResult == 3 || this.enderman.forcedStalk);
        }
    }

    public void switchToAggroIfPlayerInRange() {
        if (this.stalkingTarget.distanceTo(this.enderman) < this.distanceForAggro
                && this.enderman.inPlayerLineOfSight()
                && this.enderman.isPlayerLookingTowards()) {
            this.enderman.rRollResult = 0;
            this.enderman.forcedStalk = false;
        }
    }

    @Override
    public void start() {
        this.enderman.getEntityData().set(EndermanEntity.STALKING_ACCESSOR, true);
        this.ticksTillFlip = this.minTicksTillFlip + this.rand.nextInt(this.maxTicksTillFlip - this.minTicksTillFlip);
        this.ticksUntilNextSteal = 200 + this.rand.nextInt(200);
        super.start();
    }

    @Override
    public void stop() {
        this.enderman.getEntityData().set(EndermanEntity.STALKING_ACCESSOR, false);
        this.enderman.getNavigation().stop();
        super.stop();
    }

    private LivingEntity getTargetToStalk() {
        return this.enderman.level().getNearestPlayer(this.enderman, 200.0);
    }

    @Override
    public void tick() {
        this.switchToAggroIfPlayerInRange();
        LivingEntity livingentity = this.stalkingTarget;
        if (livingentity != null) {
            this.enderman.getLookControl().setLookAt(livingentity, 30.0F, 30.0F);
            double d0 = this.enderman.distanceToSqr(livingentity);
            this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
            if ((this.followingTargetEvenIfNotSeen || this.enderman.getSensing().hasLineOfSight(livingentity))
                    && this.ticksUntilNextPathRecalculation <= 0
                    && (this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0
                    || livingentity.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                    || this.enderman.getRandom().nextFloat() < 0.05F)) {
                this.pathedTargetX = livingentity.getX();
                this.pathedTargetY = livingentity.getY();
                this.pathedTargetZ = livingentity.getZ();
                this.ticksUntilNextPathRecalculation = 4 + this.enderman.getRandom().nextInt(7);
                if (this.canPenalize) {
                    this.ticksUntilNextPathRecalculation = this.ticksUntilNextPathRecalculation + this.failedPathFindingPenalty;
                    if (this.enderman.getNavigation().getPath() != null) {
                        Node finalPathPoint = this.enderman.getNavigation().getPath().getEndNode();
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

                if (d0 > 1024.0) {
                    this.ticksUntilNextPathRecalculation += 10;
                } else if (d0 > 256.0) {
                    this.ticksUntilNextPathRecalculation += 5;
                }

                if (!this.enderman.getNavigation().moveTo(livingentity, this.speedModifier)) {
                    this.ticksUntilNextPathRecalculation += 15;
                }

                this.ticksUntilNextPathRecalculation = this.reducedTickDelay(this.ticksUntilNextPathRecalculation);
            }

            // Block stealing: only steal when player isn't looking at the entity
            this.ticksUntilNextSteal--;
            if (this.ticksUntilNextSteal <= 0) {
                BlockPos playerPos = livingentity.blockPosition();
                // Only steal if player has no line of sight to the entity (not watching)
                if (!livingentity.hasLineOfSight(this.enderman)) {
                    this.enderman.stealBlockNear(playerPos, 8);
                    this.ticksUntilNextSteal = 400 + this.rand.nextInt(400);
                } else {
                    // Player is watching - delay and try again soon
                    this.ticksUntilNextSteal = 60 + this.rand.nextInt(60);
                }
            }
        }

        if (this.enderman.rRollResult == 3) {
            this.flipClock++;
            if (this.flipClock > this.ticksTillFlip) {
                this.flipToAggroOrFlee();
            }
        }
    }

    private void flipToAggroOrFlee() {
        this.enderman.rRollResult = 0;
    }
}
