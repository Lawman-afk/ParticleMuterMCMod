package dev.particlemuter.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.windows.User32;

/**
 * The dev test runs (the photographers and the prepared worlds: build.gradle sets {@code particlemuter.keepMouse}) leave
 * the person at the computer their mouse and their windows: the game's window is put behind the other windows without
 * taking the focus, and the game never grabs the mouse ({@code mixin/DevMouseMixin}; the runs turn the camera
 * themselves). 1.21.1's window is GLFW's (this branch's own, manual.txt; main's is SDL's). Players' games never set the
 * property.
 */
public final class DevRuns {
    public static final boolean KEEP_MOUSE = Boolean.getBoolean("particlemuter.keepMouse");
    /** Client ticks it goes on setting the window behind (the game may raise it as it loads its first world). */
    private static final int BEHIND_FOR = 200;
    private static int ticks;

    private DevRuns() {}

    /** Called as the client starts. */
    public static void register() {
        if (!KEEP_MOUSE) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent event) -> {if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            if (ticks < BEHIND_FOR && ticks++ % 20 == 0) {
                behind(Minecraft.getInstance());
            }
        });
    }

    /** The game's window behind every other, not activated, and not focused when shown again (Windows). */
    private static void behind(Minecraft mc) {
        long window = mc.getWindow().getWindow();
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        if (net.minecraft.Util.getPlatform() == net.minecraft.Util.OS.WINDOWS) {
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(window);
            if (hwnd != 0) {
                User32.SetWindowPos(hwnd, User32.HWND_BOTTOM, 0, 0, 0, 0, User32.SWP_NOMOVE | User32.SWP_NOSIZE | User32.SWP_NOACTIVATE);
            }
        }
    }
}
