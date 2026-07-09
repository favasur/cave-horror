package com.favasur.cavehorror.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Random;

public class EndermanStalkGoal extends Goal {
    private final EndermanEntity enderman;
    private final double speedModifier;
    private Path path;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private int failedPathFindingPenalty = 0;
    private final boolean followingTargetEvenIfNotSeen = true;
    private final boolean canPenalize = true;
    private final float distanceForAggro;
    private LivingEntity stalkingTarget;
    private final Random rand = new Random();
    private int ticksUntilNextSteal;
    private int ticksUntilNextAltar;

    // Entity keeps 20-30 block distance from the player (tug-of-war between path-toward and back-away)
    private static final double MIN_STALK_DISTANCE = 20.0;
    private static final double MAX_STALK_DISTANCE = 30.0;
    private static final int STARE_TRIGGER_TICKS = 200; // 10 seconds of staring triggers aggro
    private int playerStareTicks = 0;

    // Mineshaft/tunnel stalking: when player is in mineshafts or near Enderman structures,
    // the entity stalks much closer (20-30 blocks away instead of 200)
    private boolean isCloseStalking = false;
    private int closeStalkCooldown = 0;
    private static final int MINESHAFT_STALK_RANGE = 30;
    private static final int NORMAL_STALK_RANGE = 200;
    private static final int MINESHAFT_SCAN_INTERVAL = 100;

    // Structure building: only after several in-game days
    private static final long STRUCTURE_MIN_WORLD_AGE = 48000;  // 2 Minecraft days
    private static final int STRUCTURE_COOLDOWN_TICKS = 24000;  // 1 day between structures

    // Stealing cooldown decreases as days pass (entity gets bolder)
    private static final int STEAL_COOLDOWN_MAX = 1800;

    // Movement tracking for ambush detection
    private Vec3 prevPlayerPos = Vec3.ZERO;
    private boolean hasPrevPlayerPos = false;
    private int phaseCooldown = 0;
    private static final int PHASE_COOLDOWN_TICKS = 60;   // 3 seconds between wall phases

    // Corridor ambush: stalks from behind for 10 min, then phases out of corridor walls
    private int runAwayStalkTimer = 0;
    private boolean readyToAmbush = false;
    private int ambushCooldown = 0;
    private static final int RUNAWAY_STALK_TIME = 12000;      // 10 minutes before ambush
    private static final int AMBUSH_COOLDOWN_TICKS = 100;     // 5 seconds between ambushes

    public EndermanStalkGoal(EndermanEntity pEnderman, double pSpeedModifier, float pDistanceForAggro) {
        this.distanceForAggro = pDistanceForAggro;
        this.enderman = pEnderman;
        this.speedModifier = pSpeedModifier;
        this.ticksUntilNextSteal = 1200 + this.rand.nextInt(600);
        this.ticksUntilNextAltar = 24000 + this.rand.nextInt(12000);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.enderman.isInvisible()) {
            return false;
        } else {
            if (this.enderman.getTarget() == null) {
                this.stalkingTarget = this.getTargetToStalk();
            } else {
                this.stalkingTarget = this.enderman.getTarget();
            }
            return this.stalkingTarget != null && (this.enderman.rRollResult == 3 || this.enderman.forcedStalk);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (this.enderman.isInvisible()) {
            return false;
        } else {
            return this.stalkingTarget != null && (this.enderman.rRollResult == 3 || this.enderman.forcedStalk);
        }
    }

    public void switchToAggroIfPlayerInRange() {
        if (this.stalkingTarget.distanceTo(this.enderman) < this.distanceForAggro
                && this.enderman.inPlayerLineOfSight()
                && this.enderman.isPlayerLookingTowards()) {
            this.enderman.rRollResult = 0;
            this.enderman.forcedStalk = false;
        }
    }

    @Override
    public void start() {
        this.enderman.getEntityData().set(EndermanEntity.STALKING_ACCESSOR, true);
        this.ticksUntilNextSteal = 1200 + this.rand.nextInt(600);
        this.ticksUntilNextAltar = 24000 + this.rand.nextInt(12000);
        super.start();
    }

    @Override
    public void stop() {
        this.enderman.getEntityData().set(EndermanEntity.STALKING_ACCESSOR, false);
        this.enderman.getNavigation().stop();
        super.stop();
    }

    private LivingEntity getTargetToStalk() {
        if (this.isCloseStalking) {
            return this.enderman.level().getNearestPlayer(this.enderman, MINESHAFT_STALK_RANGE);
        }
        return this.enderman.level().getNearestPlayer(this.enderman, NORMAL_STALK_RANGE);
    }

    private long getWorldAge() {
        Level level = this.enderman.level();
        if (level != null && level.getServer() != null) {
            return level.getServer().overworld().getGameTime();
        }
        return 0;
    }

    private boolean isOldEnoughForStructures() {
        return getWorldAge() >= STRUCTURE_MIN_WORLD_AGE;
    }

    private int getDaysPassed() {
        return (int) (getWorldAge() / 24000);
    }

    @Override
    public void tick() {
        this.switchToAggroIfPlayerInRange();
        LivingEntity livingentity = this.stalkingTarget;
        if (livingentity != null) {
            this.enderman.getLookControl().setLookAt(livingentity, 30.0F, 30.0F);
            double d0 = this.enderman.distanceToSqr(livingentity);
            double distToPlayer = Math.sqrt(d0);
            this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
            if ((this.followingTargetEvenIfNotSeen || this.enderman.getSensing().hasLineOfSight(livingentity))
                    && this.ticksUntilNextPathRecalculation <= 0
                    && (this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0
                    || livingentity.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                    || this.enderman.getRandom().nextFloat() < 0.05F)) {
                this.pathedTargetX = livingentity.getX();
                this.pathedTargetY = livingentity.getY();
                this.pathedTargetZ = livingentity.getZ();
                this.ticksUntilNextPathRecalculation = 4 + this.enderman.getRandom().nextInt(7);
                if (this.canPenalize) {
                    this.ticksUntilNextPathRecalculation = this.ticksUntilNextPathRecalculation + this.failedPathFindingPenalty;
                    if (this.enderman.getNavigation().getPath() != null) {
                        Node finalPathPoint = this.enderman.getNavigation().getPath().getEndNode();
                        if (finalPathPoint != null
                                && livingentity.distanceToSqr(finalPathPoint.x, finalPathPoint.y, finalPathPoint.z) < 1.0) {
                            this.failedPathFindingPenalty = 0;
                        } else {
                            this.failedPathFindingPenalty += 10;
                        }
                    } else {
                        this.failedPathFindingPenalty += 10;
                    }
                }

                if (d0 > 1024.0) {
                    this.ticksUntilNextPathRecalculation += 10;
                } else if (d0 > 256.0) {
                    this.ticksUntilNextPathRecalculation += 5;
                }

                if (!this.enderman.getNavigation().moveTo(livingentity, this.speedModifier)) {
                    this.ticksUntilNextPathRecalculation += 15;
                }

                this.ticksUntilNextPathRecalculation = this.reducedTickDelay(this.ticksUntilNextPathRecalculation);
            }

            // ===== STARE TRACKING =====
            if (this.enderman.inPlayerLineOfSight() && this.enderman.isPlayerLookingTowards()) {
                this.playerStareTicks++;
            } else {
                this.playerStareTicks = Math.max(0, this.playerStareTicks - 5);
            }
            if (this.playerStareTicks >= STARE_TRIGGER_TICKS) {
                this.enderman.rRollResult = 0;
                this.enderman.forcedStalk = false;
                this.playerStareTicks = 0;
            }

            // ===== DISTANCE MAINTENANCE (tug-of-war) =====
            if (distToPlayer < MIN_STALK_DISTANCE) {
                this.enderman.getNavigation().stop();
                Vec3 awayDir = this.enderman.position().subtract(livingentity.position()).normalize();
                if (awayDir.lengthSqr() < 0.01) awayDir = new Vec3(1, 0, 0);
                BlockPos retreatPos = new BlockPos(
                        (int)(this.enderman.getX() + awayDir.x * MAX_STALK_DISTANCE),
                        (int)this.enderman.getY(),
                        (int)(this.enderman.getZ() + awayDir.z * MAX_STALK_DISTANCE)
                );
                this.enderman.getNavigation().moveTo(
                        retreatPos.getX(), retreatPos.getY(), retreatPos.getZ(), this.speedModifier);
            }

            // ===== MINESHAFT / TUNNEL CLOSE STALKING =====
            this.closeStalkCooldown--;
            if (this.closeStalkCooldown <= 0) {
                this.isCloseStalking = this.enderman.isNearMineshaftOrTunnel(
                        livingentity.blockPosition(), 15);
                this.closeStalkCooldown = MINESHAFT_SCAN_INTERVAL + this.rand.nextInt(100);
            }

            // ===== DAYTIME SURFACE CHECK =====
            boolean isNight = this.enderman.level().getDayTime() % 24000 >= 12000;
            boolean playerOnSurface = livingentity.level().canSeeSky(livingentity.blockPosition());

            if (!isNight && playerOnSurface && !this.enderman.isInvisible()) {
                this.enderman.getNavigation().stop();
                BlockPos undergroundTarget = new BlockPos(
                        (int)livingentity.getX() + this.rand.nextInt(40) - 20,
                        (int)Math.min(livingentity.getY() - 10, 25),
                        (int)livingentity.getZ() + this.rand.nextInt(40) - 20
                );
                this.enderman.getNavigation().moveTo(
                        undergroundTarget.getX(), undergroundTarget.getY(), undergroundTarget.getZ(),
                        this.speedModifier * 0.6);
            }

            // ===== RUN-AWAY STALK TIMER & CORRIDOR AMBUSH =====
            Vec3 currentPlayerPos = livingentity.position();

            if (this.hasPrevPlayerPos) {
                Vec3 playerDelta = currentPlayerPos.subtract(this.prevPlayerPos);
                double playerSpeed = playerDelta.horizontalDistance();

                // --- Phase through walls when stuck (existing mechanic) ---
                this.phaseCooldown--;
                if (this.phaseCooldown <= 0 && distToPlayer > 10.0) {
                    boolean pathBlocked = this.enderman.getNavigation().isDone()
                            || this.enderman.horizontalCollision;
                    if (pathBlocked) {
                        Vec3 toPlayer = currentPlayerPos.subtract(this.enderman.position()).normalize();
                        double teleportDist = Math.min(distToPlayer * 0.4, 12.0);
                        Vec3 teleportPos = this.enderman.position().add(toPlayer.scale(teleportDist));
                        teleportPos = teleportPos.add(
                                (this.rand.nextDouble() - 0.5) * 4.0,
                                0,
                                (this.rand.nextDouble() - 0.5) * 4.0
                        );
                        this.enderman.teleportTo(teleportPos.x, this.enderman.getY(), teleportPos.z);
                        this.phaseCooldown = PHASE_COOLDOWN_TICKS + this.rand.nextInt(40);
                    }
                }

                // --- Track player running away (stalk from behind) ---
                boolean isRunningAway = distToPlayer > 20.0 && playerSpeed > 0.18;
                if (isRunningAway) {
                    Vec3 toEntity = this.enderman.position().subtract(currentPlayerPos).normalize();
                    Vec3 playerDir = playerDelta.normalize();
                    // Only count if player is actually moving away from entity
                    if (toEntity.dot(playerDir) > 0.25) {
                        this.runAwayStalkTimer++;
                    } else {
                        this.runAwayStalkTimer = Math.max(0, this.runAwayStalkTimer - 100);
                    }
                } else {
                    this.runAwayStalkTimer = Math.max(0, this.runAwayStalkTimer - 100);
                }

                // --- Check if ready to ambush (10 min running + failing to keep up) ---
                boolean failingToKeepUp = distToPlayer > 30.0 && this.enderman.getNavigation().isDone();
                if (this.runAwayStalkTimer >= RUNAWAY_STALK_TIME && failingToKeepUp && !this.readyToAmbush) {
                    this.readyToAmbush = true;
                    this.ambushCooldown = 20; // Short initial delay for wall check
                }

                // --- Corridor ambush: phase out of wall when player is in narrow passage ---
                if (this.readyToAmbush) {
                    this.ambushCooldown--;
                    if (this.ambushCooldown <= 0) {
                        Vec3 playerDir = playerDelta.normalize();
                        // Perpendicular directions (left/right of player's movement)
                        Vec3 leftDir = new Vec3(-playerDir.z, 0, playerDir.x).normalize();
                        Vec3 rightDir = new Vec3(playerDir.z, 0, -playerDir.x).normalize();

                        BlockPos selectedWall = null;
                        boolean corridorBlocked = true;

                        // Scan 20-30 blocks ahead of the player's movement direction
                        // Far enough ahead that the entity emerges before the player reaches that spot
                        for (int dist = 20; dist <= 30; dist++) {
                            BlockPos aheadPos = livingentity.blockPosition().offset(
                                    (int) Math.round(playerDir.x * dist),
                                    0,
                                    (int) Math.round(playerDir.z * dist));

                            boolean wallLeft = false;
                            boolean wallRight = false;
                            BlockPos leftWallAtDist = null;
                            BlockPos rightWallAtDist = null;

                            // Check 2-4 blocks to the left/right of the player's path
                            for (int sideDist = 2; sideDist <= 4; sideDist++) {
                                BlockPos leftCheck = aheadPos.offset(
                                        (int) Math.round(leftDir.x * sideDist),
                                        0,
                                        (int) Math.round(leftDir.z * sideDist));
                                BlockPos rightCheck = aheadPos.offset(
                                        (int) Math.round(rightDir.x * sideDist),
                                        0,
                                        (int) Math.round(rightDir.z * sideDist));

                                // Check for vertical walls at heights 1-3
                                for (int h = 1; h <= 3; h++) {
                                    if (!wallLeft && this.enderman.level().getBlockState(leftCheck.above(h)).isSolid()) {
                                        wallLeft = true;
                                        leftWallAtDist = leftCheck.above(h);
                                    }
                                    if (!wallRight && this.enderman.level().getBlockState(rightCheck.above(h)).isSolid()) {
                                        wallRight = true;
                                        rightWallAtDist = rightCheck.above(h);
                                    }
                                }
                            }

                            // If either side lacks a wall, not a corridor — can't ambush
                            if (!wallLeft || !wallRight) {
                                corridorBlocked = false;
                                break;
                            }

                            // Pick the wall closest to the entity
                            if (selectedWall == null) {
                                double distToLeft = leftWallAtDist != null
                                        ? this.enderman.distanceToSqr(Vec3.atCenterOf(leftWallAtDist))
                                        : Double.MAX_VALUE;
                                double distToRight = rightWallAtDist != null
                                        ? this.enderman.distanceToSqr(Vec3.atCenterOf(rightWallAtDist))
                                        : Double.MAX_VALUE;
                                selectedWall = distToLeft < distToRight ? leftWallAtDist : rightWallAtDist;
                            }
                        }

                        if (corridorBlocked && selectedWall != null && distToPlayer > 20.0) {
                            // Check ceiling height for emergence type
                            boolean lowCeiling = !this.enderman.level().getBlockState(selectedWall.above(2)).isAir();

                            // Calculate facing direction toward the player
                            Vec3 toPlayer = livingentity.position().subtract(Vec3.atCenterOf(selectedWall));
                            float facingYaw = (float) Math.toDegrees(Math.atan2(-toPlayer.x, toPlayer.z));

                            // Emerge from wall with squeeze animation + mold spreading
                            this.enderman.emergeFromWall(selectedWall, lowCeiling, facingYaw);

                            this.readyToAmbush = false;
                            this.runAwayStalkTimer = 0;
                            this.ambushCooldown = AMBUSH_COOLDOWN_TICKS + this.rand.nextInt(40);
                        } else {
                            // Check again in a bit
                            this.ambushCooldown = 10 + this.rand.nextInt(10);
                        }
                    }
                }
            }
            this.prevPlayerPos = currentPlayerPos;
            this.hasPrevPlayerPos = true;

            // ===== BLOCK STEALING =====
            this.ticksUntilNextSteal--;
            if (this.ticksUntilNextSteal <= 0) {
                int daysPassed = getDaysPassed();
                int dynamicCooldown = Math.max(300, STEAL_COOLDOWN_MAX - (daysPassed * 40));

                BlockPos playerPos = livingentity.blockPosition();
                if (!livingentity.hasLineOfSight(this.enderman)) {
                    this.enderman.stealBlockNear(playerPos, 5);
                    this.ticksUntilNextSteal = dynamicCooldown + this.rand.nextInt(400);
                } else {
                    this.ticksUntilNextSteal = 200 + this.rand.nextInt(200);
                }
            }

            // ===== STRUCTURE BUILDING =====
            this.ticksUntilNextAltar--;
            if (this.ticksUntilNextAltar <= 0) {
                BlockPos playerPos = livingentity.blockPosition();
                if (!livingentity.hasLineOfSight(this.enderman) && isOldEnoughForStructures()) {
                    if (this.enderman.buildStructure(playerPos)) {
                        this.ticksUntilNextAltar = STRUCTURE_COOLDOWN_TICKS + this.rand.nextInt(12000);
                    } else {
                        this.ticksUntilNextAltar = 6000 + this.rand.nextInt(6000);
                    }
                } else {
                    this.ticksUntilNextAltar = 6000 + this.rand.nextInt(6000);
                }
            }
        }

    }
}
