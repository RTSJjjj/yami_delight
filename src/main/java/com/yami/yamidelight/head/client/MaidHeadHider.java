package com.yami.yamidelight.head.client;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.yami.yamidelight.YamiDelight;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Takes the head off the maid on screen, so a maid whose head has been taken reads as headless.
 *
 * <p>Which model loses a bone depends on who is drawing her. A maid in a YSM skin is drawn by YSM, and
 * {@link YsmMaidAccess} reaches that model through the maid renderer and wraps it, which is where
 * {@link #hideHead} gets called from - inside the render call, after the animations and immediately
 * before the bones are drawn. A maid in one of Touhou Little Maid's own models is drawn by that mod, and
 * there the model is the {@code GeckoMaidEntity} the mod keeps on the entity, which is a plain
 * attachment read and needs no reflection at all. The client tick below repeats the hide for any maid
 * whose model was rebuilt since.
 *
 * <p>Which maids are headless comes from the server, because the flag lives in her persistent data and
 * that is saved but never synced. See {@code MaidHeadNetwork}.
 */
public final class MaidHeadHider {
    /** The bone YSM models call the head joint; the fallback is whatever the model calls its head. */
    private static final String HEAD_BONE = "MHead";
    /** The other name the same joint goes by: YSM models say {@code MHead}, maid models say {@code head}. */
    private static final String HEAD_BONE_LOWER = "head";
    private static final Set<Integer> WITHOUT_HEADS = new HashSet<>();
    private static boolean reported;

    private MaidHeadHider() {}

    /** A head was taken: from now on this maid is drawn without hers. */
    public static void mark(int entityId) {
        WITHOUT_HEADS.add(entityId);
    }

    public static void clear() {
        WITHOUT_HEADS.clear();
    }

    public static boolean isHeadless(int entityId) {
        return WITHOUT_HEADS.contains(entityId);
    }

    /** Re-hides heads for maids whose model was rebuilt; called once per client tick. */
    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || WITHOUT_HEADS.isEmpty()) {
            return;
        }
        for (int id : List.copyOf(WITHOUT_HEADS)) {
            if (level.getEntity(id) instanceof EntityMaid maid) {
                hideHead(maid);
            }
        }
    }

    /**
     * Hides the head group of one maid. False when this maid still has her head, when she is not drawn
     * by Yes Steve Model at all, or when the model is not built yet - all of which are normal.
     */
    public static boolean hideHead(EntityMaid maid) {
        if (!WITHOUT_HEADS.contains(maid.getId())) {
            return false;
        }
        if (!maid.isYsmModel()) {
            // Touhou Little Maid's own renderer reads these flags, so hiding really does hide her head.
            GeckoMaidEntity<?> gecko = maid.getData(GeckoMaidEntity.TYPE);
            if (gecko == null) {
                report("no GeckoMaidEntity on " + maid.getName().getString());
                return false;
            }
            // getCurrentModel is the instance the renderer draws; getGeoModel can hand back a different
            // one, and hiding a bone on a model nobody draws does nothing at all.
            ILocationModel model = gecko.getCurrentModel();
            if (model == null) {
                model = gecko.getGeoModel();
            }
            return hideIn(model);
        }
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(maid);
        if (!(renderer instanceof EntityMaidRenderer maidRenderer)) {
            return false;
        }
        IGeoEntityRenderer<Mob> ysm = YsmMaidAccess.of(maidRenderer);
        if (ysm == null) {
            return false;
        }
        IGeoEntity entity = ysm.getGeoEntity(maid);
        ILocationModel model = entity == null ? null : entity.getGeoModel();
        return hideIn(model);
    }

    /** Keep the humanoid visible throughout hanging death, without removing its head. */
    public static void keepHangingBody(EntityMaid maid) {
        if (!com.yami.yamidelight.head.MaidHanging.active(maid)) return;
        var gecko = maid.getData(GeckoMaidEntity.TYPE);
        if (gecko == null || gecko.getCurrentModel() == null) return;
        var model = gecko.getCurrentModel();
        AnimatedGeoBone body = findBone(model, "AllBody"), fox = findBone(model, "FOX");
        if (body != null && fox != null) {
            body.setScale(1F, 1F, 1F);
            fox.setScale(0F, 0F, 0F);
        }
    }

    /** Hides the head bone of a built model, whichever of the two names it uses. */
    private static boolean hideIn(ILocationModel model) {
        if (!(model instanceof AnimatedGeoModel animated)) {
            return false;
        }
        AnimatedGeoBone head = findBone(animated, HEAD_BONE);
        if (head == null) {
            head = findBone(animated, HEAD_BONE_LOWER);
        }
        if (head == null) {
            head = animated.head();
        }
        if (head == null) {
            report("no head bone in a model with " + animated.bones().size() + " bones");
            return false;
        }
        // setHidden hides the cubes and everything hanging off them - hair, ears, hats - which is the
        // whole MHead group and not just the skull.
        head.setHidden(true, true);
        head.setCubesHidden(true);
        // parallel1 hides the humanoid below 20% health; override only headless maids,
        // after animation evaluation, so the death pose remains visible after kill().
        AnimatedGeoBone body = findBone(animated, "AllBody");
        AnimatedGeoBone fox = findBone(animated, "FOX");
        if (body != null && fox != null) {
            body.setScale(1F, 1F, 1F);
            fox.setScale(0F, 0F, 0F);
        }
        return true;
    }

    /** A bone by name, ignoring case: the same joint is {@code MHead} in one model and {@code head} in another. */
    @Nullable
    private static AnimatedGeoBone findBone(AnimatedGeoModel model, String name) {
        AnimatedGeoBone exact = model.bones().get(name);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, AnimatedGeoBone> entry : model.bones().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** One line, the first time a head that should have been hidden could not be found in the model. */
    private static void report(String why) {
        if (!reported) {
            reported = true;
            YamiDelight.LOGGER.warn("yamidelight: could not hide a head ({})", why);
        }
    }
}
