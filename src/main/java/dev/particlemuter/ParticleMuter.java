package dev.particlemuter;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.particlemuter.client.DevRuns;
import dev.particlemuter.client.InventoryButton;
import dev.particlemuter.client.Keys;
import dev.particlemuter.client.MuterScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Particle Muter: any particle, Minecraft's or a mod's, turned off from a button in the inventory (DESIGN.md). A
 * client-side mod only: it adds nothing to the game, and on a dedicated server it does nothing at all.
 */
@Mod(ParticleMuter.MODID)
public class ParticleMuter {
    public static final String MODID = "particlemuter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ParticleMuter() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.start(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    /** The client's part (its own class, so a server never loads the client's classes). */
    private static final class Client {
        static void start(IEventBus modBus) {
            Settings.load();
            DevRuns.register();                                 // the dev test runs leave the user's mouse and windows alone
            // The list in the mod list's Config button too.
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new MuterScreen(parent)));
            modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(Catalog::build));
            Keys.register(modBus);
            InventoryButton.register();
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    Tracker.tick();
                    Keys.tick();
                }
            });
        }
    }
}
