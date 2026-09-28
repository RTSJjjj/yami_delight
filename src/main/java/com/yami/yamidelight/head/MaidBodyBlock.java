package com.yami.yamidelight.head;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.phys.BlockHitResult;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;


/** Six-face decorative mount. Loot is emitted once from onRemove with the saved appearance/state. */
public final class MaidBodyBlock extends BaseEntityBlock {
    public static final MapCodec<MaidBodyBlock> CODEC = simpleCodec(MaidBodyBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    
    private static final double REACH = 8.0D;
    
    private static final double SIDE_LOW = 1.0D, SIDE_HIGH = 15.0D;
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    public MaidBodyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    
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

    
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Keep the old block id for existing saves, but never create another frame-style headless body.
        if (this == MaidHeadContent.MAID_BODY.get()) return null;
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    
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
        return new MaidBodyBlockEntity(pos, state);
    }

    
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof MaidBodyBlockEntity body) {
            popResource(level, pos, body.asItem());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof MaidBodyBlockEntity body) {
            return body.asItem();
        }
        return new ItemStack(this);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MaidBodyBlockEntity body) {
            body.loadItem(stack);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        // Empty main hand is dispatched once by useWithoutItem; empty offhand still reaches the hook.
        if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return switch (interact(level, pos, player, hand, hit)) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            case PASS -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return interact(level, pos, player, InteractionHand.MAIN_HAND, hit);
    }

    private InteractionResult interact(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MaidBodyBlockEntity body) {
            return MaidBodyInteractions.interact(new MaidBodyInteractions.Context(body, player, hand, hit));
        }
        return InteractionResult.PASS;
    }
}
