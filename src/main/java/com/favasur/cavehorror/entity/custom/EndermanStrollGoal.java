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
        return super.canUse() && this.enderman.rRollResult == 4 && !this.enderman.forcedStalk;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse() && this.enderman.rRollResult == 4;
    }
}
