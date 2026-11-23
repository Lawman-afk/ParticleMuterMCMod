package dev.particlemuter;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * The checks the mixins make as each particle is made: the hot path. Each particle type carries its own flag
 * ({@link MutedFlag}), so a check is a field read or two and never a lookup; nothing is allocated. Every particle is
 * counted (muted or not, so the screen shows what a muted one would be doing).
 */
public final class Muting {
    /** Whether any typed particle is hidden now (the master switch on and something muted). */
    private static boolean active;
    /** The three kinds without a type, hidden now. */
    private static boolean blockBreaking, blockHits, itemPickup;

    private Muting() {}

    /** Whether a particle of this type is kept (drawn): false when muted. Counts it either way. */
    public static boolean keep(ParticleType<?> type) {
        MutedFlag flag = (MutedFlag) type;
        Tracker.count(flag.particlemuter$index());
        return !(active && flag.particlemuter$muted());
    }

    /** Whether a broken block's bits are made. */
    public static boolean keepBlockBreaking() {
        Tracker.count(Catalog.BLOCK_BREAKING);
        return !blockBreaking;
    }

    /** Whether the bits flying off a block being mined are made. */
    public static boolean keepBlockHits() {
        Tracker.count(Catalog.BLOCK_HITS);
        return !blockHits;
    }

    /** Whether an item flies to the player picking it up. */
    public static boolean keepItemPickup() {
        Tracker.count(Catalog.ITEM_PICKUP);
        return !itemPickup;
    }

    /** Puts the settings into the types' flags (after loading them, and after each change on the screen). */
    public static void apply() {
        boolean any = false;
        for (ParticleType<?> type : BuiltInRegistries.PARTICLE_TYPE) {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
            boolean muted = id != null && Settings.isMuted(id.toString());
            ((MutedFlag) type).particlemuter$setMuted(muted);
            any |= muted;
        }
        boolean on = Settings.enabled();
        active = on && any;
        blockBreaking = on && Settings.blockBreaking();
        blockHits = on && Settings.blockHits();
        itemPickup = on && Settings.itemPickup();
    }

    /** Takes away the particles already flying (after something was newly muted, so it goes at once, not as they fade). */
    public static void clearLiving() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.particleEngine.clearParticles();
        }
    }
}
