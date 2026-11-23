package dev.particlemuter.client;

import java.util.HashMap;
import java.util.Map;

import dev.particlemuter.Catalog;
import dev.particlemuter.ParticleMuter;
import dev.particlemuter.mixin.ParticleEngineAccessor;
import dev.particlemuter.mixin.ParticleResourcesAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Each entry's picture: the first sprite of its particle (what it looks like in the world), or, for particles drawn
 * some other way (a block's bits, an item's, an explosion's), an item that stands for it; the mod's own button sheet
 * for the toggles.
 */
public final class Icons {
    /** The button in four states: muted, muted under the mouse, not muted, not muted under the mouse (11x11 each). */
    public static final Identifier SHEET = ParticleMuter.id("textures/gui/icons.png");
    public static final int SIZE = 11;

    /** Items for the particles with no sprite of their own (by id; ones missing on this version are skipped). */
    private static final Map<String, String> STAND_INS = Map.ofEntries(
            Map.entry("particlemuter:block_breaking", "minecraft:grass_block"),
            Map.entry("particlemuter:block_hits", "minecraft:iron_pickaxe"),
            Map.entry("particlemuter:item_pickup", "minecraft:hopper"),
            Map.entry("minecraft:block", "minecraft:dirt"),
            Map.entry("minecraft:block_marker", "minecraft:barrier"),
            Map.entry("minecraft:block_crumble", "minecraft:creaking_heart"),
            Map.entry("minecraft:dust_pillar", "minecraft:mace"),
            Map.entry("minecraft:falling_dust", "minecraft:sand"),
            Map.entry("minecraft:item", "minecraft:stick"),
            Map.entry("minecraft:item_slime", "minecraft:slime_ball"),
            Map.entry("minecraft:item_snowball", "minecraft:snowball"),
            Map.entry("minecraft:item_cobweb", "minecraft:cobweb"),
            Map.entry("minecraft:explosion_emitter", "minecraft:tnt"),
            Map.entry("minecraft:gust_emitter_large", "minecraft:wind_charge"),
            Map.entry("minecraft:gust_emitter_small", "minecraft:wind_charge"),
            Map.entry("minecraft:elder_guardian", "minecraft:elder_guardian_spawn_egg"),
            Map.entry("minecraft:noxious_gas_cloud", "minecraft:fermented_spider_eye"),
            Map.entry("minecraft:geyser", "minecraft:magma_block"),
            Map.entry("minecraft:vibration", "minecraft:sculk_sensor"),
            Map.entry("minecraft:shriek", "minecraft:sculk_shrieker"));
    private static final Map<String, ItemStack> ITEMS = new HashMap<>();

    private Icons() {}

    /** Draws an entry's picture, 16x16 at (x, y); dimmed when muted. */
    public static void draw(GuiGraphicsExtractor g, Catalog.Entry e, int x, int y, boolean dim) {
        TextureAtlasSprite sprite = e.type == null ? null : sprite(e.id);
        if (sprite != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, dim ? 0x66FFFFFF : 0xFFFFFFFF);
            return;
        }
        ItemStack item = item(e.id);
        if (!item.isEmpty()) {
            g.fakeItem(item, x, y);
            if (dim) {
                g.nextStratum();                                // (over the item, not under it)
                g.fill(x, y, x + 16, y + 16, 0x99202020);
            }
            return;
        }
        // No picture at all (a mod's particle drawn its own way): a plain square with a dot.
        g.fill(x + 3, y + 3, x + 13, y + 13, dim ? 0x66808080 : 0xFF808080);
        g.fill(x + 7, y + 7, x + 9, y + 9, dim ? 0x66FFFFFF : 0xFFFFFFFF);
    }

    /** The toggle: the slashed button when muted, the plain one when not. */
    public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean muted, boolean hover) {
        int u = (muted ? 0 : 22) + (hover ? 11 : 0);
        g.blit(RenderPipelines.GUI_TEXTURED, SHEET, x, y, u, 0, SIZE, SIZE, 64, 16);
    }

    /** The first sprite of that particle's sprite set, or null when it has none (or none loaded). */
    private static TextureAtlasSprite sprite(String id) {
        try {
            var resources = ((ParticleEngineAccessor) Minecraft.getInstance().particleEngine).particlemuter$resources();
            SpriteSet set = ((ParticleResourcesAccessor) resources).particlemuter$spriteSets().get(Identifier.parse(id));
            if (set == null) {
                return null;
            }
            TextureAtlasSprite sprite = set.first();
            return sprite == null || sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? null : sprite;
        } catch (RuntimeException e) {
            return null;                                        // (a set never bound: its particle has no sprites)
        }
    }

    private static ItemStack item(String id) {
        return ITEMS.computeIfAbsent(id, k -> {
            String itemId = STAND_INS.get(k);
            if (itemId == null) {
                return ItemStack.EMPTY;
            }
            Identifier key = Identifier.parse(itemId);
            Item item = BuiltInRegistries.ITEM.containsKey(key) ? BuiltInRegistries.ITEM.getValue(key) : null;
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        });
    }
}
