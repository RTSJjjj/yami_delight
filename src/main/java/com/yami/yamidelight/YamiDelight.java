package com.yami.yamidelight;

import com.yami.yamidelight.head.MaidHeadContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maid heads: a Touhou Little Maid that wears a Yes Steve Model skin drops her own {@code MHead} group
 * as an item, and that item mounts on the side of a block the way an item frame does.
 *
 * <p>The head is never baked into a texture or into a fixed model. What the item carries is the model
 * id, the texture id and the model name the maid was using; the client reads that same YSM model file
 * and draws the {@code MHead} bone together with everything hanging off it. That is what keeps the
 * head looking like the maid's head instead of a flattened picture of one, and it is why a model the
 * client does not have yet still shows up as a readable - if plain - default head rather than as a
 * missing texture.
 */
@Mod(YamiDelight.MODID)
public final class YamiDelight {
    public static final String MODID = "yamidelight";
    public static final String TOUHOU_LITTLE_MAID = "touhou_little_maid";
    public static final String YES_STEVE_MODEL = "yes_steve_model";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public YamiDelight(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, YamiConfig.SPEC);
        MaidHeadContent.init(modBus);
        com.yami.yamidelight.feast.FeastContent.init(modBus);
        com.yami.yamidelight.head.MaidDeathSounds.init(modBus);
    }
}
