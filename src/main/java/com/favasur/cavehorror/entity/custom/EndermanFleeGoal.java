package com.favasur.cavehorror.entity.custom;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class EndermanFleeGoal extends Goal {
    private final EndermanEntity enderman;
    private final float ticksTillLeave;
    private final float ticksTillFlee;
    private float currentTicksTillLeave;
    private float currentTicksTillFlee;
    private boolean shouldLeave;
    private double fleeX;
    private double fleeY;
    private double fleeZ;
    private int ticksUntilNextPathRecalculation;
    private final double speedModifier;

    public EndermanFleeGoal(EndermanEntity pEnderman, float pTicksTillLeave, double pSpeedModifier) {
        this.enderman = pEnderman;
        this.ticksTillLeave = pTicksTillLeave;
        this.currentTicksTillLeave = pTicksTillLeave;
        this.ticksTillFlee = 10.0F;
        this.currentTicksTillFlee = this.ticksTillFlee;
        this.speedModifier = pSpeedModifier;
    }

    @Override
    public boolean canUse() {
        if (this.enderman.isInvisible()) {
            return false;
        } else {
            return this.enderman.rRollResult == 2 && !this.enderman.forcedStalk && this.enderman.getTarget() != null;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.enderman.rRollResult == 2 && !this.enderman.forcedStalk && this.enderman.getTarget() != null;
    }

    @Override
    public void start() {
        this.getSpotToWalk();
        this.enderman.spottedByPlayer = false;
        this.shouldLeave = false;
    }

    @Override
    public void stop() {
    }

    public boolean isPlayerLookingTowards() {
        LivingEntity pendingTarget = this.enderman.getTarget();
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

    public boolean inPlayerLineOfSight() {
        return this.enderman.getTarget() != null && this.enderman.getTarget().hasLineOfSight(this.enderman);
    }

    public double loopAngle(double angle) {
        if (angle > 360.0) {
            return angle - 360.0;
        } else {
            return angle < 0.0 ? angle + 360.0 : angle;
        }
    }

    private boolean getSpotToWalk() {
        Random rand = new Random();
        double randX = rand.nextDouble() - 0.5;
        double randY = rand.nextInt(64) - 32;
        double randZ = rand.nextDouble() - 0.5;

        if (randX > 0.0) {
            this.fleeX = (this.enderman.getX() + 1.0) * 64.0;
        } else {
            this.fleeX = (this.enderman.getX() - 1.0) * 64.0;
        }

        this.fleeY = this.enderman.getY() + randY;

        if (randZ > 0.0) {
            this.fleeZ = (this.enderman.getZ() + 1.0) * 64.0;
        } else {
            this.fleeZ = (this.enderman.getZ() - 1.0) * 64.0;
        }

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(this.fleeX, this.fleeY, this.fleeZ);

        while (blockPos.getY() > this.enderman.level().getMinBuildHeight()
                && !this.enderman.level().getBlockState(blockPos).isSolid()) {
            blockPos.move(Direction.DOWN);
        }

        BlockState blockstate = this.enderman.level().getBlockState(blockPos);
        boolean flag = blockstate.isSolid();
        boolean flag1 = blockstate.getFluidState().is(FluidTags.WATER);
        return flag && !flag1;
    }

    public void tickStareClock() {
        this.currentTicksTillLeave--;
        if (this.currentTicksTillLeave < 0.0F) {
            this.shouldLeave = true;
        }
    }

    void tickFleeClock() {
        this.currentTicksTillFlee--;
    }

    public void fleeTick() {
        this.enderman.playFleeSound();
        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
        if (this.ticksUntilNextPathRecalculation <= 0) {
            this.ticksUntilNextPathRecalculation = 2;
            if (!this.enderman.getNavigation().moveTo(this.fleeX, this.fleeY, this.fleeZ, this.speedModifier)) {
                this.ticksUntilNextPathRecalculation += 2;
            }
            this.ticksUntilNextPathRecalculation = this.reducedTickDelay(this.ticksUntilNextPathRecalculation);
        }
    }

    @Override
    public void tick() {
        if (this.shouldLeave && (!this.isPlayerLookingTowards() || !this.inPlayerLineOfSight())) {
            this.enderman.discard();
        }

        this.tickFleeClock();
        this.tickStareClock();
        if (this.currentTicksTillFlee <= 0.0F) {
            this.fleeTick();
            this.enderman.isFleeing = true;
            this.enderman.getEntityData().set(EndermanEntity.FLEEING_ACCESSOR, true);
        } else {
            this.enderman.getLookControl().setLookAt(this.enderman.getTarget(), 180.0F, 1.0F);
        }
    }
}
