package dev.particlemuter.mixin;

import dev.particlemuter.Muting;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The block particles, which are made without a type (on this version the engine makes them): the bits of a broken
 * block (vanilla's and a mod block's own effect alike), and the bits flying off a block being mined (this branch's own).
 */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineBlockMixin {
    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void particlemuter$blockBreaking(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!state.isAir() && !Muting.keepBlockBreaking()) {
            ci.cancel();
        }
    }

    /** A mined block's hit, a mod block's own effect too (counted here only when muted: else in crack, which follows). */
    @Inject(method = "addBlockHitEffects", at = @At("HEAD"), cancellable = true)
    private void particlemuter$blockHitEffects(BlockPos pos, BlockHitResult target, CallbackInfo ci) {
        if (Muting.hidesBlockHits()) {
            Muting.keepBlockHits();
            ci.cancel();
        }
    }

    @Inject(method = "crack", at = @At("HEAD"), cancellable = true)
    private void particlemuter$blockHits(BlockPos pos, Direction side, CallbackInfo ci) {
        if (!Muting.keepBlockHits()) {
            ci.cancel();
        }
    }
}
