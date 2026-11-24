package dev.particlemuter.mixin;

import java.util.Map;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The engine's sprite sets (each particle type's pictures, by id) and its clearing, private on this version. */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Accessor("spriteSets")
    Map<ResourceLocation, SpriteSet> particlemuter$spriteSets();

    @Invoker("clearParticles")
    void particlemuter$clear();
}
