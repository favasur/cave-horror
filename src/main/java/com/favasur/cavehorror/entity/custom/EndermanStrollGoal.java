package com.favasur.cavehorror.entity.custom;

import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

public class EndermanStrollGoal extends WaterAvoidingRandomStrollGoal {
    private final EndermanEntity cavedweller;

    public EndermanStrollGoal(EndermanEntity pMob, double pSpeedModifier) {
        super(pMob, pSpeedModifier);
        this.cavedweller = pMob;
    }

    @Override
    public boolean canUse() {
        return super.canUse() && this.cavedweller.rRollResult == 4 && !this.cavedweller.forcedStalk;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse() && this.cavedweller.rRollResult == 4;
    }
}
