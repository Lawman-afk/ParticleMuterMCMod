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
import dev.particlemuter.client.MuterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development aid, main only, left out of the released jar: the mod page's pictures, from a normally generated world
 * (the user's rule for every picture players see). With {@code -Dparticlemuter.showcase=true} (the {@code showcase}
 * Gradle run) the client makes a default world from a fixed seed, goes to the cherry grove nearest the spawn and
 * photographs its falling petals from four sides, the inventory with the mod's button and the Recent list there, then
 * mutes the petals and photographs the same views again; then the same in the basalt deltas nearest the Nether's
 * middle, with their white ash. Pictures in run/screenshots/showcase_*.png; then it quits. {@code -Pseed=<n>} picks
 * another world, {@code -PshotSize=1920x1080} the page's size.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ParticleMuter.MODID)
public final class Showcase {
    private static final boolean ENABLED = Boolean.getBoolean("particlemuter.showcase");
    private static final long SEED = Long.getLong("particlemuter.seed", 20261125L);
    /** Ticks for a place's chunks to be made and drawn once there; for particles to fill the air before a picture. */
    private static final int SETTLE = 700, FILL = 90;

    /** A step: what to do on the client (it may ask the server to move the player), and how long after it to take the
     * picture of it (named; null: no picture). */
    private record Step(String name, int ticks, Consumer<Minecraft> setup) {}

    private static boolean worldMade, started;
    private static final List<Step> steps = new ArrayList<>();
    private static int step = -1, waited;
    private static volatile Vec3 grove, deltas;
    private static volatile boolean groveReady, deltasReady;
    private static boolean pendingInventory;

    private Showcase() {}

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
            Settings.unmuteAll();                                  // (as a player new to the mod)
            Settings.setEnabled(true);
            Settings.setShowButton(true);
            Settings.setButtonPlace(false, null);
            Settings.setButtonPlace(true, null);
            Muting.apply();
            try {
                var settings = new LevelSettings("Particle Muter Showcase", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true,
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(fresh(mc, "particlemuter_showcase"), settings, new WorldOptions(SEED, true, false),
                        WorldPresets::createNormalWorldDimensions, mc.gui.screen());
            } catch (IOException e) {
                ParticleMuter.LOGGER.error("Showcase: could not make a world", e);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        if (!started) {
            started = true;
            mc.options.renderDistance().set(12);
            mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            mc.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
            plan();
            server.execute(() -> findGrove(server));
            return;
        }
        if (pendingInventory && !mc.player.hasInfiniteMaterials()) {
            mc.gui.setScreen(new InventoryScreen(mc.player));
            pendingInventory = false;
        }
        if (!groveReady) {
            return;
        }
        if (mc.gui.hud.isHidden() == false) {
            mc.gui.hud.toggle();                                   // no hand or hotbar in the pictures
        }
        if (step >= 0 && step < steps.size() && ++waited < steps.get(step).ticks()) {
            return;
        }
        if (step >= 0 && step < steps.size() && steps.get(step).name() != null) {
            String name = steps.get(step).name();
            Screenshot.grab(mc.gameDirectory, "showcase_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> ParticleMuter.LOGGER.info("Showcase: {}", msg.getString()));
        }
        step++;
        waited = 0;
        if (step < steps.size()) {
            steps.get(step).setup().accept(mc);
        } else if (step == steps.size() + 40) {
            ParticleMuter.LOGGER.info("Showcase done");
            mc.stop();
        }
    }

    /** The steps: the grove (petals, the button, the list, then the petals muted), then the deltas (ash, then muted). */
    private static void plan() {
        String[] sides = {"s", "w", "n", "e"};
        for (int i = 0; i < 4; i++) {
            int side = i;
            steps.add(new Step("grove_" + sides[i], FILL, mc -> look(mc, grove, Level.OVERWORLD, side, false)));
        }
        steps.add(new Step(null, 20, mc -> gameMode(mc, GameType.SURVIVAL)));
        steps.add(new Step("grove_inventory", 40, mc -> pendingInventory = true));
        steps.add(new Step("grove_recent", 60, mc -> {
            MuterScreen screen = new MuterScreen(new InventoryScreen(mc.player));
            mc.gui.setScreen(screen);
            screen.showForPicture("recent", "");
        }));
        steps.add(new Step(null, 20, mc -> {
            mc.gui.setScreen(null);
            gameMode(mc, GameType.CREATIVE);
            mute(mc, "minecraft:cherry_leaves");
        }));
        for (int i = 0; i < 4; i++) {
            int side = i;
            steps.add(new Step("grove_" + sides[i] + "_muted", FILL, mc -> look(mc, grove, Level.OVERWORLD, side, true)));
        }
        steps.add(new Step("grove_muted_list", 40, mc -> {
            MuterScreen screen = new MuterScreen(new InventoryScreen(mc.player));
            mc.gui.setScreen(screen);
            screen.showForPicture("muted", "");
        }));
        steps.add(new Step(null, 20, mc -> {
            mc.gui.setScreen(null);
            Settings.unmuteAll();
            Muting.apply();
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> findDeltas(server));
        }));
        steps.add(new Step(null, SETTLE, mc -> {}));               // (the deltas found and their chunks made)
        for (int i = 0; i < 4; i++) {
            int side = i;
            steps.add(new Step("deltas_" + sides[i], FILL, mc -> look(mc, deltas, Level.NETHER, side, false)));
        }
        steps.add(new Step("deltas_recent", 60, mc -> {
            MuterScreen screen = new MuterScreen(new InventoryScreen(mc.player));
            mc.gui.setScreen(screen);
            screen.showForPicture("recent", "");
        }));
        steps.add(new Step(null, 20, mc -> {
            mc.gui.setScreen(null);
            mute(mc, "minecraft:white_ash");
        }));
        for (int i = 0; i < 4; i++) {
            int side = i;
            steps.add(new Step("deltas_" + sides[i] + "_muted", FILL, mc -> look(mc, deltas, Level.NETHER, side, true)));
        }
        steps.add(new Step(null, 20, mc -> {
            Settings.unmuteAll();
            Settings.save();
            Muting.apply();
        }));
    }

    /** A particle muted as a player mutes it from the list, the ones already flying cleared as closing the list does. */
    private static void mute(Minecraft mc, String id) {
        for (Catalog.Entry e : Catalog.entries()) {
            if (e.id.equals(id)) {
                e.setMuted(true);
            }
        }
        Muting.apply();
        Muting.clearLiving();
        ParticleMuter.LOGGER.info("Showcase: muted {}", id);
    }

    /** The camera at a place, standing, looking out to one side (0 south, 1 west, 2 north, 3 east). */
    private static void look(Minecraft mc, Vec3 at, net.minecraft.resources.ResourceKey<Level> dimension, int side, boolean again) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (at == null) {
            return;
        }
        float yaw = side * 90f;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            ServerLevel level = server.getLevel(dimension);
            player.teleportTo(level, at.x, at.y, at.z, Set.of(), yaw, -4f, true);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
    }

    private static void gameMode(Minecraft mc, GameType mode) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getPlayerList().getPlayers().getFirst().setGameMode(mode));
    }

    /** On the server: steady light and weather, then the cherry grove nearest the spawn, flown to. */
    private static void findGrove(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (String c : List.of("gamerule advance_time false", "gamerule advance_weather false", "weather clear", "time set 6000",
                "gamerule spawn_mobs false")) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c);
        }
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        var found = level.findClosestBiome3d(b -> b.is(Biomes.CHERRY_GROVE), player.blockPosition(), 6400, 32, 64);
        if (found == null) {
            ParticleMuter.LOGGER.error("Showcase: no cherry grove near the spawn (seed {})", SEED);
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
            return;
        }
        BlockPos p = found.getFirst();
        level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        grove = new Vec3(p.getX() + 0.5, ground + 0.4, p.getZ() + 0.5);
        ParticleMuter.LOGGER.info("Showcase: seed {}, cherry grove at {} {} {}, {} blocks from the spawn", SEED, p.getX(), ground, p.getZ(),
                (int) Math.sqrt(p.distSqr(player.blockPosition())));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, grove.x, grove.y, grove.z, Set.of(), 0f, -4f, true);
        // (the grove's chunks made and drawn before the first picture)
        Minecraft.getInstance().execute(() -> {
            steps.add(0, new Step(null, SETTLE, mc -> {}));
            groveReady = true;
        });
    }

    /** On the server: the basalt deltas nearest the Nether's middle, and a place to stand in the open there. */
    private static void findDeltas(MinecraftServer server) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        var found = nether.findClosestBiome3d(b -> b.is(Biomes.BASALT_DELTAS), new BlockPos(0, 64, 0), 3200, 32, 32);
        if (found == null) {
            ParticleMuter.LOGGER.error("Showcase: no basalt deltas in the Nether (seed {})", SEED);
            return;
        }
        BlockPos p = found.getFirst();
        nether.getChunk(p.getX() >> 4, p.getZ() >> 4);
        // Standing ground: the highest solid block below the roof with four blocks of air over it.
        int stand = p.getY();
        for (int y = 110; y > 32; y--) {
            BlockPos at = new BlockPos(p.getX(), y, p.getZ());
            if (!nether.getBlockState(at).isAir() && nether.getBlockState(at.above()).isAir() && nether.getBlockState(at.above(2)).isAir()
                    && nether.getBlockState(at.above(3)).isAir() && nether.getBlockState(at.above(4)).isAir()) {
                stand = y + 1;
                break;
            }
        }
        deltas = new Vec3(p.getX() + 0.5, stand + 0.4, p.getZ() + 0.5);
        ParticleMuter.LOGGER.info("Showcase: basalt deltas at {} {} {}", p.getX(), stand, p.getZ());
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.teleportTo(nether, deltas.x, deltas.y, deltas.z, Set.of(), 0f, -4f, true);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
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
