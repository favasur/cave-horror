package com.favasur.cavehorror.item;

import com.favasur.cavehorror.entity.ModEntityTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "cavehorror");

    public static final DeferredHolder<Item, Item> WORM = ITEMS.register("worm",
            () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> BABY_SPIDER = ITEMS.register("baby_spider",
            () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> CAVE_DWELLER_SPAWN_EGG = ITEMS.register("enderman_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntityTypes.CAVE_DWELLER, 12895428, 790333, new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
