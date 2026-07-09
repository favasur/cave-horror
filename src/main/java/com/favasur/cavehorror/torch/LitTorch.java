package com.favasur.cavehorror.torch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LitTorch extends Block implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> ATTACH_FACE = BlockStateProperties.ATTACH_FACE;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public int lightLevel;
    public SimpleParticleType flameParticles;

    public LitTorch(Properties properties, SimpleParticleType flameParticles, int lightLevel) {
        super(properties);
        this.lightLevel = 14;
        this.flameParticles = ParticleTypes.FLAME;
        this.flameParticles = flameParticles;
        this.lightLevel = lightLevel;
        // Register default state - stateDefinition is built by super() after createBlockStateDefinition
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ATTACH_FACE, AttachFace.FLOOR)
                .setValue(LIT, true)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
        builder.add(ATTACH_FACE);
        builder.add(LIT);
        builder.add(WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        AttachFace face = state.getValue(ATTACH_FACE);

        return switch (face) {
            case FLOOR -> Block.box(6.0, 0.0, 6.0, 10.0, 10.0, 10.0);
            case WALL -> {
                float xOff = 0.0F, zOff = 0.0F;
                switch (facing) {
                    case EAST -> xOff = 6.0F;
                    case WEST -> xOff = -6.0F;
                    case SOUTH -> zOff = 6.0F;
                    case NORTH -> zOff = -6.0F;
                }
                yield Block.box(6.0 + xOff, 3.0, 6.0 + zOff, 10.0 + xOff, 13.0, 10.0 + zOff);
            }
            case CEILING -> Block.box(6.0, 8.0, 6.0, 10.0, 16.0, 10.0);
        };
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIT) ? this.lightLevel : 0;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends Block> codec() {
        return null;
    }

    private static AttachFace getAttachFaceFromDir(Direction dir) {
        return switch (dir) {
            case DOWN -> AttachFace.CEILING;
            case UP -> AttachFace.FLOOR;
            default -> AttachFace.WALL;
        };
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();
        boolean isWaterlogged = level.getFluidState(clickedPos).is(Fluids.WATER);

        AttachFace attachFace = getAttachFaceFromDir(clickedFace);

        Direction facing;
        if (attachFace == AttachFace.WALL) {
            facing = clickedFace.getOpposite();
        } else {
            facing = context.getHorizontalDirection().getOpposite();
        }

        // Ceiling placement not allowed
        if (attachFace == AttachFace.CEILING) return null;

        level.scheduleTick(clickedPos, this, 1);

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(ATTACH_FACE, attachFace)
                .setValue(LIT, ModTorchConfig.PLACE_LIT.getAsBoolean() && !isWaterlogged)
                .setValue(WATERLOGGED, isWaterlogged);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;

        Vec3i offset;
        double extraY;

        if (state.getValue(ATTACH_FACE) == AttachFace.WALL) {
            Direction facing = state.getValue(FACING);
            offset = facing.getNormal();
            extraY = 0.2;
        } else {
            offset = new Vec3i(0, 0, 0);
            extraY = 0.0;
        }

        double d0 = (double) pos.getX() + 0.5 - (double) offset.getX() * 0.3;
        double d1 = (double) pos.getY() + 0.7 + extraY;
        double d2 = (double) pos.getZ() + 0.5 - (double) offset.getZ() * 0.3;

        level.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0, 0.0, 0.0);
        level.addParticle(this.flameParticles, d0, d1, d2, 0.0, 0.0, 0.0);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        double burnoutChance = ModTorchConfig.BURNOUT_CHANCE.get();

        if (state.getValue(LIT) && random.nextDouble() < burnoutChance) {
            if (ModTorchConfig.BURNED_TORCHES.get()) {
                level.setBlock(pos, state.setValue(LIT, false), 3);
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS);
        }

        super.randomTick(state, level, pos, random);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand,
                                               BlockHitResult hitResult) {
        if (!ModTorchConfig.CAN_RELIGHT.get() || state.getValue(WATERLOGGED)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        if (!state.getValue(LIT) && stack.getItem() == Items.FLINT_AND_STEEL) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(LIT, true), 3);
                stack.hurtAndBreak(1, player, stack.getEquipmentSlot());
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                 BlockPos fromPos, boolean isMoving) {
        if (state.getValue(WATERLOGGED) && state.getValue(LIT)) {
            level.scheduleTick(pos, this, 1);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return true;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED) && state.getValue(LIT)) {
            level.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            level.setBlock(pos, level.getBlockState(pos).setValue(LIT, false), 3);
            level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS);
        }
        super.tick(state, level, pos, random);
    }
}
