package com.favasur.cavehorror.entity.custom;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public class EndermanTargetTooCloseGoal extends NearestAttackableTargetGoal<Player> {
    private Player pendingTarget;
    private final EndermanEntity enderman;
    private final float distanceThreshold;

    public EndermanTargetTooCloseGoal(EndermanEntity pEnderman, float pDistanceThreshold) {
        super(pEnderman, Player.class, false);
        this.enderman = pEnderman;
        this.distanceThreshold = pDistanceThreshold;
    }

    public void setPendingTarget(@Nullable Player pendingTarget) {
        this.pendingTarget = pendingTarget;
    }

    public boolean inPlayerLineOfSight() {
        return this.pendingTarget != null && this.pendingTarget.hasLineOfSight(this.enderman);
    }

    @Override
    public boolean canUse() {
        if (this.enderman.isInvisible()) {
            return false;
        } else {
            this.setPendingTarget(this.enderman.level().getNearestPlayer(this.enderman, this.distanceThreshold));
            if (this.pendingTarget == null) {
                return false;
            } else {
                return !this.pendingTarget.isSpectator() && this.inPlayerLineOfSight();
            }
        }
    }

    @Override
    public void start() {
        this.enderman.getEntityData().set(EndermanEntity.AGGRO_ACCESSOR, true);
        this.enderman.isAggro = true;
        this.enderman.rRollResult = 0;
        this.target = this.pendingTarget;
        this.enderman.setTarget(this.pendingTarget);
        super.start();
    }

    @Override
    public void stop() {
        this.pendingTarget = null;
        super.stop();
    }

    @Override
    public boolean canContinueToUse() {
        return this.pendingTarget != null && !this.pendingTarget.isSpectator();
    }

    @Override
    public void tick() {
        super.tick();
    }
}
