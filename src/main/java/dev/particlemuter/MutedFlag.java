package dev.particlemuter;

/**
 * What each particle type carries for the mod (mixed into {@code ParticleType} by {@code mixin/ParticleTypeMixin}):
 * whether it is muted, and its place in the {@link Catalog} (for the counts), so the check for each particle spawned is
 * two field reads, with no lookup.
 */
public interface MutedFlag {
    boolean particlemuter$muted();

    void particlemuter$setMuted(boolean muted);

    /** Its place in the catalog, or -1 before the catalog is made. */
    int particlemuter$index();

    void particlemuter$setIndex(int index);
}
