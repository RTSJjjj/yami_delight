package com.yami.yamidelight.head;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class DissectionTableBlock extends BaseEntityBlock {
    public static final MapCodec<DissectionTableBlock> CODEC = simpleCodec(DissectionTableBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    public DissectionTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 1));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, PART); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return s.getValue(PART) == 1 ? new DissectionTableBlockEntity(p,s) : null; }
    public static BlockPos center(BlockPos p, BlockState s) { return p.relative(s.getValue(FACING), s.getValue(PART)-1); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction facing = c.getHorizontalDirection().getOpposite();
        for (int d : new int[]{-1,1}) {
            BlockPos p = c.getClickedPos().relative(facing,d);
            if (!c.getLevel().getWorldBorder().isWithinBounds(p) || !c.getLevel().getBlockState(p).canBeReplaced(c)
                    || (c.getPlayer()!=null && !c.getPlayer().mayUseItemAt(p,c.getClickedFace(),c.getItemInHand()))) return null;
        }
        return defaultBlockState().setValue(FACING,facing);
    }
    @Override public void setPlacedBy(Level l, BlockPos p, BlockState s, LivingEntity entity, ItemStack item) {
        if (!l.isClientSide) for(int part : new int[]{0,2}) l.setBlock(p.relative(s.getValue(FACING),1-part),s.setValue(PART,part),UPDATE_ALL);
    }
    @Override protected List<ItemStack> getDrops(BlockState s, LootParams.Builder p) { return List.of(); }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && !l.isClientSide) {
            BlockPos c=center(p,s);
            if(s.getValue(PART)==1) {
                if(l.getBlockEntity(p) instanceof DissectionTableBlockEntity table) {
                    // asItem marks an in-flight two-tick cut as complete.  Keep its tail yield too.
                    if (table.cutting()) popResource(l, p, new ItemStack(MaidHeadContent.WINE_FOX_TAIL.get()));
                    popResource(l,p,table.asItem());
                }
                popResource(l,p,new ItemStack(MaidHeadContent.DISSECTION_TABLE_ITEM.get()));
                for(int part:new int[]{0,2}) {
                    BlockPos side=p.relative(s.getValue(FACING),1-part); BlockState other=l.getBlockState(side);
                    if(other.is(this)&&other.getValue(FACING)==s.getValue(FACING)&&other.getValue(PART)==part) l.setBlock(side,Blocks.AIR.defaultBlockState(),UPDATE_ALL);
                }
            } else {
                BlockState middle=l.getBlockState(c);
                if(middle.is(this)&&middle.getValue(PART)==1&&middle.getValue(FACING)==s.getValue(FACING)) l.destroyBlock(c,true);
            }
        }
        super.onRemove(s,l,p,next,moving);
    }
    public static InteractionResult mount(Level l, BlockPos p, Player player, ItemStack stack, BlockHitResult hit) {
        BlockState s=l.getBlockState(p);
        if(!(s.getBlock() instanceof DissectionTableBlock)) return InteractionResult.PASS;
        BlockPos c=center(p,s);
        if(!player.mayBuild()||!player.mayUseItemAt(c,hit.getDirection(),stack)
                || !(l.getBlockEntity(c) instanceof DissectionTableBlockEntity table)||table.occupied()) return InteractionResult.FAIL;
        if(!isTableMountItem(stack)) return InteractionResult.PASS;
        if(!l.isClientSide){table.mount(stack,player);stack.consume(1,player);}
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    /** Mounts a data-bearing head in the authored Z_STAGE0 position. */
    public static InteractionResult mountHead(Level l, BlockPos p, Player player, ItemStack stack, BlockHitResult hit) {
        BlockState state = l.getBlockState(p);
        if (!(state.getBlock() instanceof DissectionTableBlock)) return InteractionResult.PASS;
        BlockPos center = center(p, state);
        if (!player.mayBuild() || !player.mayUseItemAt(center, hit.getDirection(), stack)
                || !(l.getBlockEntity(center) instanceof DissectionTableBlockEntity table) || table.occupied()) return InteractionResult.FAIL;
        if (!MaidHeadContent.isHeadItem(stack)) return InteractionResult.PASS;
        if (!l.isClientSide) {
            table.mountHead(stack);
            stack.consume(1, player);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    private InteractionResult interact(Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit) {
        ItemStack held=player.getItemInHand(hand);
        BlockPos c=center(p,l.getBlockState(p));
        if (MaidHeadContent.isHeadItem(held)
                && (!(l.getBlockEntity(c) instanceof DissectionTableBlockEntity existing) || !existing.occupied())) {
            return mountHead(l, p, player, held, hit);
        }
        if (isTableMountItem(held)
                && (!(l.getBlockEntity(c) instanceof DissectionTableBlockEntity existing) || !existing.occupied())) {
            return mount(l,p,player,held,hit);
        }
        if(!(l.getBlockEntity(c) instanceof DissectionTableBlockEntity table)||!table.occupied()) return InteractionResult.PASS;
        if(!player.mayBuild()||!player.mayUseItemAt(c,hit.getDirection(),held)) return InteractionResult.FAIL;
        if (table.headMounted()) return interactHead(l, c, player, held, table);
        if(table.cutting()) return InteractionResult.sidedSuccess(l.isClientSide);
        // Both headless bodies and full remains may have their tail cut first.  The full-remains
        // gate below still requires an axe before any of the headless-body dissection steps.
        if (held.is(Items.SHEARS) && !table.tailRemoved()) {
            if (!l.isClientSide) beginTailCut(l, c, table);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        // A full remains item remains in its authored position until its tail is removed and an axe
        // takes the head.  It is never generically rotated: its headless successor owns the A pose.
        if(table.isRemains()) {
            if (held.isEmpty() && player.isShiftKeyDown()) {
                if (!l.isClientSide) {
                    var item=table.take();
                    if(!player.getInventory().add(item))player.drop(item,false);
                }
                return InteractionResult.sidedSuccess(l.isClientSide);
            }
            if (held.getItem() instanceof AxeItem) {
                if (!l.isClientSide && table.tailRemoved()) {
                    toolFeedback(l, c, table, SoundEvents.AXE_STRIP);
                    popResource(l, c, table.removeHead());
                }
                return InteractionResult.sidedSuccess(l.isClientSide);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if (table.readyForFinalRemoval()) {
            if (!l.isClientSide) finish(l, c, table);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        // C with every organ removed has two deliberate endings: an axe continues to limb cuts,
        // while any other direct use packages the body as the processed-maid drop.
        if (table.readyForProcessedMaid() && !(held.getItem() instanceof AxeItem)) {
            if (!l.isClientSide) {
                popResource(l, c, MaidHeadContent.processedMaid(table.data(), table.interactionData()));
                table.finishDissection();
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if(held.isEmpty()&&player.isShiftKeyDown()) {
            if (!table.dissectionLocked() && !l.isClientSide) {
                var item=table.take();if(!player.getInventory().add(item))player.drop(item,false);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if(held.isEmpty()&&table.tailRemoved()) {
            // The first normal empty-hand click turns the body over.  Once it is front-side up,
            // consume later clicks instead of allowing a second flip or another interaction.
            if(!table.flipped()&&!l.isClientSide)table.flipToFront();
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if(held.is(Items.SHEARS)) {
            if (!l.isClientSide && table.flipped()) {
                int step=table.dissectionStep();
                if (step == 0) {
                    toolFeedback(l, c, table, SoundEvents.SHEEP_SHEAR);
                    table.advanceDissection(); // A -> B
                }
                else if (step >= 2 && step < DissectionTableBlockEntity.AXE_START_STEP) {
                    toolFeedback(l, c, table, SoundEvents.SHEEP_SHEAR);
                    popResource(l, c, organDrop(step));
                    table.advanceDissection(); // C: remove the matching authored group
                }
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if (table.flipped() && isFarmersDelightKnife(held) && table.dissectionStep() == 1) {
            if (!l.isClientSide) {
                toolFeedback(l, c, table, cuttingBoardKnifeSound());
                table.advanceDissection(); // B -> C
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if (table.flipped() && held.getItem() instanceof AxeItem) {
            int step=table.dissectionStep();
            if (step >= DissectionTableBlockEntity.AXE_START_STEP && step < DissectionTableBlockEntity.FINAL_STEP) {
                if (!l.isClientSide) {
                    toolFeedback(l, c, table, SoundEvents.AXE_STRIP);
                    popResource(l, c, axeDrop(step));
                    table.advanceDissection();
                }
                return InteractionResult.sidedSuccess(l.isClientSide);
            }
        }
        return MaidBodyInteractions.interact(new MaidBodyInteractions.Context(table,player,hand,hit));
    }
    private static InteractionResult interactHead(Level level, BlockPos pos, Player player, ItemStack held,
                                                   DissectionTableBlockEntity table) {
        // Shift-empty is always the reversible path until the tongue has been removed.
        if (held.isEmpty() && player.isShiftKeyDown() && table.headStage() < 5) {
            if (!level.isClientSide) give(player, table.takeMountedHead());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        switch (table.headStage()) {
            case 0 -> {
                if (!held.is(Items.SHEARS)) return InteractionResult.sidedSuccess(level.isClientSide);
                if (!level.isClientSide) {
                    headSound(level, pos, SoundEvents.SHEEP_SHEAR);
                    popResource(level, pos, new ItemStack(MaidHeadContent.WINE_FOX_EAR.get(), 2));
                    table.advanceHeadStage();
                }
            }
            case 1 -> {
                if (!(held.getItem() instanceof AxeItem)) return InteractionResult.sidedSuccess(level.isClientSide);
                if (!level.isClientSide) {
                    headSound(level, pos, SoundEvents.AXE_STRIP);
                    table.advanceHeadStage();
                }
            }
            case 2 -> {
                if (!level.isClientSide) {
                    headSound(level, pos, SoundEvents.SLIME_SQUISH);
                    popResource(level, pos, new ItemStack(MaidHeadContent.WINE_FOX_BRAIN.get()));
                    table.advanceHeadStage();
                }
            }
            case 3 -> {
                if (!held.is(Items.SHEARS)) return InteractionResult.sidedSuccess(level.isClientSide);
                if (!level.isClientSide) {
                    headSound(level, pos, SoundEvents.SHEEP_SHEAR);
                    popResource(level, pos, new ItemStack(MaidHeadContent.WINE_FOX_EYE.get(), 2));
                    table.advanceHeadStage();
                }
            }
            case 4 -> {
                if (!held.is(Items.SHEARS)) return InteractionResult.sidedSuccess(level.isClientSide);
                if (!level.isClientSide) {
                    headSound(level, pos, SoundEvents.SHEEP_SHEAR);
                    popResource(level, pos, new ItemStack(MaidHeadContent.WINE_FOX_TONGUE.get()));
                    table.advanceHeadStage();
                }
            }
            default -> {
                if (!level.isClientSide) give(player, table.takeEmptyHead());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
    }
    private static void headSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1F, 1F);
    }
    private static void beginTailCut(Level level, BlockPos pos, DissectionTableBlockEntity table) {
        table.beginCut();
        toolFeedback(level, pos, table, SoundEvents.SHEEP_SHEAR);
        level.scheduleTick(pos, table.getBlockState().getBlock(), 2);
    }
    /** Each successful tool action is audible and emits the same compact 3--4-drop blood burst. */
    private static void toolFeedback(Level level, BlockPos pos, DissectionTableBlockEntity table, SoundEvent sound) {
        level.blockEvent(pos, table.getBlockState().getBlock(), 1, 3 + level.random.nextInt(2));
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1F, 1F);
    }
    /** Farmer's Delight provides this exact cutting-board sound.  Vanilla wood-hit is a safe fallback. */
    private static SoundEvent cuttingBoardKnifeSound() {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath(
                "farmersdelight", "block.cutting_board.knife"));
        return sound != null ? sound : SoundEvents.WOOD_HIT;
    }
    private static boolean isTableMountItem(ItemStack stack) {
        return stack.is(MaidHeadContent.MAID_BODY_ITEM.get())
                || stack.is(MaidHeadContent.MAID_REMAINS_ITEM.get())
                || stack.is(MaidHeadContent.PROCESSED_MAID.get());
    }
    @Override protected void tick(BlockState s, net.minecraft.server.level.ServerLevel l, BlockPos p, net.minecraft.util.RandomSource random) {
        if(l.getBlockEntity(p) instanceof DissectionTableBlockEntity table && table.cutting()) {
            table.removeTail();
            popResource(l, p, new ItemStack(MaidHeadContent.WINE_FOX_TAIL.get()));
        }
    }
    private static boolean isFarmersDelightKnife(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "farmersdelight".equals(id.getNamespace()) && id.getPath().endsWith("_knife");
    }
    private static ItemStack organDrop(int step) {
        return switch (step) {
            case 2 -> new ItemStack(Items.BONE);
            case 3 -> new ItemStack(MaidHeadContent.WINE_FOX_HEART.get());
            case 4 -> new ItemStack(MaidHeadContent.WINE_FOX_LUNG.get());
            case 5 -> new ItemStack(MaidHeadContent.WINE_FOX_DIAPHRAGM.get());
            case 6 -> new ItemStack(MaidHeadContent.WINE_FOX_LIVER.get());
            case 7 -> new ItemStack(MaidHeadContent.WINE_FOX_STOMACH.get());
            case 8 -> new ItemStack(MaidHeadContent.WINE_FOX_PANCREAS.get());
            case 9 -> new ItemStack(MaidHeadContent.WINE_FOX_INTESTINE.get());
            case 10 -> new ItemStack(MaidHeadContent.WINE_FOX_KIDNEY.get(), 2);
            case 11 -> new ItemStack(MaidHeadContent.WINE_FOX_UTERUS.get());
            default -> ItemStack.EMPTY;
        };
    }
    private static ItemStack axeDrop(int step) {
        return switch (step) {
            case 12, 13 -> new ItemStack(MaidHeadContent.WINE_FOX_ARM.get());
            case 14 -> new ItemStack(MaidHeadContent.WINE_FOX_FOOT.get(), 2);
            case 15, 16 -> new ItemStack(MaidHeadContent.WINE_FOX_HAM.get());
            default -> ItemStack.EMPTY;
        };
    }
    private static void finish(Level level, BlockPos pos, DissectionTableBlockEntity table) {
        popResource(level, pos, new ItemStack(MaidHeadContent.WINE_FOX_LOIN.get(), 2));
        popResource(level, pos, new ItemStack(MaidHeadContent.WINE_FOX_RIB_MEAT.get(), 4));
        popResource(level, pos, new ItemStack(MaidHeadContent.WINE_FOX_WAIST_MEAT.get(), 2));
        popResource(level, pos, new ItemStack(MaidHeadContent.WINE_FOX_BREAST.get(), 2));
        popResource(level, pos, new ItemStack(MaidHeadContent.WINE_FOX_HONEY_HOLE.get()));
        table.finishDissection();
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit) {
        if(stack.isEmpty()&&hand==InteractionHand.MAIN_HAND)return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return switch(interact(l,p,player,hand,hit)){
            case SUCCESS,SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            case PASS -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        };
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){return interact(l,p,player,InteractionHand.MAIN_HAND,hit);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        int part=s.getValue(PART);double lo=part==0?0.5:0,hi=part==2?15.5:16;
        return switch(s.getValue(FACING)){
            case SOUTH -> Block.box(-2,0,16-hi,18,14.3,16-lo);
            case EAST -> Block.box(16-hi,0,-2,16-lo,14.3,18);
            case WEST -> Block.box(lo,0,-2,hi,14.3,18);
            default -> Block.box(-2,0,lo,18,14.3,hi);
        };
    }
}
