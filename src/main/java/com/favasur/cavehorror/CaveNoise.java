package com.favasur.cavehorror;

import com.favasur.cavehorror.entity.ModEntityTypes;
import com.favasur.cavehorror.entity.client.EndermanRenderer;
import com.favasur.cavehorror.entity.custom.EndermanEntity;
import com.favasur.cavehorror.item.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.EntityType;
import org.joml.Vector3f;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod("cavehorror")
public class CaveNoise {
    public static final String MODID = "cavehorror";
    private static final Logger LOGGER = LogUtils.getLogger();
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
    private final double chanceToCooldown = 0.4;
    private boolean anySpelunkers = false;
    private final List<Player> spelunkers = new ArrayList<>();

    public CaveNoise(IEventBus modEventBus) {
        this.currentSpeedMod = 5.0F;

        modEventBus.addListener(this::commonSetup);
        ModItems.register(modEventBus);
        ModEntityTypes.register(modEventBus);
        modEventBus.addListener(this::addCreative);

        NeoForge.EVENT_BUS.register(this);

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

        AtomicBoolean dwellerExists = new AtomicBoolean(false);
        if (!overworld.getEntitiesOfClass(EndermanEntity.class, 
                new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                         Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)).isEmpty()) {
            dwellerExists.set(true);
            this.resetCalmTimer();
        }

        this.noiseTimer = (int) (this.noiseTimer - (1.0F * this.currentSpeedMod));
        this.vanillaNoiseTimer = (int) (this.vanillaNoiseTimer - (1.0F * this.currentSpeedMod));
        this.stalkNoiseTimer = (int) (this.stalkNoiseTimer - (1.0F * this.currentSpeedMod));

        if (!dwellerExists.get()) {
            if (this.noiseTimer <= 0 && this.calmTimer <= this.creepyCaveNoiseStart) {
                overworld.players().forEach(p -> this.playCaveSoundToSpelunkers((ServerPlayer) p));
            }
            if (this.vanillaNoiseTimer <= 0 && this.calmTimer <= this.vanillaCaveNoiseStartBuild) {
                overworld.players().forEach(p -> this.playVanillaCaveSoundToSpelunkers((ServerPlayer) p));
            }
        } else if (this.stalkNoiseTimer <= 0) {
            overworld.players().forEach(p -> this.playStalkSoundToSpelunkers((ServerPlayer) p));
        }

        this.canSpawn = this.calmTimer <= 0;
        this.calmTimer = (int) (this.calmTimer - (1.0F * this.currentSpeedMod));

        if (this.canSpawn && !dwellerExists.get()) {
            Random rand = new Random();
            if (rand.nextDouble() <= this.chanceToSpawnPerTick) {
                this.spelunkers.clear();
                this.anySpelunkers = false;
                overworld.players().forEach(p -> this.listSpelunkers((ServerPlayer) p));
                if (this.anySpelunkers && !this.spelunkers.isEmpty()) {
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

        if (this.checkIfPlayerIsSpelunker(player) && !player.isSpectator() && !player.isCreative()) {
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
            this.resetNoiseTimer();
        }
    }

    private void resetNoiseTimer() {
        Random rand = new Random();
        this.noiseTimer = this.ticksNoiseResetMin + rand.nextInt(this.ticksNoiseResetMax);
    }

    public void playVanillaCaveSoundToSpelunkers(ServerPlayer player) {
        if (this.checkIfPlayerIsSpelunker(player) && !player.isSpectator() && !player.isCreative()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.0F, 1.0F);
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

        if (this.checkIfPlayerIsSpelunker(player) && !player.isSpectator() && !player.isCreative()) {
            Random rand2 = new Random();
            net.minecraft.sounds.SoundEvent stalkSound = switch (rand2.nextInt(3)) {
                case 0 -> net.minecraft.sounds.SoundEvents.ENDERMAN_AMBIENT;
                case 1 -> net.minecraft.sounds.SoundEvents.ENDERMAN_STARE;
                case 2 -> net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT;
                default -> net.minecraft.sounds.SoundEvents.ENDERMAN_AMBIENT;
            };
            player.level().playSound(null, soundPos.getX(), soundPos.getY(), soundPos.getZ(), stalkSound, SoundSource.AMBIENT, 2.0F, 1.0F);
            this.resetStalkNoiseTimer();
        }
    }

    private void resetStalkNoiseTimer() {
        Random rand = new Random();
        this.stalkNoiseTimer = this.stalkNoiseMinTime + rand.nextInt(this.stalkNoiseMaxTime - this.stalkNoiseMinTime);
    }

    public boolean checkIfPlayerIsSpelunker(Player player) {
        if (player == null) return false;
        return player.position().y < 40.0 && !player.level().canSeeSky(player.blockPosition());
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
        }
    }
}
