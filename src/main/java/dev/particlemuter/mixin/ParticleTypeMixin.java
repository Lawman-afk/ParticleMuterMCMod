package dev.particlemuter.mixin;

import dev.particlemuter.MutedFlag;
import net.minecraft.core.particles.ParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Each particle type carries whether it is muted and its place in the catalog ({@link MutedFlag}). */
@Mixin(ParticleType.class)
abstract class ParticleTypeMixin implements MutedFlag {
    @Unique
    private boolean particlemuter$muted;
    @Unique
    private int particlemuter$index = -1;

    @Override
    public boolean particlemuter$muted() {
        return particlemuter$muted;
    }

    @Override
    public void particlemuter$setMuted(boolean muted) {
        particlemuter$muted = muted;
    }

    @Override
    public int particlemuter$index() {
        return particlemuter$index;
    }

    @Override
    public void particlemuter$setIndex(int index) {
        particlemuter$index = index;
    }
}
