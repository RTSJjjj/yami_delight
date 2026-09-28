package com.yami.yamidelight.feast;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.item.Item;
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

/**
 * A two-block Farmer's Delight feast.  Its eleven servings are removed with sticks in batches, then
 * the remaining tray can be cleared by any right click to restore its bowl, bone and exact winefox head.
 */
public final class SeaLandWindWinefoxBlock extends FeastBlock implements EntityBlock {
    public static final MapCodec<SeaLandWindWinefoxBlock> CODEC = simpleCodec(SeaLandWindWinefoxBlock::new);
    public static final IntegerProperty SERVINGS = IntegerProperty.create("servings", 0, 11);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);
    private static final ThreadLocal<Set<BlockPos>> PAIR_REMOVALS = ThreadLocal.withInitial(HashSet::new);

    private record Serving(Item item, int sticks, int count) {}

    public SeaLandWindWinefoxBlock(Properties properties) {
        super(properties, () -> FeastContent.SEA_LAND_WIND_WINEFOX_LEG_SKEWER.get(), true, false);
        registerDefaultState(defaultBlockState().setValue(PART, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public IntegerProperty getServingsProperty() {
        return SERVINGS;
    }

    @Override
    public int getMaxServings() {
        return 11;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SERVINGS, PART);
    }

    /** Servings are indexed by the stage currently visible, before the next interaction advances it. */
    private static Serving serving(BlockState state) {
        return switch (state.getValue(SERVINGS)) {
            case 11, 6 -> new Serving(FeastContent.SEA_LAND_WIND_WINEFOX_LEG_SKEWER.get(), 5, 5);
            case 10, 5 -> new Serving(FeastContent.SEA_LAND_WIND_WINEFOX_ARM_SKEWER.get(), 3, 3);
            case 1 -> new Serving(FeastContent.SEA_LAND_WIND_FRUIT_WINEFOX_HOLE_SKEWER.get(), 1, 1);
            default -> new Serving(FeastContent.SEA_LAND_WIND_WINEFOX_SKEWER.get(), 3, 3);
        };
    }

    @Override
    public ItemStack getServingItem(BlockState state) {
        Serving serving = serving(state);
        return new ItemStack(serving.item(), serving.count());
    }

    public static BlockPos primary(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? pos : pos.relative(state.getValue(FACING).getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new SeaLandWindWinefoxBlockEntity(pos, state) : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private static boolean fullSupport(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isCollisionShapeFullBlock(level, below);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return fullSupport(level, pos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return null;
        }
        // The authored G mesh has its legs on the end opposite FACING, the same convention as the
        // existing stuffed winefox.  getHorizontalDirection keeps that leg end facing the placer.
        Direction facing = context.getHorizontalDirection();
        BlockPos pos = context.getClickedPos();
        BlockPos other = pos.relative(facing);
        if (!fullSupport(context.getLevel(), pos) || !fullSupport(context.getLevel(), other)
                || !context.getLevel().getWorldBorder().isWithinBounds(other)
                || !context.getLevel().getBlockState(other).canBeReplaced(context)
                || (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(other, Direction.UP, context.getItemInHand()))) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof SeaLandWindWinefoxBlockEntity feast) {
            // The cooking recipe keeps a complete one-count copy of the original head on the placed item.
            // This preserves its selected model, expression marker, custom name and any other components.
            ItemStack head = SeaLandWindWinefoxCookingRecipe.storedHead(stack, level.registryAccess());
            if (!head.isEmpty()) {
                feast.setHead(head);
            }
        }
        level.setBlock(pos.relative(state.getValue(FACING)), state.setValue(PART, 1), UPDATE_ALL);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.getAbilities().instabuild) {
            BlockPos root = primary(pos, state);
            if (level.getBlockEntity(root) instanceof SeaLandWindWinefoxBlockEntity feast) {
                feast.discardHead();
            }
            if (state.getValue(PART) == 1 && level.getBlockState(root).is(this)) {
                level.removeBlock(root, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !fullSupport(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        Direction pairDirection = state.getValue(PART) == 0 ? state.getValue(FACING) : state.getValue(FACING).getOpposite();
        if (direction == pairDirection && (!neighbor.is(this) || neighbor.getValue(PART).equals(state.getValue(PART))
                || neighbor.getValue(FACING) != state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide) {
            BlockPos root = primary(pos, state);
            if (state.getValue(PART) == 0) {
                if (level.getBlockEntity(pos) instanceof SeaLandWindWinefoxBlockEntity feast) {
                    popResource(level, pos, feast.takeHead());
                }
                BlockPos other = pos.relative(state.getValue(FACING));
                BlockState paired = level.getBlockState(other);
                if (!isClearing(pos) && paired.is(this) && paired.getValue(PART) == 1
                        && paired.getValue(FACING) == state.getValue(FACING)) {
                    clearPair(level, pos, other);
                }
            } else if (!isClearing(pos)) {
                BlockState paired = level.getBlockState(root);
                if (paired.is(this) && paired.getValue(PART) == 0 && paired.getValue(FACING) == state.getValue(FACING)) {
                    clearThenDestroyPrimary(level, root, pos);
                }
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    private static boolean isClearing(BlockPos pos) {
        return PAIR_REMOVALS.get().contains(pos);
    }

    private static void clearPair(Level level, BlockPos primary, BlockPos secondary) {
        Set<BlockPos> clearing = PAIR_REMOVALS.get();
        clearing.add(primary);
        clearing.add(secondary);
        try {
            level.removeBlock(secondary, false);
        } finally {
            clearing.remove(primary);
            clearing.remove(secondary);
        }
    }

    private static void clearThenDestroyPrimary(Level level, BlockPos primary, BlockPos secondary) {
        Set<BlockPos> clearing = PAIR_REMOVALS.get();
        clearing.add(primary);
        clearing.add(secondary);
        try {
            level.destroyBlock(primary, true);
        } finally {
            clearing.remove(primary);
            clearing.remove(secondary);
        }
    }

    private static void clearFinishedFeast(Level level, BlockPos primary, SeaLandWindWinefoxBlockEntity feast) {
        popResource(level, primary, new ItemStack(Items.BOWL));
        popResource(level, primary, new ItemStack(Items.BONE));
        popResource(level, primary, feast.takeHead());
        level.removeBlock(primary, false);
    }

    private ItemInteractionResult interact(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockState clicked = level.getBlockState(pos);
        if (!clicked.is(this)) {
            return ItemInteractionResult.FAIL;
        }
        BlockPos root = primary(pos, clicked);
        BlockState state = level.getBlockState(root);
        ItemStack held = player.getItemInHand(hand);
        if (!state.is(this) || !player.mayBuild() || !player.mayUseItemAt(root, hit.getDirection(), held)
                || !(level.getBlockEntity(root) instanceof SeaLandWindWinefoxBlockEntity feast)) {
            return ItemInteractionResult.FAIL;
        }
        // The final tray intentionally accepts an empty hand or any held item before checking for sticks.
        if (state.getValue(SERVINGS) == 0) {
            if (!level.isClientSide) {
                level.playSound(null, root, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, .8F, .8F);
                clearFinishedFeast(level, root, feast);
            }
            return ItemInteractionResult.SUCCESS;
        }
        Serving serving = serving(state);
        if (!held.is(Items.STICK) || (!player.getAbilities().instabuild && held.getCount() < serving.sticks())) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.yamidelight.sea_land_wind_winefox.need_sticks"), true);
            }
            return ItemInteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            held.consume(serving.sticks(), player);
            BlockState next = state.setValue(SERVINGS, state.getValue(SERVINGS) - 1);
            level.setBlock(root, next, UPDATE_ALL);
            BlockPos other = root.relative(state.getValue(FACING));
            BlockState paired = level.getBlockState(other);
            if (paired.is(this) && paired.getValue(PART) == 1 && paired.getValue(FACING) == state.getValue(FACING)) {
                level.setBlock(other, next.setValue(PART, 1), UPDATE_ALL);
            }
            player.awardStat(Stats.ITEM_USED.get(Items.STICK));
            popResource(level, root, new ItemStack(serving.item(), serving.count()));
            level.playSound(null, root, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, .8F, .8F);
            level.updateNeighbourForOutputSignal(root, this);
            level.updateNeighbourForOutputSignal(other, this);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return interact(level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hit) {
        if (state.getValue(SERVINGS) > 0 && !player.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
            return InteractionResult.PASS;
        }
        ItemInteractionResult result = interact(level, pos, player, InteractionHand.MAIN_HAND, hit);
        return result.consumesAction() ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0, 0, 0, 16, state.getValue(SERVINGS) == 0 ? 4 : 12, 16);
    }
}
