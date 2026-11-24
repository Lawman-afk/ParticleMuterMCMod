package dev.particlemuter.port;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft 26.x's GUI graphics as main's screens draw with them, over 1.21.1's {@link GuiGraphics} (this branch's own:
 * only what main uses of it).
 */
public final class GuiGraphicsExtractor {
    private final GuiGraphics g;

    public GuiGraphicsExtractor(GuiGraphics g) {
        this.g = g;
    }

    public GuiGraphics unwrap() {
        return g;
    }

    public void fill(int x0, int y0, int x1, int y1, int col) {
        g.fill(x0, y0, x1, y1, col);
    }

    public void text(Font font, String str, int x, int y, int color) {
        g.drawString(font, str, x, y, color);
    }

    public void text(Font font, String str, int x, int y, int color, boolean dropShadow) {
        g.drawString(font, str, x, y, color, dropShadow);
    }

    public void text(Font font, Component str, int x, int y, int color) {
        g.drawString(font, str, x, y, color);
    }

    public void text(Font font, Component str, int x, int y, int color, boolean dropShadow) {
        g.drawString(font, str, x, y, color, dropShadow);
    }

    public void centeredText(Font font, FormattedCharSequence text, int x, int y, int color) {
        g.drawCenteredString(font, text, x, y, color);
    }

    /** A sprite tinted by {@code color} (ARGB; its alpha fades it). */
    public void blitSprite(Object pipeline, TextureAtlasSprite sprite, int x, int y, int w, int h, int color) {
        g.blit(x, y, 0, w, h, sprite, (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f,
                (color >>> 24) / 255f);
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int w, int h, int texW, int texH) {
        g.blit(texture, x, y, u, v, w, h, texW, texH);
    }

    public void fakeItem(ItemStack stack, int x, int y) {
        g.renderFakeItem(stack, x, y);
    }

    /** What is drawn next goes over what was drawn before (items included). */
    public void nextStratum() {
        g.pose().translate(0, 0, 200);
    }

    public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int x, int y) {
        g.renderComponentTooltip(font, lines, x, y);
    }
}
