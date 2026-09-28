package com.yami.yamidelight.head.client;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yami.yamidelight.YamiDelight;
import java.lang.reflect.Field;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Reaches the renderer Yes Steve Model put inside Touhou Little Maid's maid renderer.
 *
 * <p>A maid in a YSM skin is not drawn by Touhou Little Maid at all: YSM registers its own
 * {@code IGeoEntityRenderer} into the public {@code YSM_ENTITY_MAID_RENDERER} field, and the maid
 * renderer keeps the instance it built in a private field. That private field is the only way to reach
 * the model YSM is actually drawing, which is what has to lose the head bone - the model the maid
 * carries as an attachment belongs to Touhou Little Maid's own skins and is a different object.
 *
 * <p>Rather than put a mixin in the build for one field, the field is read reflectively once. It is
 * worth being clear about what happens if that ever breaks: the head reverts to being drawn on the maid,
 * the log says so once, and nothing else in the mod is affected. The instance is swapped for
 * {@link HeadHideWrapper}, so from then on the head is hidden from inside the render call, right before
 * YSM draws the bones and after every animation has been applied.
 */
final class YsmMaidAccess {
    private static final String RENDERER_FIELD = "ysmMaidRenderer";
    private static Field field;
    private static boolean looked;
    private static boolean reported;

    private YsmMaidAccess() {}

    /** YSM's renderer for this maid's renderer, wrapped so a headless maid hides her head as she is drawn. */
    @Nullable
    @SuppressWarnings("unchecked")
    static IGeoEntityRenderer<Mob> of(EntityMaidRenderer renderer) {
        Field target = field();
        if (target == null) {
            return null;
        }
        try {
            Object value = target.get(renderer);
            if (value instanceof HeadHideWrapper wrapper) {
                return wrapper;
            }
            if (value instanceof IGeoEntityRenderer<?> ysm) {
                HeadHideWrapper wrapper = new HeadHideWrapper((IGeoEntityRenderer<Mob>) ysm);
                target.set(renderer, wrapper);
                return wrapper;
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(e);
            return null;
        }
    }

    @Nullable
    private static Field field() {
        if (!looked) {
            looked = true;
            try {
                Field candidate = EntityMaidRenderer.class.getDeclaredField(RENDERER_FIELD);
                candidate.setAccessible(true);
                field = candidate;
            } catch (NoSuchFieldException | RuntimeException e) {
                report(e);
            }
        }
        return field;
    }

    private static void report(Exception e) {
        if (!reported) {
            reported = true;
            YamiDelight.LOGGER.warn("yamidelight: cannot reach Yes Steve Model's maid renderer through "
                    + "EntityMaidRenderer.{}; a headless maid will keep her head drawn ({})",
                    RENDERER_FIELD, e.toString());
        }
    }

    /** Draws the maid exactly as YSM would, after hiding the head of a maid who has lost it. */
    private record HeadHideWrapper(IGeoEntityRenderer<Mob> delegate) implements IGeoEntityRenderer<Mob> {
        @Override
        public IGeoEntity getGeoEntity(Mob entity) {
            return delegate.getGeoEntity(entity);
        }

        @Override
        public void addGeoLayerRenderer(GeoLayerRenderer<?, ?> layer) {
            delegate.addGeoLayerRenderer(layer);
        }

        @Override
        public void geoRender(Mob entity, float limbSwing, float partialTick, PoseStack pose,
                              MultiBufferSource buffer, int light) {
            if (entity instanceof EntityMaid maid) {
                MaidHeadHider.hideHead(maid);
            }
            delegate.geoRender(entity, limbSwing, partialTick, pose, buffer, light);
        }
    }
}
