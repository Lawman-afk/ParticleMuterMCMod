package dev.particlemuter.devworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import dev.particlemuter.Catalog;
import dev.particlemuter.Muting;
import dev.particlemuter.ParticleMuter;
import dev.particlemuter.Settings;
import dev.particlemuter.Tracker;
import dev.particlemuter.client.MuterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development aid, left out of the released jar (build.gradle's jar task; found by its annotation, so nothing in the
 * jar names it): the world the user tests in. With {@code -Dparticlemuter.lab=true} (the {@code lab} Gradle run) the
 * client makes a flat creative world, "Particle Lab" (run/saves/particle_lab), sets out particle sources of many kinds
 * in a row with a sign at each, checks that muting works (the counts go on, the particles stop), photographs the
 * inventories' button and each list of the screen (run/screenshots/lab_*.png), and quits, the world saved to copy into
 * the test instance's saves, and the settings left as they were found (nothing muted).
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ParticleMuter.MODID)
public final class Lab {
    private static final boolean ENABLED = Boolean.getBoolean("particlemuter.lab");
    /** Ticks for the world to come in and the particles to get going, and between pictures. */
    private static final int SETTLE = 200, EACH = 40;

    /** A picture: where the eye is and which way it looks, and what to do on the client before it is taken. */
    private record View(String name, Vec3 eye, float yaw, float pitch, Consumer<Minecraft> setup) {}

    private static boolean worldMade, built;
    private static int waited, shot;
    private static List<View> views = List.of();
    private static int ground;
    private static final List<String> checks = new ArrayList<>();
    private static int particlesBefore = -1;

    private Lab() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
        if (mc.level == null && !worldMade && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            // As a player who has never used the mod: nothing muted, the button in its default place.
            Settings.unmuteAll();
            Settings.setEnabled(true);
            Settings.setShowButton(true);
            Settings.setButtonPlace(false, null);
            Settings.setButtonPlace(true, null);
            Muting.apply();
            try {
                var settings = new LevelSettings("Particle Lab", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true,
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(fresh(mc, "particle_lab"), settings, new WorldOptions(0L, false, false), Lab::flat,
                        mc.gui.screen());
            } catch (IOException e) {
                ParticleMuter.LOGGER.error("Lab: could not make a world", e);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        if (!built) {
            built = true;
            server.execute(() -> build(server.overworld()));
            return;
        }
        if (++waited < SETTLE) {
            return;
        }
        if (mc.gui.hud.isHidden() == false) {
            mc.gui.hud.toggle();                                   // no hand or hotbar in the pictures
        }
        // Each view: the camera and the screen set, a wait for the frame to settle, the picture.
        int t = ++shot;
        if (t % EACH != 0) {
            return;
        }
        int step = t / EACH;
        if (step > 1 && step - 2 < views.size()) {
            View v = views.get(step - 2);
            Screenshot.grab(mc.gameDirectory, "lab_" + v.name() + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> ParticleMuter.LOGGER.info("Lab: {}", msg.getString()));
        }
        if (step - 1 < views.size()) {
            View v = views.get(step - 1);
            v.setup().accept(mc);
            server.execute(() -> server.getPlayerList().getPlayers().getFirst()
                    .teleportTo(server.overworld(), v.eye().x, v.eye().y - 1.62, v.eye().z, Set.of(), v.yaw(), v.pitch(), true));
            return;
        }
        if (step - 1 == views.size()) {
            mc.gui.setScreen(null);
            finish(mc);
            server.execute(() -> server.getPlayerList().getPlayers().getFirst()
                    .teleportTo(server.overworld(), 0.5, ground, 0.5, Set.of(), 0f, 10f, true));
            return;
        }
        if (step - 1 > views.size() + 1) {
            ParticleMuter.LOGGER.info("Lab built");
            mc.stop();
        }
    }

    /** The checks' results in the log, and the settings put back (nothing muted). */
    private static void finish(Minecraft mc) {
        Settings.unmuteAll();
        Settings.save();
        Muting.apply();
        boolean ok = checks.stream().allMatch(c -> c.startsWith("PASS"));
        for (String c : checks) {
            ParticleMuter.LOGGER.info("Lab check: {}", c);
        }
        ParticleMuter.LOGGER.info("Lab checks: {} of {} passed{}", checks.stream().filter(c -> c.startsWith("PASS")).count(), checks.size(),
                ok ? "" : " (SOME FAILED)");
    }

    private static void check(boolean ok, String what) {
        checks.add((ok ? "PASS " : "FAIL ") + what);
    }

    private static int particles(Minecraft mc) {
        try {
            return Integer.parseInt(mc.particleEngine.countParticles().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Catalog.Entry entry(String id) {
        for (Catalog.Entry e : Catalog.entries()) {
            if (e.id.equals(id)) {
                return e;
            }
        }
        throw new IllegalStateException("no catalog entry " + id);
    }

    /** Sets the catalog's checks: every vanilla type listed and named. */
    private static void checkCatalog() {
        int types = 0, unnamed = 0;
        StringBuilder missing = new StringBuilder();
        for (Catalog.Entry e : Catalog.entries()) {
            if (e.type == null) {
                continue;
            }
            types++;
            if (e.namespace.equals("minecraft") && !net.minecraft.locale.Language.getInstance().has("particlemuter.particle." + e.id.replace(':', '.'))) {
                unnamed++;
                missing.append(' ').append(e.id);
            }
        }
        check(types >= 128, "the catalog lists every particle type (" + types + ")");
        check(unnamed == 0, "every vanilla particle has a name of ours" + (unnamed == 0 ? "" : ":" + missing));
    }

    /**
     * On the server: the rules; a row of particle sources with a sign at each (campfires, torches and candles, lava,
     * dripping blocks, a portal, an enchanting table, a spawner, a bubble column, cherry leaves, a spore blossom, end
     * rods, redstone); the spawn's signs; then the views to take.
     */
    private static void build(ServerLevel level) {
        var commands = level.getServer().getCommands();
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        for (String c : List.of("time set 6000", "gamerule advance_time false", "gamerule advance_weather false", "weather clear",
                "gamerule spawn_mobs false", "difficulty peaceful", "kill @e[type=!player]")) {
            commands.performPrefixedCommand(source, c);
        }
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -2; cz <= 3; cz++) {
                level.getChunk(cx, cz);
            }
        }
        ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        int y = ground, z = 8;

        // Campfires: two plain, two signal fires (on hay).
        for (int i = 0; i < 4; i++) {
            int x = -22 + i;
            if (i >= 2) {
                set(level, x, y, z, Blocks.HAY_BLOCK.defaultBlockState());
            }
            set(level, x, i >= 2 ? y + 1 : y, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, i >= 2));
        }
        sign(level, -21, y, z - 2, "Campfires", "smoke rises", "(signal fires", "on hay: taller)");

        // Torches, soul torches, candles.
        set(level, -17, y, z, Blocks.TORCH.defaultBlockState());
        set(level, -16, y, z, Blocks.SOUL_TORCH.defaultBlockState());
        set(level, -15, y, z, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true));
        set(level, -14, y, z, Blocks.REDSTONE_TORCH.defaultBlockState());
        sign(level, -16, y, z - 2, "Torches and", "candles: flame,", "soul flame,", "redstone dust");

        // A pool of lava, and lava dripping from a ledge over it.
        for (int x = -11; x <= -9; x++) {
            for (int dz = 0; dz <= 2; dz++) {
                set(level, x, y - 1, z + dz, Blocks.LAVA.defaultBlockState());
            }
        }
        set(level, -10, y + 3, z + 3, Blocks.STONE.defaultBlockState());
        set(level, -10, y + 2, z + 3, Blocks.STONE.defaultBlockState());
        set(level, -10, y + 4, z + 3, Blocks.LAVA.defaultBlockState());
        set(level, -10, y + 2, z + 2, Blocks.AIR.defaultBlockState());
        set(level, -10, y + 3, z + 2, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
        set(level, -10, y + 4, z + 2, Blocks.LAVA.defaultBlockState());
        sign(level, -10, y, z - 2, "Lava: pops,", "smoke, and", "drips from", "the ledge");

        // Water and crying obsidian dripping from the underside of floating blocks.
        set(level, -6, y + 3, z, Blocks.STONE.defaultBlockState());
        set(level, -6, y + 4, z, Blocks.WATER.defaultBlockState());
        for (int[] d : new int[][] {{-7, 0}, {-5, 0}, {-6, -1}, {-6, 1}}) {
            set(level, d[0], y + 4, z + d[1], Blocks.GLASS.defaultBlockState());
        }
        set(level, -4, y + 3, z, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        sign(level, -5, y, z - 2, "Drips: water", "through stone,", "crying obsidian", "tears");

        // A nether portal.
        for (int x = 0; x <= 3; x++) {
            set(level, x, y, z, Blocks.OBSIDIAN.defaultBlockState());
            set(level, x, y + 4, z, Blocks.OBSIDIAN.defaultBlockState());
        }
        for (int h = 1; h <= 3; h++) {
            set(level, 0, y + h, z, Blocks.OBSIDIAN.defaultBlockState());
            set(level, 3, y + h, z, Blocks.OBSIDIAN.defaultBlockState());
            for (int x = 1; x <= 2; x++) {
                level.setBlock(new BlockPos(x, y + h, z), Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X), 18);
            }
        }
        sign(level, 1, y, z - 2, "Nether portal:", "portal", "particles", "");

        // An enchanting table among bookshelves.
        set(level, 7, y, z + 1, Blocks.ENCHANTING_TABLE.defaultBlockState());
        for (int dx = -2; dx <= 2; dx++) {
            set(level, 7 + dx, y, z + 3, Blocks.BOOKSHELF.defaultBlockState());
            set(level, 7 + dx, y + 1, z + 3, Blocks.BOOKSHELF.defaultBlockState());
        }
        sign(level, 7, y, z - 2, "Enchanting", "table: glyphs", "fly from the", "bookshelves");

        // A pig spawner (flame and smoke while a player is near).
        BlockPos spawner = new BlockPos(11, y, z);
        level.setBlock(spawner, Blocks.SPAWNER.defaultBlockState(), 3);
        if (level.getBlockEntity(spawner) instanceof SpawnerBlockEntity be) {
            be.setEntityId(EntityTypes.PIG, level.getRandom());
            be.setChanged();
        }
        sign(level, 11, y, z - 2, "Spawner:", "flame and", "smoke", "");

        // A bubble column in a glass tube (soul sand under water).
        set(level, 15, y - 1, z, Blocks.SOUL_SAND.defaultBlockState());
        for (int h = 0; h <= 3; h++) {
            for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                set(level, 15 + d[0], y + h, z + d[1], Blocks.GLASS.defaultBlockState());
            }
            set(level, 15, y + h, z, Blocks.WATER.defaultBlockState());
        }
        sign(level, 15, y, z - 2, "Bubble column:", "bubbles up,", "and pop at", "the top");

        // Cherry leaves overhead (petals fall), a spore blossom under a block beside them.
        for (int x = 18; x <= 21; x++) {
            for (int dz = -1; dz <= 2; dz++) {
                set(level, x, y + 4, z + dz, Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            }
        }
        set(level, 23, y + 4, z, Blocks.MOSS_BLOCK.defaultBlockState());
        set(level, 23, y + 3, z, Blocks.SPORE_BLOSSOM.defaultBlockState());
        sign(level, 20, y, z - 2, "Cherry petals", "fall; a spore", "blossom fills", "the air");

        // End rods.
        for (int x = 26; x <= 28; x++) {
            set(level, x, y, z, Blocks.END_ROD.defaultBlockState());
        }
        sign(level, 27, y, z - 2, "End rods:", "sparkles", "", "");

        // The spawn's signs: what the mod is and where its button is.
        sign(level, -1, y, 3, "Particle Muter", "Lab: sources", "of particles", "all along here");
        sign(level, 1, y, 3, "Open your", "inventory: the", "small button at", "the player's top");
        sign(level, 3, y, 3, "right opens the", "list. Click a", "particle there", "to mute it");
        commands.performPrefixedCommand(source, "setworldspawn 1 " + y + " 1");
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;                       // (else the camera falls between the views)
        player.onUpdateAbilities();

        Vec3 front = new Vec3(0.5, y + 6.0, -10.0);
        Vec3 fires = new Vec3(-19.0, y + 2.4, z - 6.0);
        List<View> list = new ArrayList<>();
        list.add(new View("overview", new Vec3(2.5, y + 14.0, -16.0), 0f, 32f, mc -> mc.gui.setScreen(null)));
        list.add(new View("campfires", fires, 0f, -6f, mc -> {
            mc.gui.setScreen(null);
            checkCatalog();
            particlesBefore = particles(mc);
        }));
        list.add(new View("inventory", front, 0f, 20f, mc -> {
            run(mc, "gamemode survival @a");
            mc.gui.setScreen(new InventoryScreen(mc.player));
        }));
        list.add(new View("inventory_creative", front, 0f, 20f, mc -> {
            run(mc, "gamemode creative @a");
            var screen = new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), false);
            mc.gui.setScreen(screen);
            selectInventoryTab(screen);
        }));
        list.add(new View("screen_all", front, 0f, 20f, mc -> screen(mc, "all", "")));
        list.add(new View("screen_recent", front, 0f, 20f, mc -> screen(mc, "recent", "")));
        list.add(new View("screen_mods", front, 0f, 20f, mc -> screen(mc, "mods", "")));
        list.add(new View("screen_search", front, 0f, 20f, mc -> screen(mc, "all", "smoke")));
        list.add(new View("screen_muted", front, 0f, 20f, mc -> {
            // Muted as a player would, by clicking them in the list: the campfires' two smokes.
            Catalog.Entry cosy = entry("minecraft:campfire_cosy_smoke"), signal = entry("minecraft:campfire_signal_smoke");
            cosy.setMuted(true);
            signal.setMuted(true);
            Muting.apply();
            Settings.save();
            check(Tracker.rate(cosy.index) > 0 && Tracker.rate(signal.index) > 0,
                    "the campfires' smoke is counted (" + Tracker.rate(cosy.index) + "/s and " + Tracker.rate(signal.index) + "/s)");
            screen(mc, "muted", "");
        }));
        list.add(new View("campfires_muted", fires, 0f, -6f, mc -> {
            mc.gui.screen().onClose();                             // (closing the list takes away the smoke already rising)
            mc.gui.setScreen(null);
        }));
        list.add(new View("campfires_muted_later", fires, 0f, -6f, mc -> {
            int after = particles(mc);
            Catalog.Entry cosy = entry("minecraft:campfire_cosy_smoke");
            check(after >= 0 && particlesBefore > 0 && after < particlesBefore * 0.7,
                    "muting the smoke takes it out of the world (" + particlesBefore + " particles before, " + after + " after)");
            check(Tracker.rate(cosy.index) > 0, "a muted particle is still counted (" + Tracker.rate(cosy.index) + "/s)");
            // The block particles: a block broken while they are muted (counted, as every particle is, but cancelled).
            int counted = Tracker.current(Catalog.BLOCK_BREAKING);
            Settings.setBlockBreaking(true);
            Muting.apply();
            mc.level.addDestroyBlockEffect(new BlockPos(0, ground + 1, 4), Blocks.STONE.defaultBlockState());
            check(Tracker.current(Catalog.BLOCK_BREAKING) == counted + 1, "a block broken while block breaking is muted goes through the hook");
        }));
        views = list;
        ParticleMuter.LOGGER.info("Lab: particle sources set out at y {}", y);
    }

    private static void run(Minecraft mc, String command) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    /** The list, from the inventory as its button opens it, showing that list and search. */
    private static void screen(Minecraft mc, String list, String search) {
        MuterScreen screen = mc.gui.screen() instanceof MuterScreen open ? open : new MuterScreen(new InventoryScreen(mc.player));
        if (mc.gui.screen() != screen) {
            mc.gui.setScreen(screen);
        }
        screen.showForPicture(list, search);
    }

    /** The creative inventory's own tab (Survival Inventory), as a player clicks it. */
    private static void selectInventoryTab(CreativeModeInventoryScreen screen) {
        try {
            var select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            select.setAccessible(true);
            select.invoke(screen, net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(CreativeModeTabs.INVENTORY));
        } catch (ReflectiveOperationException e) {
            ParticleMuter.LOGGER.warn("Lab: could not pick the creative inventory's tab", e);
        }
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }

    /** A standing oak sign at (x, y, z) facing north (toward the spawn), four lines on its front. */
    private static void sign(ServerLevel level, int x, int y, int z, String... lines) {
        BlockPos at = new BlockPos(x, y, z);
        level.setBlock(at, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8), 3);
        if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            SignText.Mutable text = sign.getText(SignTextSlot.FRONT).asMutable();
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                text.setLine(i, Component.literal(lines[i]));
            }
            sign.setText(text.asImmutable(), SignTextSlot.FRONT);
            sign.setChanged();
            level.sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), 3);
        }
    }

    /** A flat world of grass over dirt and stone, a plains biome. */
    private static WorldDimensions flat(HolderLookup.Provider registries) {
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        var settings = FlatLevelGeneratorSettings.getDefault(biomes, registries.lookupOrThrow(Registries.STRUCTURE_SET),
                        registries.lookupOrThrow(Registries.PLACED_FEATURE))
                .withBiomeAndLayers(List.of(new FlatLayerInfo(1, Blocks.BEDROCK), new FlatLayerInfo(20, Blocks.STONE),
                        new FlatLayerInfo(3, Blocks.DIRT), new FlatLayerInfo(1, Blocks.GRASS_BLOCK)),
                        java.util.Optional.empty(), biomes.getOrThrow(Biomes.PLAINS));
        return WorldPresets.createTestWorldDimensions(registries).replaceOverworldGenerator(registries, new FlatLevelSource(settings));
    }

    /** Deletes the worlds earlier runs left under {@code name} and gives the folder for this run's. */
    private static String fresh(Minecraft mc, String name) throws IOException {
        Path saves = mc.getLevelSource().getBaseDir();
        if (Files.isDirectory(saves)) {
            try (var old = Files.list(saves)) {
                for (Path dir : old.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith(name)).toList()) {
                    try (var walk = Files.walk(dir)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                    }
                }
            }
        }
        return FileUtil.findAvailableName(saves, name, "");
    }
}
