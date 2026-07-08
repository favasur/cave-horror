package com.favasur.cavehorror.entity.custom;

import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

public class EndermanStrollGoal extends WaterAvoidingRandomStrollGoal {
    private final EndermanEntity enderman;

    public EndermanStrollGoal(EndermanEntity pMob, double pSpeedModifier) {
        super(pMob, pSpeedModifier);
        this.enderman = pMob;
    }

    @Override
    public boolean canUse() {
        if (this.enderman.forcedStalk) {
            return false;
        }
        return super.canUse() && (this.enderman.rRollResult == 4 || this.enderman.getTarget() == null);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.enderman.forcedStalk) {
            return false;
        }
        // Stop strolling if a target is acquired while in fallback mode
        if (this.enderman.rRollResult != 4 && this.enderman.getTarget() != null) {
            return false;
        }
        return super.canContinueToUse() && (this.enderman.rRollResult == 4 || this.enderman.getTarget() == null);
    }
}
