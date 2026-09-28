package com.yami.yamidelight.feast;

import com.mojang.serialization.MapCodec;
import com.yami.yamidelight.head.MaidHeadContent;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import vectorwing.farmersdelight.common.block.FeastBlock;

/** FD feast semantics with twelve bowl portions and two linked cells. Stage 7 is intentionally absent. */
public final class StuffedWinefoxBlock extends FeastBlock implements EntityBlock {
    public static final MapCodec<StuffedWinefoxBlock> CODEC = simpleCodec(StuffedWinefoxBlock::new);
    public static final IntegerProperty SERVINGS = IntegerProperty.create("servings", 0, 12);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);
    /** Stops the linked half from recursively destroying the primary while it is being cleared. */
    private static final ThreadLocal<Set<BlockPos>> PAIR_REMOVALS = ThreadLocal.withInitial(HashSet::new);
    public StuffedWinefoxBlock(Properties properties) {
        // DeferredItem<ConsumableItem> is not covariant with FeastBlock's Supplier<Item> parameter.
        super(properties, () -> FeastContent.STUFFED_WINEFOX_PLATE.get(), true, false);
        registerDefaultState(defaultBlockState().setValue(PART, 0));
    }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override public IntegerProperty getServingsProperty() { return SERVINGS; }
    @Override public int getMaxServings() { return 12; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, SERVINGS, PART); }
    @Override public ItemStack getServingItem(BlockState state) {
        return new ItemStack(switch (state.getValue(SERVINGS)) {
            case 12 -> FeastContent.FOX_CARROT_RICE.get(); // full -> stage_0
            case 11 -> FeastContent.FOX_UTERUS_RICE.get(); // stage_0 -> stage_1
            case 10, 9, 8, 7 -> FeastContent.STUFFED_WINEFOX_HAM_PLATE.get(); // stages_2..5
            case 6 -> FeastContent.FOX_INTIMATE_RICE.get(); // stage_5 -> stage_6
            default -> FeastContent.STUFFED_WINEFOX_PLATE.get(); // stages_8..12 (five portions)
        });
    }
    public static BlockPos primary(BlockPos pos, BlockState state) { return state.getValue(PART) == 0 ? pos : pos.relative(state.getValue(FACING).getOpposite()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return state.getValue(PART) == 0 ? new StuffedWinefoxBlockEntity(pos, state) : null; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    private static boolean fullSupport(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isCollisionShapeFullBlock(level, below);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return fullSupport(level, pos); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) return null;
        // The source model's legs point opposite FACING. Keeping FACING aligned with the player
        // therefore places the legs on the player-facing end of the two-block feast.
        Direction facing = context.getHorizontalDirection();
        BlockPos pos = context.getClickedPos(), other = pos.relative(facing);
        if (!fullSupport(context.getLevel(), pos) || !fullSupport(context.getLevel(), other)
                || !context.getLevel().getWorldBorder().isWithinBounds(other)
                || !context.getLevel().getBlockState(other).canBeReplaced(context)
                || (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(other, Direction.UP, context.getItemInHand()))) return null;
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) level.setBlock(pos.relative(state.getValue(FACING)), state.setValue(PART, 1), UPDATE_ALL);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.getAbilities().instabuild) {
            BlockPos root = primary(pos, state);
            if (level.getBlockEntity(root) instanceof StuffedWinefoxBlockEntity feast) feast.discardHead();
            // The normal creative path removes the clicked block without loot. When the clicked
            // half is the secondary one, remove the primary in the same no-loot path as well.
            if (state.getValue(PART) == 1 && level.getBlockState(root).is(this)) level.removeBlock(root, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !fullSupport(level, pos)) return Blocks.AIR.defaultBlockState();
        Direction pairDirection = state.getValue(PART) == 0 ? state.getValue(FACING) : state.getValue(FACING).getOpposite();
        if (direction == pairDirection && (!neighbor.is(this) || neighbor.getValue(PART).equals(state.getValue(PART))
                || neighbor.getValue(FACING) != state.getValue(FACING))) return Blocks.AIR.defaultBlockState();
        return state;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide) {
            BlockPos root = primary(pos, state);
            if (state.getValue(PART) == 0) {
                if (level.getBlockEntity(pos) instanceof StuffedWinefoxBlockEntity feast) popResource(level, pos, feast.takeHead());
                BlockPos other = pos.relative(state.getValue(FACING));
                BlockState paired = level.getBlockState(other);
                if (!isClearing(pos) && paired.is(this) && paired.getValue(PART) == 1
                        && paired.getValue(FACING) == state.getValue(FACING)) clearPair(level, pos, other);
            } else if (!isClearing(pos)) {
                BlockState paired = level.getBlockState(root);
                if (paired.is(this) && paired.getValue(PART) == 0 && paired.getValue(FACING) == state.getValue(FACING)) {
                    clearThenDestroyPrimary(level, root, pos);
                }
            }
        }
        // FeastBlock is not a BaseEntityBlock, but Block's onRemove still removes its EntityBlock BE.
        super.onRemove(state, level, pos, next, moving);
    }
    private static boolean isClearing(BlockPos pos) { return PAIR_REMOVALS.get().contains(pos); }
    private static void clearPair(Level level, BlockPos primary, BlockPos secondary) {
        Set<BlockPos> clearing = PAIR_REMOVALS.get();
        clearing.add(primary); clearing.add(secondary);
        try { level.removeBlock(secondary, false); }
        finally { clearing.remove(primary); clearing.remove(secondary); }
    }
    private static void clearThenDestroyPrimary(Level level, BlockPos primary, BlockPos secondary) {
        Set<BlockPos> clearing = PAIR_REMOVALS.get();
        clearing.add(primary); clearing.add(secondary);
        try { level.destroyBlock(primary, true); }
        finally { clearing.remove(primary); clearing.remove(secondary); }
    }
    /**
     * Clearing an empty tray is an interaction, not a creative-mode block break. Refund the
     * tray contents explicitly, then remove without loot so the linked half cannot consume the
     * primary block entity or duplicate the static feast loot table.
     */
    private static void clearFinishedFeast(Level level, BlockPos primary, StuffedWinefoxBlockEntity feast) {
        popResource(level, primary, new ItemStack(Items.BOWL));
        popResource(level, primary, new ItemStack(Items.BONE));
        popResource(level, primary, feast.takeHead());
        level.removeBlock(primary, false);
    }
    private ItemInteractionResult interact(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockState clicked = level.getBlockState(pos);
        if (!clicked.is(this)) return ItemInteractionResult.FAIL;
        BlockPos root = primary(pos, clicked);
        BlockState state = level.getBlockState(root);
        ItemStack held = player.getItemInHand(hand);
        if (!state.is(this) || !player.mayBuild() || !player.mayUseItemAt(root, hit.getDirection(), held)
                || !(level.getBlockEntity(root) instanceof StuffedWinefoxBlockEntity feast)) return ItemInteractionResult.FAIL;
        // Leftovers take priority even over another head or an arbitrary held item.
        if (state.getValue(SERVINGS) == 0) {
            if (!level.isClientSide) {
                level.playSound(null, root, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, .8F, .8F);
                clearFinishedFeast(level, root, feast);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (held.is(MaidHeadContent.MAID_HEAD_ITEM.get())) {
            if (feast.hasHead()) return ItemInteractionResult.FAIL;
            if (!level.isClientSide) { feast.insertHead(held); held.consume(1, player); }
            return ItemInteractionResult.SUCCESS;
        }
        return takeServing(level, root, state, player, hand);
    }
    @Override protected ItemInteractionResult takeServing(LevelAccessor access, BlockPos pos, BlockState state, Player player, InteractionHand hand) {
        ItemStack bowl = player.getItemInHand(hand);
        if (!bowl.is(Items.BOWL)) {
            if (!access.isClientSide()) player.displayClientMessage(Component.translatable("message.yamidelight.stuffed_winefox.need_bowl"), true);
            return ItemInteractionResult.FAIL;
        }
        if (access instanceof Level level && !level.isClientSide) {
            int servings = state.getValue(SERVINGS);
            if (servings <= 0) return ItemInteractionResult.FAIL;
            ItemStack serving = getServingItem(state);
            BlockState next = state.setValue(SERVINGS, servings - 1);
            level.setBlock(pos, next, UPDATE_ALL);
            BlockPos other = pos.relative(state.getValue(FACING));
            BlockState paired = level.getBlockState(other);
            if (paired.is(this) && paired.getValue(PART) == 1 && paired.getValue(FACING) == state.getValue(FACING)) level.setBlock(other, next.setValue(PART, 1), UPDATE_ALL);
            bowl.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(Items.BOWL));
            popResource(level, pos, serving);
            level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, .8F, .8F);
            level.updateNeighbourForOutputSignal(pos, this);
            level.updateNeighbourForOutputSignal(other, this);
        }
        return ItemInteractionResult.SUCCESS;
    }
    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return interact(level, pos, player, hand, hit);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(SERVINGS) > 0 && !player.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
            // Do not show a false “need bowl” message before Minecraft reaches a bowl or head held
            // in the off hand. Leftovers deliberately stay in the main-hand path so either hand can
            // clear the final tray.
            return InteractionResult.PASS;
        }
        ItemInteractionResult result = interact(level, pos, player, InteractionHand.MAIN_HAND, hit);
        // Let Minecraft continue with the off hand when the empty main hand cannot take a
        // serving. This keeps both a bowl and a winefox head usable from the off hand.
        return result.consumesAction() ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }
    public InteractionResult useHead(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level, pos, player, hand, hit).consumesAction() ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.FAIL;
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(0, 0, 0, 16, state.getValue(SERVINGS) == 0 ? 4 : 12, 16); }
}
