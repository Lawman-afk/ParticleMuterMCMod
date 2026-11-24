package dev.particlemuter.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import dev.particlemuter.Catalog;
import dev.particlemuter.Muting;
import dev.particlemuter.Settings;
import dev.particlemuter.Tracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import dev.particlemuter.port.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import dev.particlemuter.port.input.KeyEvent;
import dev.particlemuter.port.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The list of particles, opened from the inventory button (or its key, or the mod list): every particle the game can
 * make, Minecraft's and every mod's, each with its picture, its name and how many are being made around the player
 * each second; a click mutes or unmutes it. Laid out as the sound mod's screen is: the lists along the top (Recent, the
 * ones being made lately, busiest first; All, by mod; Muted; Mods, a whole mod at once), the list, then the search and
 * the switches along the bottom. It does not pause the game, so a change shows in the world behind it at once.
 */
public final class MuterScreen extends Screen {
    private enum View { RECENT, ALL, MUTED, MODS }

    private static final int W = 256, H = 232, ROW = 18, ROWS = 9;
    private static final int LIST_X = 8, LIST_Y = 40, LIST_W = 240, BAR_W = 6;
    private static final int TEXT_DARK = 0xFF404040, TEXT = 0xFFFFFFFF, MUTED_TEXT = 0xFF55FF55, HOVER_TEXT = 0xFF55FFFF,
            GREY = 0xFFA0A0A0, HEADER = 0xFFFFAA00;
    /** The list shown last time, kept for the next opening. */
    private static View view = View.ALL;

    /** A row: one entry, or a mod (its header in All, its row in Mods) with the entries under it. */
    private record Row(Catalog.Entry entry, String mod, List<Catalog.Entry> group) {
        boolean header() {
            return entry == null;
        }
    }

    private final Screen parent;
    private final Map<View, Button> tabs = new LinkedHashMap<>();
    private EditBox search;
    private Button master, unmuteAll;
    private List<Row> rows = List.of();
    private int x, y, scroll;
    private boolean draggingBar, confirmUnmute, newlyMuted;
    private String query = "";
    /** Ticks until the Recent list is sorted again (its order follows the counts, but not every frame). */
    private int resort;

    public MuterScreen(Screen parent) {
        super(Component.translatable("particlemuter.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        x = (width - W) / 2;
        y = (height - H) / 2;
        tabs.clear();
        int tx = x + 8;
        for (View v : View.values()) {
            Button b = Button.builder(Component.translatable("particlemuter.view." + v.name().toLowerCase(Locale.ROOT)), btn -> show(v))
                    .bounds(tx, y + 19, 57, 16)
                    .tooltip(Tooltip.create(Component.translatable("particlemuter.view." + v.name().toLowerCase(Locale.ROOT) + ".tip")))
                    .build();
            tabs.put(v, addRenderableWidget(b));
            tx += 61;
        }
        String old = search == null ? query : search.getValue();
        search = new EditBox(font, x + 9, y + 209, 124, 14, Component.translatable("particlemuter.search"));
        search.setHint(Component.translatable("particlemuter.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(50);
        search.setValue(old);
        search.setResponder(text -> {
            query = text.trim().toLowerCase(Locale.ROOT);
            scroll = 0;
            rebuild();
        });
        addRenderableWidget(search);
        master = addRenderableWidget(Button.builder(Component.empty(), b -> {
            Settings.setEnabled(!Settings.enabled());
            changed();
        }).bounds(x + 138, y + 208, 52, 16).build());
        unmuteAll = addRenderableWidget(Button.builder(Component.translatable("particlemuter.unmute_all"), b -> {
            if (!confirmUnmute) {
                confirmUnmute = true;                           // a second click to be sure
            } else {
                confirmUnmute = false;
                Settings.unmuteAll();
                changed();
            }
            labels();
        }).bounds(x + 194, y + 208, 54, 16).build());
        labels();
        rebuild();
    }

    private void show(View v) {
        view = v;
        scroll = 0;
        rebuild();
        labels();
    }

    /** The buttons' words, which say what they are set to. */
    private void labels() {
        for (var tab : tabs.entrySet()) {
            String key = "particlemuter.view." + tab.getKey().name().toLowerCase(Locale.ROOT);
            tab.getValue().setMessage(tab.getKey() == view ? Component.translatable(key).withStyle(ChatFormatting.GREEN) : Component.translatable(key));
        }
        boolean on = Settings.enabled();
        master.setMessage(Component.translatable(on ? "particlemuter.master.on" : "particlemuter.master.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
        master.setTooltip(Tooltip.create(Component.translatable(on ? "particlemuter.master.on.tip" : "particlemuter.master.off.tip")));
        unmuteAll.setMessage(confirmUnmute ? Component.translatable("particlemuter.unmute_all.sure").withStyle(ChatFormatting.RED)
                : Component.translatable("particlemuter.unmute_all"));
        unmuteAll.setTooltip(Tooltip.create(Component.translatable("particlemuter.unmute_all.tip")));
    }

    /** After any change: the flags set, the file written, the list and words brought up to date. */
    private void changed() {
        Muting.apply();
        Settings.save();
        rebuild();
        labels();
    }

    /** The rows of the list shown, filtered by the search. */
    private void rebuild() {
        List<Catalog.Entry> all = Catalog.entries();
        List<Row> list = new ArrayList<>();
        switch (view) {
            case RECENT -> {
                List<Catalog.Entry> seen = new ArrayList<>();
                for (Catalog.Entry e : all) {
                    if (Tracker.seen(e.index) && e.matches(query)) {
                        seen.add(e);
                    }
                }
                seen.sort(Comparator.<Catalog.Entry>comparingDouble(e -> -Tracker.rate(e.index))
                        .thenComparingInt(e -> Tracker.secondsSince(e.index)));
                seen.forEach(e -> list.add(new Row(e, null, null)));
            }
            case ALL, MUTED -> {
                String mod = null;
                for (Catalog.Entry e : Catalog.byMod(all)) {
                    if ((view == View.MUTED && !e.muted()) || !e.matches(query)) {
                        continue;
                    }
                    if (!e.modName().equals(mod)) {
                        mod = e.modName();
                        list.add(new Row(null, mod, group(all, e.namespace)));
                    }
                    list.add(new Row(e, null, null));
                }
            }
            case MODS -> {
                Map<String, List<Catalog.Entry>> mods = new LinkedHashMap<>();
                for (Catalog.Entry e : Catalog.byMod(all)) {
                    mods.computeIfAbsent(e.namespace, k -> new ArrayList<>()).add(e);
                }
                for (List<Catalog.Entry> group : mods.values()) {
                    String name = group.get(0).modName();
                    if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query) || group.get(0).namespace.contains(query)) {
                        list.add(new Row(null, name, group));
                    }
                }
            }
        }
        rows = list;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        resort = 20;
    }

    private static List<Catalog.Entry> group(List<Catalog.Entry> all, String namespace) {
        List<Catalog.Entry> group = new ArrayList<>();
        for (Catalog.Entry e : all) {
            if (e.namespace.equals(namespace)) {
                group.add(e);
            }
        }
        return group;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - ROWS);
    }

    @Override
    public void tick() {
        if (view == View.RECENT && --resort <= 0) {
            rebuild();                                          // (new particles join, the busiest go to the top)
        }
    }

    // --- drawing ----------------------------------------------------------------------------------------------------

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(new GuiGraphicsExtractor(graphics), mouseX, mouseY);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTooltip(new GuiGraphicsExtractor(graphics), mouseX, mouseY);
    }

    /** The row under the mouse this frame (its tooltip is drawn over everything). */
    private Row hovered;

    /** The panel, its title and the list (under the widgets). */
    private void drawPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        panel(g, x, y, W, H);
        g.text(font, title, x + (W - font.width(title)) / 2, y + 7, TEXT_DARK, false);   // (vanilla's container grey, no shadow)
        int lx = x + LIST_X, ly = y + LIST_Y, lw = LIST_W - BAR_W - 1;
        inset(g, lx - 1, ly - 1, LIST_W + 2, ROWS * ROW + 2);
        g.fill(lx, ly, lx + LIST_W, ly + ROWS * ROW, 0xFF000000);
        Row hovered = null;
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int ry = ly + i * ROW;
            boolean over = mouseX >= lx && mouseX < lx + lw && mouseY >= ry && mouseY < ry + ROW;
            if (over) {
                hovered = row;
            }
            g.fill(lx, ry, lx + lw, ry + ROW, over ? 0xFF2E3350 : (i & 1) == 0 ? 0xFF101010 : 0xFF1C1C1C);
            drawRow(g, row, lx, ry, lw, over, mouseX, mouseY);
        }
        if (rows.isEmpty()) {
            Component empty = Component.translatable(view == View.RECENT && query.isEmpty() ? "particlemuter.empty.recent"
                    : view == View.MUTED && query.isEmpty() ? "particlemuter.empty.muted" : "particlemuter.empty.search");
            List<FormattedCharSequence> lines = font.split(empty, lw - 20);
            int ty = ly + ROWS * ROW / 2 - lines.size() * 5;
            for (FormattedCharSequence line : lines) {
                g.centeredText(font, line, lx + lw / 2, ty, GREY);
                ty += 10;
            }
        }
        scrollbar(g, lx + LIST_W - BAR_W, ly, ROWS * ROW);
        this.hovered = hovered;
    }

    private void drawTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (hovered != null) {
            tooltip(g, hovered, mouseX, mouseY);
        }
    }

    private void drawRow(GuiGraphicsExtractor g, Row row, int rx, int ry, int rw, boolean over, int mouseX, int mouseY) {
        int toggleX = rx + rw - Icons.SIZE - 4, toggleY = ry + (ROW - Icons.SIZE) / 2;
        boolean overToggle = over && mouseX >= toggleX && mouseX < toggleX + Icons.SIZE;
        if (row.header()) {
            int muted = mutedIn(row.group());
            boolean all = muted == row.group().size();
            if (view == View.MODS) {
                Icons.draw(g, picture(row.group()), rx + 2, ry + 1, all);
                g.text(font, row.mod(), rx + 22, ry + 5, all ? MUTED_TEXT : over ? HOVER_TEXT : TEXT);
            } else {
                g.text(font, row.mod(), rx + 4, ry + 5, HEADER);
            }
            Component count = Component.translatable("particlemuter.mod.count", muted, row.group().size());
            if (view == View.MUTED) {
                return;                                          // (a heading only: the mod's switch is in All and Mods)
            }
            g.text(font, count, toggleX - 4 - font.width(count), ry + 5, GREY);
            Icons.toggle(g, toggleX, toggleY, all, overToggle);
            return;
        }
        Catalog.Entry e = row.entry();
        boolean muted = e.muted();
        Icons.draw(g, e, rx + 2, ry + 1, muted);
        String rate = rateText(e);
        int rateW = font.width(rate);
        int nameMax = toggleX - 6 - rateW - (rx + 22);
        String name = e.name();
        if (font.width(name) > nameMax) {
            name = font.plainSubstrByWidth(name, nameMax - font.width("...")) + "...";
        }
        g.text(font, name, rx + 22, ry + 5, muted ? MUTED_TEXT : over ? HOVER_TEXT : TEXT);
        g.text(font, rate, toggleX - 4 - rateW, ry + 5, GREY);
        Icons.toggle(g, toggleX, toggleY, muted, overToggle);
    }

    /** A mod's picture: its first particle with a sprite set of its own, else its first. */
    private static Catalog.Entry picture(List<Catalog.Entry> group) {
        for (Catalog.Entry e : group) {
            if (e.type != null) {
                return e;
            }
        }
        return group.get(0);
    }

    private static String rateText(Catalog.Entry e) {
        float rate = Tracker.rate(e.index);
        if (rate <= 0) {
            return Tracker.seen(e.index) && view == View.RECENT ? "0/s" : "";
        }
        return rate < 1 ? "<1/s" : Math.round(rate) + "/s";
    }

    private static int mutedIn(List<Catalog.Entry> group) {
        int n = 0;
        for (Catalog.Entry e : group) {
            if (e.muted()) {
                n++;
            }
        }
        return n;
    }

    private void tooltip(GuiGraphicsExtractor g, Row row, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (row.header() && view == View.MUTED) {
            return;
        }
        if (row.header()) {
            boolean all = mutedIn(row.group()) == row.group().size();
            lines.add(Component.literal(row.mod()));
            lines.add(Component.translatable(all ? "particlemuter.tip.unmute_mod" : "particlemuter.tip.mute_mod").withStyle(ChatFormatting.GRAY));
        } else {
            Catalog.Entry e = row.entry();
            lines.add(Component.literal(e.name()));
            if (e.type != null) {
                lines.add(Component.literal(e.id).withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.translatable("particlemuter.special." + e.id.substring(e.id.indexOf(':') + 1) + ".tip")
                        .withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.literal(e.modName()).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
            lines.add(Component.translatable(e.muted() ? "particlemuter.tip.unmute" : "particlemuter.tip.mute").withStyle(ChatFormatting.GRAY));
            if (!Settings.enabled() && e.muted()) {
                lines.add(Component.translatable("particlemuter.tip.master_off").withStyle(ChatFormatting.RED));
            }
        }
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    /** A panel in vanilla's container style: light grey, a white edge top and left, a dark one bottom and right. */
    private static void panel(GuiGraphicsExtractor g, int px, int py, int pw, int ph) {
        g.fill(px + 1, py, px + pw - 1, py + ph, 0xFF000000);
        g.fill(px, py + 1, px + pw, py + ph - 1, 0xFF000000);
        g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xFFC6C6C6);
        g.fill(px + 1, py + 1, px + pw - 2, py + 3, 0xFFFFFFFF);
        g.fill(px + 1, py + 1, px + 3, py + ph - 2, 0xFFFFFFFF);
        g.fill(px + 3, py + ph - 3, px + pw - 1, py + ph - 1, 0xFF555555);
        g.fill(px + pw - 3, py + 3, px + pw - 1, py + ph - 1, 0xFF555555);
    }

    /** A sunken box (as vanilla's slots are drawn): dark edge top and left, white bottom and right. */
    private static void inset(GuiGraphicsExtractor g, int bx, int by, int bw, int bh) {
        g.fill(bx, by, bx + bw, by + bh, 0xFFFFFFFF);
        g.fill(bx, by, bx + bw - 1, by + bh - 1, 0xFF373737);
    }

    private void scrollbar(GuiGraphicsExtractor g, int bx, int by, int bh) {
        g.fill(bx, by, bx + BAR_W, by + bh, 0xFF2A2A2A);
        int max = maxScroll();
        if (max == 0) {
            return;
        }
        int knob = Math.max(12, bh * ROWS / rows.size());
        int ky = by + (bh - knob) * scroll / max;
        g.fill(bx, ky, bx + BAR_W, ky + knob, 0xFF8B8B8B);
        g.fill(bx, ky, bx + BAR_W - 1, ky + knob - 1, 0xFFC6C6C6);
    }

    // --- input ------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX_, double mouseY_, int button_) {
        MouseButtonEvent event = new MouseButtonEvent(mouseX_, mouseY_, button_);
        double mx = event.x(), my = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && search.isMouseOver(mx, my)) {
            search.setValue("");                                // (as the sound mod's search: a right click empties it)
            return true;
        }
        int lx = x + LIST_X, ly = y + LIST_Y;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && mx >= lx + LIST_W - BAR_W && mx < lx + LIST_W && my >= ly && my < ly + ROWS * ROW) {
            draggingBar = true;
            dragBar(my);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && mx >= lx && mx < lx + LIST_W - BAR_W - 1 && my >= ly && my < ly + ROWS * ROW) {
            int i = scroll + (int) ((my - ly) / ROW);
            if (i < rows.size()) {
                click(rows.get(i));
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        if (confirmUnmute && !unmuteAll.isMouseOver(mx, my)) {
            confirmUnmute = false;                              // (any other click calls off the unmute-all)
            labels();
        }
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    /** A row clicked: an entry muted or unmuted; a mod's row or header, all of its particles at once. */
    private void click(Row row) {
        if (row.header() && view == View.MUTED) {
            return;                                             // (a heading only)
        }
        if (row.header()) {
            boolean all = mutedIn(row.group()) == row.group().size();
            for (Catalog.Entry e : row.group()) {
                e.setMuted(!all);
            }
            newlyMuted |= !all;
        } else if (!row.header()) {
            boolean mute = !row.entry().muted();
            row.entry().setMuted(mute);
            newlyMuted |= mute;
        }
        int keep = scroll;
        changed();
        scroll = Math.min(keep, maxScroll());
    }

    @Override
    public boolean mouseDragged(double mouseX_, double mouseY_, int button_, double dx, double dy) {
        MouseButtonEvent event = new MouseButtonEvent(mouseX_, mouseY_, button_);
        if (draggingBar) {
            dragBar(event.y());
            return true;
        }
        return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX_, double mouseY_, int button_) {
        MouseButtonEvent event = new MouseButtonEvent(mouseX_, mouseY_, button_);
        draggingBar = false;
        return super.mouseReleased(event.x(), event.y(), event.button());
    }

    private void dragBar(double my) {
        int bh = ROWS * ROW, max = maxScroll();
        if (max == 0) {
            return;
        }
        int knob = Math.max(12, bh * ROWS / rows.size());
        double f = (my - (y + LIST_Y) - knob / 2.0) / (bh - knob);
        scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * max);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int key_, int scancode_, int modifiers_) {
        KeyEvent event = new KeyEvent(key_, scancode_, modifiers_);
        // The inventory key closes it, as it closes the inventory (unless the search is being typed in).
        if (!search.isFocused() && Minecraft.getInstance().options.keyInventory.matches(event.key(), event.scancode())) {
            onClose();
            return true;
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    /** For the dev test world's pictures: the list and the search as a player would set them. */
    public void showForPicture(String list, String text) {
        show(View.valueOf(list.toUpperCase(Locale.ROOT)));
        search.setValue(text);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Settings.save();
        if (newlyMuted) {
            Muting.clearLiving();                               // (the ones already flying go too, not as they fade)
        }
        Minecraft.getInstance().setScreen(parent);
    }
}
