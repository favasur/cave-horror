package com.favasur.cavehorror.entity.custom;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

public class EndermanStalkTargetGoal extends NearestAttackableTargetGoal<Player> {
    public EndermanStalkTargetGoal(Mob pMob, Class<Player> pTargetType, boolean pMustSee) {
        super(pMob, pTargetType, pMustSee);
    }
}
