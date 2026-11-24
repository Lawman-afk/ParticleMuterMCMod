package dev.particlemuter;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.particlemuter.client.DevRuns;
import dev.particlemuter.client.InventoryButton;
import dev.particlemuter.client.Keys;
import dev.particlemuter.client.MuterScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Particle Muter: any particle, Minecraft's or a mod's, turned off from a button in the inventory (DESIGN.md). A
 * client-side mod only: it adds nothing to the game and is never loaded on a dedicated server.
 */
@Mod(value = ParticleMuter.MODID, dist = Dist.CLIENT)
public class ParticleMuter {
    public static final String MODID = "particlemuter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ParticleMuter(IEventBus modBus, ModContainer container) {
        Settings.load();
        DevRuns.register();                                     // the dev test runs leave the user's mouse and windows alone
        // The list in the mod list's Config button too.
        container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (mod, parent) -> new MuterScreen(parent));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(Catalog::build));
        Keys.register(modBus);
        InventoryButton.register();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Tracker.tick();
            Keys.tick();
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
