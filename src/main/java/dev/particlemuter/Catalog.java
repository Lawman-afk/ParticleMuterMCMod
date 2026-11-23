package dev.particlemuter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;

/**
 * Every particle the player can mute: each registered particle type, Minecraft's and every mod's, plus the three kinds
 * of particle that are made without a type (the bits of a block broken, the bits flying off a block being mined, and
 * items and orbs flying to a player who picks them up). Each has its readable name, its mod and a place (its index)
 * for the counts. Made once the registries are frozen; the names are looked up again when the language changes.
 */
public final class Catalog {
    public enum Kind { TYPE, BLOCK_BREAKING, BLOCK_HITS, ITEM_PICKUP }

    /** One row of the list. {@code type} is null for the three kinds without a type. */
    public static final class Entry {
        public final Kind kind;
        public final String id;
        public final String namespace;
        public final ParticleType<?> type;
        public final int index;
        private String name, modName, search;

        Entry(Kind kind, String id, String namespace, ParticleType<?> type, int index) {
            this.kind = kind;
            this.id = id;
            this.namespace = namespace;
            this.type = type;
            this.index = index;
        }

        public String name() {
            return name;
        }

        public String modName() {
            return modName;
        }

        /** Whether the player muted it (whatever the master switch says). */
        public boolean muted() {
            return switch (kind) {
                case TYPE -> Settings.isMuted(id);
                case BLOCK_BREAKING -> Settings.blockBreaking();
                case BLOCK_HITS -> Settings.blockHits();
                case ITEM_PICKUP -> Settings.itemPickup();
            };
        }

        public void setMuted(boolean muted) {
            switch (kind) {
                case TYPE -> Settings.setMuted(id, muted);
                case BLOCK_BREAKING -> Settings.setBlockBreaking(muted);
                case BLOCK_HITS -> Settings.setBlockHits(muted);
                case ITEM_PICKUP -> Settings.setItemPickup(muted);
            }
        }

        /** Whether a search (already lower case) finds it: in its name, its id or its mod's name. */
        public boolean matches(String query) {
            return query.isEmpty() || search.contains(query);
        }
    }

    /** The places of the three kinds without a type; the types follow them. */
    public static final int BLOCK_BREAKING = 0, BLOCK_HITS = 1, ITEM_PICKUP = 2, FIRST_TYPE = 3;

    private static List<Entry> entries = List.of();
    private static String language = "";

    private Catalog() {}

    /** Every entry, in their index order (the three special ones first, then the types in registry order). */
    public static List<Entry> entries() {
        refreshNames();
        return entries;
    }

    public static int size() {
        return entries.size();
    }

    public static boolean ready() {
        return !entries.isEmpty();
    }

    /** Makes the catalog: gives each particle type its index (once the registries are frozen, at client setup). */
    public static void build() {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry(Kind.BLOCK_BREAKING, "particlemuter:block_breaking", "minecraft", null, BLOCK_BREAKING));
        list.add(new Entry(Kind.BLOCK_HITS, "particlemuter:block_hits", "minecraft", null, BLOCK_HITS));
        list.add(new Entry(Kind.ITEM_PICKUP, "particlemuter:item_pickup", "minecraft", null, ITEM_PICKUP));
        for (ParticleType<?> type : BuiltInRegistries.PARTICLE_TYPE) {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
            if (id == null) {
                continue;
            }
            int index = list.size();
            ((MutedFlag) type).particlemuter$setIndex(index);
            list.add(new Entry(Kind.TYPE, id.toString(), id.getNamespace(), type, index));
        }
        entries = Collections.unmodifiableList(list);
        language = "";
        Tracker.resize(list.size());
        Muting.apply();
    }

    /** The names in the game's language (looked up again when it changes). */
    private static void refreshNames() {
        String now = net.minecraft.client.Minecraft.getInstance().options.languageCode;
        if (now.equals(language)) {
            return;
        }
        language = now;
        Map<String, String> modNames = new HashMap<>();
        for (Entry e : entries) {
            e.name = nameOf(e);
            e.modName = modNames.computeIfAbsent(e.namespace, Catalog::modName);
            e.search = (e.name + " " + e.id + " " + e.modName).toLowerCase(Locale.ROOT);
        }
    }

    private static String nameOf(Entry e) {
        Language lang = Language.getInstance();
        if (e.kind != Kind.TYPE) {
            return lang.getOrDefault("particlemuter.special." + e.id.substring(e.id.indexOf(':') + 1));
        }
        int colon = e.id.indexOf(':');
        String ns = e.id.substring(0, colon), path = e.id.substring(colon + 1);
        // Our own names; then the names a mod gave its particles for Sodium Extra's list; then the id made readable.
        for (String key : new String[] {"particlemuter.particle." + ns + "." + path.replace('/', '.'),
                "options.particles." + ns + "." + path.replace('/', '.')}) {
            if (lang.has(key)) {
                return lang.getOrDefault(key);
            }
        }
        return readable(path);
    }

    /** {@code soul_fire_flame} as "Soul fire flame". */
    public static String readable(String path) {
        String words = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ').replace('.', ' ').trim();
        return words.isEmpty() ? path : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** A mod's name as its mods.toml gives it (Minecraft's own as "Minecraft"). */
    public static String modName(String namespace) {
        if (namespace.equals("minecraft")) {
            return "Minecraft";
        }
        return net.neoforged.fml.ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName())
                .orElse(readable(namespace));
    }

    /** The entries grouped by mod (Minecraft first, then by mod name), each group in name order. */
    public static List<Entry> byMod(List<Entry> from) {
        List<Entry> list = new ArrayList<>(from);
        list.sort(Comparator.<Entry>comparingInt(e -> e.namespace.equals("minecraft") ? 0 : 1)
                .thenComparing(e -> e.modName().toLowerCase(Locale.ROOT))
                .thenComparingInt(e -> e.kind == Kind.TYPE ? 1 : 0)
                .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return list;
    }
}
