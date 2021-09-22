package com.mrh0.gbemu.lcd.color;

import java.awt.Graphics2D;

import com.mrh0.gbemu.Emulator;
import com.mrh0.gbemu.lcd.ILCD;
import com.mrh0.gbemu.ui.game.Renderer;

public class CLCD implements ILCD{

	private final Palette bg;
    private final Palette oam;
	
	public CLCD() {
		this.bg = new Palette(0xff68);
        this.oam = new Palette(0xff6a);
	}
	
	@Override
	public void render(Graphics2D g, int scale, int mode) {
	}

	@Override
	public int cycle(Emulator emulator, Renderer renderer, int cycles) {
		return cycles;
	}

	public static int getColor(int c) {
        int r = (c >> 0) & 0x1f;
        int g = (c >> 5) & 0x1f;
        int b = (c >> 10) & 0x1f;
        return ((r * 8) << 16) | ((g * 8) << 8) | (b * 8);
    }
}
