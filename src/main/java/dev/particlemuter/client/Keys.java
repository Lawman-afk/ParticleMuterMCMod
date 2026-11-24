package dev.particlemuter.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** A key for the list (none by default, as the sound mod's: the inventory button is the way in). */
public final class Keys {
    public static final KeyMapping OPEN = new KeyMapping("key.particlemuter.open", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.misc");

    private Keys() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN));
    }

    /** Each client tick: the key pressed in the game opens the list. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new MuterScreen(null));
            }
        }
    }
}
