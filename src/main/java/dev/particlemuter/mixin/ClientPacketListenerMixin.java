package dev.particlemuter.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import dev.particlemuter.Muting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** An item or orb flying to the player who picks it up (a particle made without a type). Its sound is kept. */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    @WrapWithCondition(method = "handleTakeItemEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;add(Lnet/minecraft/client/particle/Particle;)V"))
    private boolean particlemuter$itemPickup(ParticleEngine engine, Particle particle) {
        return Muting.keepItemPickup();
    }
}
