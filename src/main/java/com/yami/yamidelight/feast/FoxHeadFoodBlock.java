package com.yami.yamidelight.feast;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.sounds.SoundSource;
import vectorwing.farmersdelight.common.registry.ModEffects;
import vectorwing.farmersdelight.common.registry.ModSounds;

public final class FoxHeadFoodBlock extends BaseEntityBlock {
    public static final MapCodec<FoxHeadFoodBlock> CODEC = simpleCodec(FoxHeadFoodBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.level.block.state.properties.AttachFace> ATTACH_FACE = BlockStateProperties.ATTACH_FACE;
    public FoxHeadFoodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(ATTACH_FACE,net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING,ATTACH_FACE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var face=switch(context.getClickedFace()) {
            case UP -> net.minecraft.world.level.block.state.properties.AttachFace.FLOOR;
            case DOWN -> net.minecraft.world.level.block.state.properties.AttachFace.CEILING;
            default -> net.minecraft.world.level.block.state.properties.AttachFace.WALL;
        };
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(ATTACH_FACE,face);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(2, 0, 2, 14, 15, 14);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }
    // Retain full physical collision without connecting fences, walls or glass panes to the model.
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FoxHeadFoodBlockEntity(pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof FoxHeadFoodBlockEntity food) food.setItem(stack);
    }
    private InteractionResult interact(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof FoxHeadFoodBlockEntity food)) return InteractionResult.PASS;
        ItemStack stack = food.item();
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) { food.discard(); level.removeBlock(pos, false); popResource(level, pos, stack); }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!stack.is(FeastContent.ROASTED_AGED_FOX_BRAIN_ITEM.get())) return InteractionResult.PASS;
        int stage = FoxHeadFoodData.stage(stack);
        if (stage < 4 && !player.canEat(false)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.playSound(null, pos, ModSounds.BLOCK_FOOD_TAKE_PORTION.get(), SoundSource.BLOCKS, 1, 1);
            if (stage == 4) {
                ItemStack bowl = FoxHeadFoodData.create(FeastContent.FOX_BRAIN_BOWL_ITEM.get(), stack, level.registryAccess());
                food.discard(); level.removeBlock(pos, false); popResource(level, pos, bowl);
            } else {
                player.getFoodData().eat(8, .5F);
                player.addEffect(new MobEffectInstance(ModEffects.COMFORT, 20 * 120));
                food.advance();
                level.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.EAT, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = interact(level, pos, player);
        return result.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return List.of(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof FoxHeadFoodBlockEntity food)
            popResource(level, pos, food.item());
        super.onRemove(state, level, pos, next, moving);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isCreative() && level.getBlockEntity(pos) instanceof FoxHeadFoodBlockEntity food) food.discard();
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override public ItemStack getCloneItemStack(BlockState state, HitResult hit, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof FoxHeadFoodBlockEntity food ? food.item() : new ItemStack(this);
    }
}
