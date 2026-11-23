package dev.particlemuter.mixin;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The engine's loaded particle resources (the sprite sets the screen shows each particle by). */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Accessor("resourceManager")
    ParticleResources particlemuter$resources();
}
