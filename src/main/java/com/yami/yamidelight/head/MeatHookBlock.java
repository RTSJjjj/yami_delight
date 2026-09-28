package com.yami.yamidelight.head;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MeatHookBlock extends BaseEntityBlock {
    public static final MapCodec<MeatHookBlock> CODEC = simpleCodec(MeatHookBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");
    public MeatHookBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OCCUPIED, false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MeatHookBlockEntity(pos, state); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, OCCUPIED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.DOWN) return null;
        var state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.UP && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Both groups occupy this cell; A's top touches the ceiling at y=16 pixels.
        return switch (state.getValue(FACING)) {
            case EAST -> Block.box(5, 8, 5.5, 11, 16, 10.5);
            case SOUTH -> Block.box(5.5, 8, 5, 10.5, 16, 11);
            case WEST -> Block.box(5, 8, 5.5, 11, 16, 10.5);
            default -> Block.box(5.5, 8, 5, 10.5, 16, 11);
        };
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return List.of(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof MeatHookBlockEntity hook && hook.hasBody()) popResource(level, pos, hook.asItem());
            popResource(level, pos, new ItemStack(MaidHeadContent.MEAT_HOOK_ITEM.get()));
        }
        super.onRemove(state, level, pos, next, moving);
    }
    public static InteractionResult mount(Level level, BlockPos pos, Player player, ItemStack held, BlockHitResult hit) {
        int step = DissectionTableBlockEntity.dissectionStep(held);
        // B is step 1; every state of C is step 2 through 12.  A completed processed maid
        // is also a valid hanging item and carries the original maid data.
        if (!((held.is(MaidHeadContent.MAID_BODY_ITEM.get())
                && step >= 1 && step <= DissectionTableBlockEntity.AXE_START_STEP)
                || held.is(MaidHeadContent.PROCESSED_MAID.get()))) return InteractionResult.FAIL;
        if (!player.mayBuild() || !player.mayUseItemAt(pos, hit.getDirection(), held)
                || !(level.getBlockEntity(pos) instanceof MeatHookBlockEntity hook) || hook.hasBody()) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            hook.mount(held);
            held.consume(1, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private InteractionResult interact(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(MaidHeadContent.MAID_BODY_ITEM.get()) || held.is(MaidHeadContent.PROCESSED_MAID.get())) return mount(level, pos, player, held, hit);
        if (!(level.getBlockEntity(pos) instanceof MeatHookBlockEntity hook) || !hook.hasBody()) return InteractionResult.PASS;
        var result = MaidBodyInteractions.interact(new MaidBodyInteractions.Context(hook, player, hand, hit));
        if (result != InteractionResult.PASS) return result;
        if (player.isShiftKeyDown() && held.isEmpty() && player.mayBuild()) {
            if (!level.isClientSide) {
                ItemStack body = hook.takeBody();
                if (!player.getInventory().add(body)) player.drop(body, false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return switch (interact(level, pos, player, hand, hit)) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            case PASS -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        };
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player, InteractionHand.MAIN_HAND, hit);
    }
}
