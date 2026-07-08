package com.favasur.cavehorror.entity.custom;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Random;

public class EndermanStalkGoal extends Goal {
    private final EndermanEntity cavedweller;
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

    public EndermanStalkGoal(EndermanEntity pEnderman, double pSpeedModifier, float pDistanceForAggro) {
        this.distanceForAggro = pDistanceForAggro;
        this.cavedweller = pEnderman;
        this.speedModifier = pSpeedModifier;
    }

    @Override
    public boolean canUse() {
        if (this.cavedweller.isInvisible()) {
            return false;
        } else {
            if (this.cavedweller.getTarget() == null) {
                this.stalkingTarget = this.getTargetToStalk();
            } else {
                this.stalkingTarget = this.cavedweller.getTarget();
            }
            return this.stalkingTarget != null && (this.cavedweller.rRollResult == 3 || this.cavedweller.forcedStalk);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (this.cavedweller.isInvisible()) {
            return false;
        } else {
            return this.stalkingTarget != null && (this.cavedweller.rRollResult == 3 || this.cavedweller.forcedStalk);
        }
    }

    public void switchToAggroIfPlayerInRange() {
        if (this.stalkingTarget.distanceTo(this.cavedweller) < this.distanceForAggro
                && this.cavedweller.inPlayerLineOfSight()
                && this.cavedweller.isPlayerLookingTowards()) {
            this.cavedweller.rRollResult = 0;
            this.cavedweller.forcedStalk = false;
        }
    }

    @Override
    public void start() {
        this.cavedweller.getEntityData().set(EndermanEntity.STALKING_ACCESSOR, true);
        this.ticksTillFlip = this.minTicksTillFlip + this.rand.nextInt(this.maxTicksTillFlip - this.minTicksTillFlip);
        super.start();
    }

    @Override
    public void stop() {
        this.cavedweller.getEntityData().set(EndermanEntity.STALKING_ACCESSOR, false);
        this.cavedweller.getNavigation().stop();
        super.stop();
    }

    private LivingEntity getTargetToStalk() {
        return this.cavedweller.level().getNearestPlayer(this.cavedweller, 200.0);
    }

    @Override
    public void tick() {
        this.switchToAggroIfPlayerInRange();
        LivingEntity livingentity = this.stalkingTarget;
        if (livingentity != null) {
            this.cavedweller.getLookControl().setLookAt(livingentity, 30.0F, 30.0F);
            double d0 = this.cavedweller.distanceToSqr(livingentity);
            this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
            if ((this.followingTargetEvenIfNotSeen || this.cavedweller.getSensing().hasLineOfSight(livingentity))
                    && this.ticksUntilNextPathRecalculation <= 0
                    && (this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0
                    || livingentity.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                    || this.cavedweller.getRandom().nextFloat() < 0.05F)) {
                this.pathedTargetX = livingentity.getX();
                this.pathedTargetY = livingentity.getY();
                this.pathedTargetZ = livingentity.getZ();
                this.ticksUntilNextPathRecalculation = 4 + this.cavedweller.getRandom().nextInt(7);
                if (this.canPenalize) {
                    this.ticksUntilNextPathRecalculation = this.ticksUntilNextPathRecalculation + this.failedPathFindingPenalty;
                    if (this.cavedweller.getNavigation().getPath() != null) {
                        Node finalPathPoint = this.cavedweller.getNavigation().getPath().getEndNode();
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

                if (!this.cavedweller.getNavigation().moveTo(livingentity, this.speedModifier)) {
                    this.ticksUntilNextPathRecalculation += 15;
                }

                this.ticksUntilNextPathRecalculation = this.reducedTickDelay(this.ticksUntilNextPathRecalculation);
            }
        }

        if (this.cavedweller.rRollResult == 3) {
            this.flipClock++;
            if (this.flipClock > this.ticksTillFlip) {
                this.flipToAggroOrFlee();
            }
        }
    }

    private void flipToAggroOrFlee() {
        if (this.rand.nextBoolean()) {
            this.cavedweller.rRollResult = 0;
        } else {
            this.cavedweller.rRollResult = 2;
        }
    }
}
