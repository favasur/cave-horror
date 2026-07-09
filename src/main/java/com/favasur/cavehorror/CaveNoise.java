package com.favasur.cavehorror;

import com.favasur.cavehorror.entity.ModEntityTypes;
import com.favasur.cavehorror.entity.client.EndermanRenderer;
import com.favasur.cavehorror.entity.custom.EndermanEntity;
import com.favasur.cavehorror.item.ModItems;
import com.favasur.cavehorror.torch.LitTorch;
import com.favasur.cavehorror.torch.ModTorchConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod("cavehorror")
public class CaveNoise {
    public static final String MODID = "cavehorror";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Torch Burnout Registration
    public static final DeferredRegister.Blocks TORCH_BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items TORCH_ITEMS = DeferredRegister.createItems(MODID);

    public static final BlockBehaviour.Properties TORCH_BEHAVIOR = BlockBehaviour.Properties.ofFullCopy(Blocks.TORCH)
            .randomTicks();

    public static final DeferredBlock<Block> LIT_TORCH = TORCH_BLOCKS.register("lit_torch",
            () -> new LitTorch(TORCH_BEHAVIOR, ParticleTypes.FLAME, 14));
    public static final DeferredItem<Item> LIT_TORCH_ITEM = TORCH_ITEMS.register("lit_torch",
            () -> new BlockItem(LIT_TORCH.get(), new Item.Properties()));

    public static final DeferredBlock<Block> LIT_SOUL_TORCH = TORCH_BLOCKS.register("lit_soul_torch",
            () -> new LitTorch(TORCH_BEHAVIOR, ParticleTypes.SOUL_FIRE_FLAME, 10));
    public static final DeferredItem<Item> LIT_SOUL_TORCH_ITEM = TORCH_ITEMS.register("lit_soul_torch",
            () -> new BlockItem(LIT_SOUL_TORCH.get(), new Item.Properties()));

    private final boolean SPEED_ALL_CLOCKS = true;
    private final float SPEED_MOD = 5.0F;
    private float currentSpeedMod = 1.0F;
    private final int creepyCaveNoiseStart = 8000;
    private final int creepyCaveNoiseEndBuild = 1000;
    private final float creepyCaveNoiseMinVol = 0.1F;
    private final float creepyCaveNoiseMaxVol = 1.0F;
    private final int vanillaCaveNoiseStartBuild = 15000;
    private final int vanillaCaveNoiseEndBuild = 2000;
    private final int vanillaCaveNoiseStartMinTime = 8000;
    private final int vanillaCaveNoiseStartMaxTime = 10000;
    private final int vanillaCaveNoiseEndMinTime = 4000;
    private final int vanillaCaveNoiseEndMaxTime = 6000;
    private final int stalkNoiseMinTime = 800;
    private final int stalkNoiseMaxTime = 1000;
    private int ticksCalmResetMin;
    private int ticksCalmResetMax;
    private int ticksCalmResetCooldown;
    private int ticksNoiseResetMin;
    private int ticksNoiseResetMax;
    private int calmTimer = 0;
    private int noiseTimer = 0;
    private int stalkNoiseTimer = 0;
    private int vanillaNoiseTimer = 0;
    private boolean canSpawn = false;
    private final double chanceToSpawnPerTick = 0.005;
    private final double spelunkerSpawnChance = 0.04;  // Much higher when player is underground
    private final double chanceToCooldown = 0.4;
    private boolean anySpelunkers = false;
    private final List<Player> spelunkers = new ArrayList<>();
    
    // Track which players currently have a stalker — ensures only one entity per player
    private static final double STALKER_PROXIMITY_RANGE = 300.0; // If a stalker is within 300 blocks, don't spawn another

    public CaveNoise(IEventBus modEventBus, ModContainer modContainer) {
        this.currentSpeedMod = 5.0F;

        modEventBus.addListener(this::commonSetup);
        ModItems.register(modEventBus);
        ModEntityTypes.register(modEventBus);

        // Register torch blocks and items
        TORCH_BLOCKS.register(modEventBus);
        TORCH_ITEMS.register(modEventBus);

        modEventBus.addListener(this::addCreative);

        NeoForge.EVENT_BUS.register(this);

        // Register the Torch Burnout config
        modContainer.registerConfig(ModConfig.Type.COMMON, ModTorchConfig.SPEC);

        this.ticksCalmResetMin = 15000;
        this.ticksCalmResetMax = 18000;
        this.ticksCalmResetCooldown = 16000;
        this.ticksNoiseResetMin = 2000;
        this.ticksNoiseResetMax = 1600;
        this.calmTimer = 25000;
        this.noiseTimer = 4800;
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.CAVE_DWELLER_SPAWN_EGG.get());
        }

        // Add torch blocks to the functional blocks tab
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(LIT_TORCH_ITEM.get());
            event.accept(LIT_SOUL_TORCH_ITEM.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
        this.resetCalmTimer();
    }

    @SubscribeEvent
    public void serverTick(ServerTickEvent.Post event) {
        Level overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        // Check if any player already has a stalker nearby — lone stalker per player
        java.util.Map<UUID, Boolean> playerHasStalker = new java.util.HashMap<>();
        List<EndermanEntity> allEndermen = overworld.getEntitiesOfClass(EndermanEntity.class, 
                new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                         Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        
        for (Player player : overworld.players()) {
            if (player.isSpectator()) continue;
            boolean hasNearby = false;
            for (EndermanEntity enderman : allEndermen) {
                if (enderman.distanceTo(player) < STALKER_PROXIMITY_RANGE) {
                    hasNearby = true;
                    break;
                }
            }
            playerHasStalker.put(player.getUUID(), hasNearby);
        }
        
        // If any player has a stalker, reset the calm timer (entity is active)
        boolean anyPlayerHasStalker = playerHasStalker.values().stream().anyMatch(b -> b);
        if (anyPlayerHasStalker) {
            this.resetCalmTimer();
        }

        // Speed up cave noises near structures — creates intensifying dread as player explores
        float noiseSpeedMod = this.currentSpeedMod;
        for (Player p : overworld.players()) {
            if (EndermanEntity.hasStructuresNearby(p.blockPosition(), 30)) {
                noiseSpeedMod *= 3.0F;
                break;
            }
        }
        this.noiseTimer = (int) (this.noiseTimer - (1.0F * noiseSpeedMod));
        this.vanillaNoiseTimer = (int) (this.vanillaNoiseTimer - (1.0F * noiseSpeedMod));
        this.stalkNoiseTimer = (int) (this.stalkNoiseTimer - (1.0F * noiseSpeedMod));

        // Creepy cave sounds only play when the entity is around — silence means safety
        for (Player p : overworld.players()) {
            boolean hasNearbyStalker = playerHasStalker.getOrDefault(p.getUUID(), false);
            if (hasNearbyStalker) {
                // Entity is near — all sounds intensify
                if (this.noiseTimer <= 0 && this.calmTimer <= this.creepyCaveNoiseStart) {
                    this.playCaveSoundToSpelunkers((ServerPlayer) p);
                }
                if (this.vanillaNoiseTimer <= 0 && this.calmTimer <= this.vanillaCaveNoiseStartBuild) {
                    this.playVanillaCaveSoundToSpelunkers((ServerPlayer) p);
                }
                if (this.stalkNoiseTimer <= 0) {
                    this.playStalkSoundToSpelunkers((ServerPlayer) p);
                }
            }
            // No stalker = complete silence (player knows they're safe)
        }

        this.canSpawn = this.calmTimer <= 0;
        this.calmTimer = (int) (this.calmTimer - (1.0F * this.currentSpeedMod));

        if (this.canSpawn && !anyPlayerHasStalker) {
            Random rand = new Random();

            // Find eligible players: prefer spelunkers (underground players),
            // also accept players near built structures, or any player at all
            this.spelunkers.clear();
            this.anySpelunkers = false;
            overworld.players().forEach(p -> {
                if (p.isSpectator()) return;
                if (this.checkIfPlayerIsSpelunker(p)) {
                    this.anySpelunkers = true;
                    this.spelunkers.add(p);
                }
            });

            // If no spelunkers, check for players near built structures
            if (!this.anySpelunkers) {
                overworld.players().forEach(p -> {
                    if (p.isSpectator()) return;
                    if (EndermanEntity.hasStructuresNearby(p.blockPosition(), 80)) {
                        this.anySpelunkers = true;
                        this.spelunkers.add(p);
                    }
                });
            }

            // If still no candidates, accept any player (spawn underground near them)
            if (!this.anySpelunkers) {
                overworld.players().forEach(p -> {
                    if (p.isSpectator()) return;
                    this.spelunkers.add(p);
                    this.anySpelunkers = true;
                });
            }

            if (this.anySpelunkers && !this.spelunkers.isEmpty()) {
                // Calculate spawn chance: much higher for spelunkers (underground players)
                double spawnChance;
                boolean hasSpelunker = false;
                for (Player p : this.spelunkers) {
                    if (this.checkIfPlayerIsSpelunker(p)) {
                        hasSpelunker = true;
                        break;
                    }
                }
                if (hasSpelunker) {
                    // Player is underground — high spawn rate
                    spawnChance = this.spelunkerSpawnChance;
                } else {
                    // Player is on surface — use normal low rate
                    spawnChance = this.chanceToSpawnPerTick;
                }
                // Boost near structures regardless
                for (Player p : this.spelunkers) {
                    if (EndermanEntity.hasStructuresNearby(p.blockPosition(), 50)) {
                        spawnChance *= 3.0; // 3x more likely near structures
                        break;
                    }
                }

                if (rand.nextDouble() <= spawnChance) {
                    Player victim = this.spelunkers.get(rand.nextInt(this.spelunkers.size()));
                    EndermanEntity enderman = new EndermanEntity(
                            ModEntityTypes.CAVE_DWELLER.get(), overworld);
                    enderman.setInvisible(true);
                    enderman.setPos(enderman.generatePos(victim));
                    overworld.addFreshEntity(enderman);
                    this.resetCalmTimer();
                }
            }
        }
    }

    public boolean listSpelunkers(ServerPlayer player) {
        if (this.checkIfPlayerIsSpelunker(player)) {
            this.anySpelunkers = true;
            this.spelunkers.add(player);
        }
        return true;
    }

    public void playCaveSoundToSpelunkers(ServerPlayer player) {
        float a = (float) (this.calmTimer - this.creepyCaveNoiseEndBuild) / (float) (this.creepyCaveNoiseStart - this.creepyCaveNoiseEndBuild);
        float b = 1.0F - a;
        b = Math.max(0.0F, Math.min(1.0F, b));
        float vol = this.creepyCaveNoiseMinVol + (this.creepyCaveNoiseMaxVol - this.creepyCaveNoiseMinVol) * b;

        if (this.checkIfPlayerIsSpelunker(player) && !player.isSpectator()) {
            Random rand = new Random();
            net.minecraft.sounds.SoundEvent sound = switch (rand.nextInt(2)) {
                case 0 -> net.minecraft.sounds.SoundEvents.ENDERMAN_SCREAM;
                case 1 -> net.minecraft.sounds.SoundEvents.ENDERMAN_AMBIENT;
                default -> net.minecraft.sounds.SoundEvents.ENDERMAN_AMBIENT;
            };
            // Spawn white eyes particles around the player in the cave
            if (player.serverLevel() != null) {
                Random particleRand = new Random();
                for (int i = 0; i < 6; i++) {
                    double px = player.getX() + (particleRand.nextDouble() - 0.5) * 16.0;
                    double py = player.getY() + particleRand.nextDouble() * 6.0 - 1.0;
                    double pz = player.getZ() + (particleRand.nextDouble() - 0.5) * 16.0;
                    player.serverLevel().sendParticles(
                            new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 0.8F),
                            px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.AMBIENT, vol, 1.0F);

            // First cave sound — reveal the eyes of any entity stalking this player
            revealEntityEyes(player);

            this.resetNoiseTimer();
        }
    }

    private void resetNoiseTimer() {
        Random rand = new Random();
        this.noiseTimer = this.ticksNoiseResetMin + rand.nextInt(this.ticksNoiseResetMax);
    }

    public void playVanillaCaveSoundToSpelunkers(ServerPlayer player) {
        if (this.checkIfPlayerIsSpelunker(player) && !player.isSpectator()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.0F, 1.0F);

            // First cave ambient sound — reveal the eyes of any entity stalking this player
            revealEntityEyes(player);

            this.resetVanillaNoiseTimer();
        }
    }

    private void resetVanillaNoiseTimer() {
        float a = (float) (this.calmTimer - this.vanillaCaveNoiseEndBuild) / (float) (this.vanillaCaveNoiseStartBuild - this.vanillaCaveNoiseEndBuild);
        a = Math.max(0.0F, Math.min(1.0F, a));
        float b = 1.0F - a;
        int newMin = Math.round((this.vanillaCaveNoiseEndMinTime - this.vanillaCaveNoiseStartMinTime) * b + this.vanillaCaveNoiseStartMinTime);
        int newMax = Math.round((this.vanillaCaveNoiseEndMaxTime - this.vanillaCaveNoiseStartMaxTime) * b + this.vanillaCaveNoiseStartMaxTime);
        Random rand = new Random();
        this.vanillaNoiseTimer = rand.nextInt(newMax - newMin) + newMin;
    }

    public void playStalkSoundToSpelunkers(ServerPlayer player) {
        Random rand = new Random();
        BlockPos soundPos = new BlockPos(
                (int) (player.position().x + (-25 + rand.nextInt(50))),
                (int) player.position().y,
                (int) (player.position().z + (-25 + rand.nextInt(50))));

        if (this.checkIfPlayerIsSpelunker(player) && !player.isSpectator()) {
            Random rand2 = new Random();
            net.minecraft.sounds.SoundEvent stalkSound = switch (rand2.nextInt(2)) {
                case 0 -> net.minecraft.sounds.SoundEvents.ENDERMAN_AMBIENT;
                case 1 -> net.minecraft.sounds.SoundEvents.ENDERMAN_STARE;
                default -> net.minecraft.sounds.SoundEvents.ENDERMAN_AMBIENT;
            };
            player.level().playSound(null, soundPos.getX(), soundPos.getY(), soundPos.getZ(), stalkSound, SoundSource.AMBIENT, 2.0F, 1.0F);

            // Stalk sound — also reveals entity eyes
            revealEntityEyes(player);

            this.resetStalkNoiseTimer();
        }
    }

    private void resetStalkNoiseTimer() {
        Random rand = new Random();
        this.stalkNoiseTimer = this.stalkNoiseMinTime + rand.nextInt(this.stalkNoiseMaxTime - this.stalkNoiseMinTime);
    }

    /**
     * Check if a position has any natural cave blocks nearby (ores, water, lava, etc.).
     * This distinguishes natural caves from player-dug cavities.
     */
    private boolean hasNaturalCaveBlocksNearby(Level level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos checkPos = center.offset(dx, dy, dz);
                    Block block = level.getBlockState(checkPos).getBlock();
                    if (block == Blocks.IRON_ORE || block == Blocks.GOLD_ORE
                            || block == Blocks.DIAMOND_ORE || block == Blocks.COAL_ORE
                            || block == Blocks.COPPER_ORE || block == Blocks.REDSTONE_ORE
                            || block == Blocks.LAPIS_ORE || block == Blocks.EMERALD_ORE
                            || block == Blocks.DEEPSLATE_IRON_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
                            || block == Blocks.DEEPSLATE_DIAMOND_ORE || block == Blocks.DEEPSLATE_COAL_ORE
                            || block == Blocks.DEEPSLATE_COPPER_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
                            || block == Blocks.DEEPSLATE_LAPIS_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
                            || block == Blocks.GRAVEL || block == Blocks.DIORITE
                            || block == Blocks.ANDESITE || block == Blocks.GRANITE
                            || block == Blocks.TUFF || block == Blocks.DEEPSLATE
                            || block == Blocks.WATER || block == Blocks.LAVA
                            || block == Blocks.COBWEB || block == Blocks.AMETHYST_CLUSTER
                            || block == Blocks.SCULK || block == Blocks.SCULK_VEIN
                            || block == Blocks.DRIPSTONE_BLOCK || block == Blocks.POINTED_DRIPSTONE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Find the surface Y level (highest motion-blocking block) at a given X,Z.
     */
    private int getSurfaceY(Level level, int x, int z) {
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    /**
     * Find any EndermanEntity stalking this player and reveal its eyes.
     * Called when a cave ambient sound plays for the first time.
     */
    private void revealEntityEyes(ServerPlayer player) {
        if (player.serverLevel() == null) return;
        // Only reveal eyes in pitch darkness
        if (player.serverLevel().getMaxLocalRawBrightness(player.blockPosition()) > 0) return;
        List<EndermanEntity> nearbyEndermen = player.serverLevel().getEntitiesOfClass(
                EndermanEntity.class,
                player.getBoundingBox().inflate(50),
                e -> e.getTarget() == player
        );
        for (EndermanEntity enderman : nearbyEndermen) {
            enderman.setEyesVisible(true);
        }
    }

    public boolean checkIfPlayerIsSpelunker(Player player) {
        if (player == null) return false;
        Level level = player.level();
        BlockPos playerPos = player.blockPosition();

        // Must be underground (y < 40, no sky access)
        if (player.position().y >= 40.0 || level.canSeeSky(playerPos)) return false;

        // Must be at least 15 blocks beneath the surface
        int surfaceY = getSurfaceY(level, playerPos.getX(), playerPos.getZ());
        if (playerPos.getY() > surfaceY - 15) return false;

        // Check for connection to natural caves: scan for natural blocks nearby
        return hasNaturalCaveBlocksNearby(level, playerPos, 8);
    }

    private void resetCalmTimer() {
        Random rand = new Random();
        this.calmTimer = this.ticksCalmResetMin + rand.nextInt(this.ticksCalmResetMax);
        if (rand.nextDouble() <= this.chanceToCooldown) {
            this.calmTimer = this.ticksCalmResetCooldown + rand.nextInt(this.ticksCalmResetCooldown);
        }
    }

    @SubscribeEvent
    public void livingKnockbackEvent(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof EndermanEntity) {
            event.setStrength(0.0F);
        }
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Replace vanilla Endermen with the mod's Enderman
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity().getType() == EntityType.ENDERMAN) {
            event.setCanceled(true);
            EndermanEntity dweller = new EndermanEntity(
                    ModEntityTypes.CAVE_DWELLER.get(), event.getLevel());
            dweller.setPos(event.getEntity().position());
            event.getLevel().addFreshEntity(dweller);
        }
    }

    @SubscribeEvent
    public void mobDespawn(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EndermanEntity) {
            LOGGER.info("Entity left level: {}", event.getEntity());
        }
    }

    @EventBusSubscriber(modid = "cavehorror", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            EntityRenderers.register(ModEntityTypes.CAVE_DWELLER.get(), EndermanRenderer::new);

            // Set render layers for torch blocks
            ItemBlockRenderTypes.setRenderLayer(LIT_TORCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(LIT_SOUL_TORCH.get(), RenderType.cutout());
        }
    }
}
