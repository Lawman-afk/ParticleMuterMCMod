package dev.particlemuter.port.input;

/** Minecraft 26.x's key event, made from 1.21.1's key, scan code and modifiers (this branch's own). */
public record KeyEvent(int key, int scancode, int modifiers) {}
