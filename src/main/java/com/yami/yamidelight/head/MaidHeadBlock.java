package com.yami.yamidelight.head;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A head mounted on the face of another block, placed the way an item frame is: right click the side of a
 * block with a head in hand and the head appears on that side.
 *
 * <p>{@link #FACING} points away from the wall, so it is both the outward normal of the face the head is
 * pinned to and the direction the head looks. The shape below is derived from it without asking any
 * client, which is why the facing lives in the block state and not in the block entity.
 *
 * <p>Nothing here draws anything: {@link RenderShape#ENTITYBLOCK_ANIMATED} hands the whole block over to
 * the client renderer, because the geometry is the maid's own model and only a client that has that
 * model can draw it.
 */
public final class MaidHeadBlock extends BaseEntityBlock {
    public static final MapCodec<MaidHeadBlock> CODEC = simpleCodec(MaidHeadBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** Ground yaw is independent of the attachment face used for support checks. */
    public static final DirectionProperty HORIZONTAL_FACING = DirectionProperty.create("horizontal_facing", Direction.Plane.HORIZONTAL);
    /** How far the clickable part of a head reaches out of the wall, in pixels. */
    private static final double REACH = 8.0D;
    /** The head is roughly half a block wide, so the box around it is too. */
    private static final double SIDE_LOW = 4.0D, SIDE_HIGH = 12.0D;
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    public MaidHeadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    /**
     * The mounting face is the plane at 0 or at 16 pixels depending on which side of the cell the wall
     * ended up on, so each direction gets its own box rather than one box mirrored at render time.
     */
    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.NORTH, Block.box(SIDE_LOW, SIDE_LOW, 16.0D - REACH, SIDE_HIGH, SIDE_HIGH, 16.0D));
        shapes.put(Direction.SOUTH, Block.box(SIDE_LOW, SIDE_LOW, 0.0D, SIDE_HIGH, SIDE_HIGH, REACH));
        shapes.put(Direction.WEST, Block.box(16.0D - REACH, SIDE_LOW, SIDE_LOW, 16.0D, SIDE_HIGH, SIDE_HIGH));
        shapes.put(Direction.EAST, Block.box(0.0D, SIDE_LOW, SIDE_LOW, REACH, SIDE_HIGH, SIDE_HIGH));
        shapes.put(Direction.UP, Block.box(SIDE_LOW, 0.0D, SIDE_LOW, SIDE_HIGH, REACH, SIDE_HIGH));
        shapes.put(Direction.DOWN, Block.box(SIDE_LOW, 16.0D - REACH, SIDE_LOW, SIDE_HIGH, 16.0D, SIDE_HIGH));
        return shapes;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Keep a full block of physical collision, independently of the mount's selection outline. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** Physical collision does not make this decorative model a sturdy connection surface. */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace())
                .setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    /** A head only stays up while the block it was pinned to is still there and still sturdy. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MaidHeadBlockEntity(pos, state);
    }

    /** The head is dropped from {@link #onRemove} as the exact stack it was; there is no loot table. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof MaidHeadBlockEntity head) {
            popResource(level, pos, head.asItem());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Middle click gives back the head of the maid that is hanging there, not a blank one. This is the
     * pick block path NeoForge routes through; the plain {@code (LevelReader, BlockPos, BlockState)}
     * overload carries the whole level in a parameter order that reads backwards and is deprecated.
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof MaidHeadBlockEntity head) {
            return head.asItem();
        }
        return new ItemStack(this);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MaidHeadBlockEntity head) {
            head.setItem(stack);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HORIZONTAL_FACING);
    }
}
