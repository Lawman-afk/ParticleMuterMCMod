package dev.particlemuter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * The player's settings, in {@code config/particlemuter.json} (the same file on every version and loader): the muted
 * particle types by id, the three kinds of particle that have no type of their own, the master switch, and where the
 * inventory button goes. Ids of mods that are not installed are kept, so taking a mod out for a while loses nothing.
 */
public final class Settings {
    /** The file's shape, as Gson reads and writes it. */
    private static final class Data {
        int version = 1;
        /** Whether muted particles are hidden (the screen's master switch): off shows them all without losing the list. */
        boolean enabled = true;
        List<String> muted = new ArrayList<>();
        boolean blockBreaking = false;
        boolean blockHits = false;
        boolean itemPickup = false;
        Button button = new Button();
    }

    /** The inventory button: shown or not, and a place the player dragged it to (null: beside the sound mod's, or in its place). */
    private static final class Button {
        boolean show = true;
        int[] survival = null;
        int[] creative = null;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static final Set<String> MUTED = new TreeSet<>();

    private Settings() {}

    private static Path file() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(ParticleMuter.MODID + ".json");
    }

    /** Reads the file (a missing one gives the defaults; a broken one is kept as .bak and the defaults used). */
    public static void load() {
        Path file = file();
        data = new Data();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Data read = GSON.fromJson(in, Data.class);
                if (read != null) {
                    data = read;
                }
            } catch (IOException | JsonParseException e) {
                ParticleMuter.LOGGER.warn("Particle Muter: could not read {}; starting from the defaults (the old file kept as .bak)", file, e);
                try {
                    Files.move(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // (left as it is: it is written over on the next save)
                }
            }
        }
        if (data.muted == null) {
            data.muted = new ArrayList<>();
        }
        if (data.button == null) {
            data.button = new Button();
        }
        MUTED.clear();
        for (String id : data.muted) {
            if (id != null && !id.isBlank()) {
                MUTED.add(id.trim());
            }
        }
    }

    /** Writes the file (through a temporary file, so a crash mid-write never leaves half a file). */
    public static void save() {
        data.muted = new ArrayList<>(MUTED);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, out);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ParticleMuter.LOGGER.warn("Particle Muter: could not save {}", file, e);
        }
    }

    public static boolean isMuted(String id) {
        return MUTED.contains(id);
    }

    public static void setMuted(String id, boolean muted) {
        if (muted) {
            MUTED.add(id);
        } else {
            MUTED.remove(id);
        }
    }

    /** Every muted id, the uninstalled mods' too. */
    public static Set<String> muted() {
        return MUTED;
    }

    public static void unmuteAll() {
        MUTED.clear();
        data.blockBreaking = data.blockHits = data.itemPickup = false;
    }

    public static boolean enabled() {
        return data.enabled;
    }

    public static void setEnabled(boolean on) {
        data.enabled = on;
    }

    public static boolean blockBreaking() {
        return data.blockBreaking;
    }

    public static void setBlockBreaking(boolean muted) {
        data.blockBreaking = muted;
    }

    public static boolean blockHits() {
        return data.blockHits;
    }

    public static void setBlockHits(boolean muted) {
        data.blockHits = muted;
    }

    public static boolean itemPickup() {
        return data.itemPickup;
    }

    public static void setItemPickup(boolean muted) {
        data.itemPickup = muted;
    }

    public static boolean showButton() {
        return data.button.show;
    }

    public static void setShowButton(boolean show) {
        data.button.show = show;
    }

    /** Where the player dragged the button to on that inventory (from its left and top), or null for the default place. */
    public static int[] buttonPlace(boolean creative) {
        int[] at = creative ? data.button.creative : data.button.survival;
        return at != null && at.length == 2 ? at : null;
    }

    public static void setButtonPlace(boolean creative, int[] at) {
        if (creative) {
            data.button.creative = at;
        } else {
            data.button.survival = at;
        }
    }
}
