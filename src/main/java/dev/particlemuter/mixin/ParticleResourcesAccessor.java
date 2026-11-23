package dev.particlemuter.mixin;

import java.util.Map;

import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Each particle type's sprites, by its id (types drawn some other way have none). */
@Mixin(ParticleResources.class)
public interface ParticleResourcesAccessor {
    @Accessor("spriteSets")
    Map<Identifier, SpriteSet> particlemuter$spriteSets();
}
