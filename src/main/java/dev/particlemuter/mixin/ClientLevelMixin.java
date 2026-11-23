package dev.particlemuter.mixin;

import dev.particlemuter.Muting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
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

    /** The bits off a block being mined (a mod block's own effect too): this method makes only those (no sound). */
    @Inject(method = "addBreakingBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/phys/HitResult;)V",
            at = @At("HEAD"), cancellable = true)
    private void particlemuter$blockHits(BlockPos pos, Direction face, net.minecraft.world.phys.HitResult hit, CallbackInfo ci) {
        if (!Muting.keepBlockHits()) {
            ci.cancel();
        }
    }
}
