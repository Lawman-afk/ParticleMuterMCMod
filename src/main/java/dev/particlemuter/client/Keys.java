package dev.particlemuter.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** A key for the list (none by default, as the sound mod's: the inventory button is the way in). */
public final class Keys {
    public static final KeyMapping OPEN = new KeyMapping("key.particlemuter.open", InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);

    private Keys() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN));
    }

    /** Each client tick: the key pressed in the game opens the list. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.gui.screen() == null && mc.player != null) {
                mc.gui.setScreen(new MuterScreen(null));
            }
        }
    }
}
