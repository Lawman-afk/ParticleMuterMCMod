package dev.particlemuter.port.input;

/** Minecraft 26.x's mouse button event, made from 1.21.1's position and button (this branch's own). */
public record MouseButtonEvent(double x, double y, int button) {}
