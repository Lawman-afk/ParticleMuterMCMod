package dev.particlemuter.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;

import dev.particlemuter.Muting;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every particle made from a type (by the world, the server, emitters, other particles and nearly every mod) comes
 * through {@code createParticle}: a muted one is made but never added, so it is never ticked or drawn. The caller still
 * gets its particle (fireworks use theirs at once, with no check for null).
 */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
    @WrapWithCondition(method = "createParticle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;add(Lnet/minecraft/client/particle/Particle;)V"))
    private boolean particlemuter$keep(ParticleEngine engine, Particle particle, @Local(argsOnly = true) ParticleOptions options) {
        return Muting.keep(options.getType());
    }
}
