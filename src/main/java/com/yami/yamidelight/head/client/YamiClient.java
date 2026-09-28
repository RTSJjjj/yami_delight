package com.yami.yamidelight.head.client;

import com.yami.yamidelight.YamiDelight;
import com.yami.yamidelight.head.MaidHeadContent;
import com.yami.yamidelight.head.client.MaidHeadHider;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client wiring: the two renderers a head needs, and the reload hook that makes a resource reload also
 * refresh the models and textures read from disk.
 *
 * <p>All three handlers listen on the mod bus and none of them says so: since 1.21.1 the bus follows
 * from the event type, which is why the deprecated {@code bus} attribute is not spelled out here.
 */
@EventBusSubscriber(modid = YamiDelight.MODID, value = Dist.CLIENT)
public final class YamiClient {
    private YamiClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.yami.yamidelight.feast.FeastContent.FOX_HEAD_FOOD_BE.get(), FoxHeadFoodRenderer::new);
        event.registerBlockEntityRenderer(MaidHeadContent.DISSECTION_TABLE_BE.get(), DissectionTableRenderer::new);
        event.registerBlockEntityRenderer(MaidHeadContent.MEAT_HOOK_BE.get(), MeatHookRenderer::new);
        event.registerBlockEntityRenderer(com.yami.yamidelight.feast.FeastContent.STUFFED_WINEFOX_BE.get(), StuffedWinefoxRenderer::new);
        event.registerBlockEntityRenderer(com.yami.yamidelight.feast.FeastContent.SEA_LAND_WIND_WINEFOX_BE.get(), SeaLandWindWinefoxRenderer::new);
        event.registerBlockEntityRenderer(MaidHeadContent.MAID_BODY_BE.get(), MaidBodyRenderers.BlockRenderer::new);
        event.registerBlockEntityRenderer(MaidHeadContent.MAID_HEAD_BE.get(), MaidHeadRenderers.BlockRenderer::new);
    }

    @SubscribeEvent
    public static void itemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(FoxHeadFoodRenderer.ITEM_EXTENSION, com.yami.yamidelight.feast.FeastContent.FOX_LANTERN_ITEM.get());
        event.registerItem(MaidBodyRenderers.ITEM_EXTENSION, MaidHeadContent.MAID_BODY_ITEM.get());
        event.registerItem(MaidBodyRenderers.ITEM_EXTENSION, MaidHeadContent.MAID_REMAINS_ITEM.get());
        event.registerItem(MaidHeadRenderers.ITEM_EXTENSION, MaidHeadContent.MAID_HEAD_ITEM.get(), MaidHeadContent.EMPTY_WINEFOX_HEAD.get());
    }

    /**
     * Textures registered by hand are closed when the resource manager reloads, and mod listeners run
     * after the vanilla ones, so dropping the caches here means the next head re-reads and re-registers
     * rather than binding a name that has just been closed.
     */
    @SubscribeEvent
    public static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ResourceManagerReloadListener() {
            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                StaticHairCollision.clear();
                HeadHairPlacement.clear();
                FoxHeadFoodRenderer.invalidate();
                YsmModelLibrary.invalidate();
                MaidHeadModels.invalidate();
                MaidBodyModels.invalidate();
                DissectionTableRenderer.invalidate();
                MeatHookRenderer.invalidate();
                StuffedWinefoxRenderer.invalidate();
                SeaLandWindWinefoxRenderer.invalidate();
                MaidHookPose.invalidate();
                TlmModelLibrary.invalidate();
                MaidDeathAnimation.invalidate();
                HeadExpression.invalidate();
                AllHeadParts.invalidate();
                MaidHangingDrips.clear();
            }
        });
    }

    /**
     * Keeps the head off a maid whose model was rebuilt since she lost it: a resource reload or a model
     * change builds a fresh bone tree, and the hide has to be put back on the new bones.
     */
    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        StaticHairCollision.tick();
        MaidHeadHider.tick();
        MaidDeathAnimation.tick();
        MaidBloodSpray.tick();
    }

    /**
     * The last moment the head can be hidden before this maid is drawn, for the frames where the model
     * was rebuilt between two ticks. The render path also hides it from inside {@code YsmMaidAccess},
     * which is the one that always lands after the animations.
     */
    @SubscribeEvent
    public static void beforeMobRender(RenderLivingEvent.Pre<LivingEntity, ?> event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            // Health and removal arrive as separate packets. Never draw a vanilla
            // death/reset frame in between after our custom clip has finished.
            if ((MaidHeadHider.isHeadless(maid.getId()) || com.yami.yamidelight.head.MaidHanging.active(maid)) && maid.isDeadOrDying()) {
                event.setCanceled(true);
                return;
            }
            MaidHeadHider.hideHead(maid);
            MaidBloodSpray.capture(maid, event.getPoseStack());
            MaidHangingDrips.capture(maid, event.getPoseStack());
        }
    }

    /** Entity ids mean nothing in the next world, so the headless list does not outlive this one. */
    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        StaticHairCollision.clear();
        HeadHairPlacement.clear();
        FoxHeadFoodRenderer.invalidate();
        DissectionTableRenderer.invalidate();
        MaidHeadHider.clear();
        MaidBloodSpray.clear();
        MaidHangingDrips.clear();
    }
}
