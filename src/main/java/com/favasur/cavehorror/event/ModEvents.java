package com.favasur.cavehorror.event;

import com.favasur.cavehorror.entity.ModEntityTypes;
import com.favasur.cavehorror.entity.custom.EndermanEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = "cavehorror", bus = EventBusSubscriber.Bus.MOD)
public class ModEvents {
    @SubscribeEvent
    public static void entityAttributeEvent(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.CAVE_DWELLER.get(), EndermanEntity.setAttributes());
    }
}
