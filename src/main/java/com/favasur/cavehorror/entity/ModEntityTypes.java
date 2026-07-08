package com.favasur.cavehorror.entity;

import com.favasur.cavehorror.entity.custom.EndermanEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "cavehorror");

    public static final DeferredHolder<EntityType<?>, EntityType<EndermanEntity>> CAVE_DWELLER = ENTITY_TYPES.register(
            "enderman",
            () -> EntityType.Builder.<EndermanEntity>of(EndermanEntity::new, MobCategory.MONSTER)
                    .sized(0.4F, 3.0F)
                    .build(ResourceLocation.fromNamespaceAndPath("cavehorror", "enderman").toString())
    );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
