package dev.particlemuter.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.particlemuter.Settings;
import net.minecraft.client.Minecraft;
import dev.particlemuter.port.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import dev.particlemuter.port.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The 11x11 button on the survival inventory and on the creative inventory's own tab, where the sound mod (Extreme
 * Sound Muffler) puts its: in its place when that mod is not installed, one button below its when it is (following
 * it wherever the player moved it). Its place is worked out again every frame from the inventory's own, so it moves
 * with the recipe book. A left click opens the list; the right button drags it somewhere else (kept in the settings).
 */
public final class InventoryButton extends AbstractWidget {
    /** The sound mod's place for its button: on the survival inventory, and on the creative inventory's tab. */
    private static final int SURVIVAL_X = 75, SURVIVAL_Y = 7, CREATIVE_X = 2, CREATIVE_Y = 2;
    /** How far below the sound mod's button ours goes (its 11 pixels and a gap of 1). */
    private static final int BELOW = 12;
    private static final String SOUND_MOD = "com.leobeliik.extremesoundmuffler";

    private final AbstractContainerScreen<?> screen;
    private final boolean creative;
    private boolean dragging;

    private InventoryButton(AbstractContainerScreen<?> screen, boolean creative) {
        super(0, 0, Icons.SIZE, Icons.SIZE, Component.translatable("particlemuter.button"));
        this.screen = screen;
        this.creative = creative;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
            Screen screen = event.getScreen();
            // Exactly the two inventories (not mods' screens built on them).
            boolean creative = screen.getClass() == CreativeModeInventoryScreen.class;
            if (!creative && screen.getClass() != InventoryScreen.class) {
                return;
            }
            InventoryButton button = new InventoryButton((AbstractContainerScreen<?>) screen, creative);
            button.place(-1, -1);
            event.addListener(button);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre event) -> {
            InventoryButton button = of(event.getScreen());
            if (button != null) {
                button.place(event.getMouseX(), event.getMouseY());
            }
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonReleased.Pre event) -> {
            InventoryButton button = of(event.getScreen());
            if (button != null && button.dragging) {
                button.dragging = false;
                Settings.setButtonPlace(button.creative, new int[] {button.getX() - button.left(), button.getY() - button.top()});
                Settings.save();
                event.setCanceled(true);
            }
        });
    }

    /**
     * That screen's own button, if it has one. (Each screen keeps its own: in creative the game sets up the survival
     * inventory first, which opens the creative one from inside its own setting up, so the last button made is not
     * always the one on the screen.)
     */
    public static InventoryButton of(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof InventoryButton button) {
                return button;
            }
        }
        return null;
    }

    private int left() {
        return screen.getGuiLeft();
    }

    private int top() {
        return screen.getGuiTop();
    }

    /** Where it is this frame, and whether it shows (the creative inventory: only on its own tab). */
    private void place(int mouseX, int mouseY) {
        visible = Settings.showButton() && (!creative || ((CreativeModeInventoryScreen) screen).isInventoryOpen());
        if (dragging && mouseX >= 0) {
            setX(mouseX - Icons.SIZE / 2);
            setY(mouseY - Icons.SIZE / 2);
            return;
        }
        int[] dragged = Settings.buttonPlace(creative);
        if (dragged != null) {
            setX(left() + dragged[0]);
            setY(top() + dragged[1]);
            return;
        }
        AbstractWidget sound = soundModButton();
        if (sound != null) {
            setX(sound.getX());
            setY(sound.getY() + BELOW);
        } else {
            setX(left() + (creative ? CREATIVE_X : SURVIVAL_X));
            setY(top() + (creative ? CREATIVE_Y : SURVIVAL_Y));
        }
    }

    /** The sound mod's own button on this screen, when it is there and showing. */
    private AbstractWidget soundModButton() {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget != this && widget.visible
                    && widget.getClass().getName().startsWith(SOUND_MOD)) {
                return widget;
            }
        }
        return null;
    }

    @Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(graphics);
        boolean hover = isHovered() || dragging;
        Icons.toggle(g, getX(), getY(), true, hover);
        if (isHovered() && !dragging) {
            // Its name just below it, as the sound mod shows its.
            Minecraft mc = Minecraft.getInstance();
            Component name = getMessage();
            int w = mc.font.width(name);
            int x = getX() + Icons.SIZE / 2 - w / 2, y = getY() + Icons.SIZE + 2;
            g.nextStratum();
            g.fill(x - 2, y - 2, x + w + 2, y + 10, 0xC0000000);
            g.text(mc.font, name, x, y, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX_, double mouseY_, int button_) {
        MouseButtonEvent event = new MouseButtonEvent(mouseX_, mouseY_, button_);
        if (!active || !visible || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            Minecraft.getInstance().setScreen(new MuterScreen(screen));
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
