package com.mrh0.gbemu.lcd;

import java.awt.Graphics2D;

import com.mrh0.gbemu.Emulator;
import com.mrh0.gbemu.ui.game.Renderer;

public interface ILCD {
	public void render(Graphics2D g, int scale, int mode);
	public int cycle(Emulator emulator, Renderer renderer, int cycles);
}
