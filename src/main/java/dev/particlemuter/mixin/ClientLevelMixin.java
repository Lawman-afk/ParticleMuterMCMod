package dev.particlemuter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.particlemuter.Muting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The block particles, which are made without a type: the bits of a broken block (vanilla's and a mod block's own
 * effect alike), and the bits flying off a block being mined (its hit sound is kept).
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
    private void particlemuter$blockBreaking(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!state.isAir() && !Muting.keepBlockBreaking()) {
            ci.cancel();
        }
    }

    /** A muted hit is "handled" with nothing made, so neither a mod's effect nor vanilla's bits are made. */
    @WrapOperation(method = "addBreakingBlockEffects", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/extensions/common/IClientBlockExtensions;addHitEffects(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/client/particle/ParticleEngine;)Z"))
    private boolean particlemuter$blockHits(IClientBlockExtensions block, BlockState state, Level level, BlockPos pos, Direction face,
            ParticleEngine engine, Operation<Boolean> original) {
        return !Muting.keepBlockHits() || original.call(block, state, level, pos, face, engine);
    }
}
