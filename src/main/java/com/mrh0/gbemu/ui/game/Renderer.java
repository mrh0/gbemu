package com.mrh0.gbemu.ui.game;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

import com.mrh0.gbemu.Emulator;
import com.mrh0.gbemu.lcd.ILCD;

public class Renderer extends JPanel {

	private static final long serialVersionUID = 1L;

	private int scale = 3;
	private Dimension preferredSize = new Dimension(160 * scale, 144 * scale);
	private int colorMode = 0;
	
	private ILCD lcd;

	public Renderer(Emulator emulator, ILCD lcd) {
		this.lcd = lcd;
	}

	@Override
	public Dimension getPreferredSize() {
		return preferredSize;
	}

	@Override
	protected void paintComponent(Graphics gfx) {
		Graphics2D g = (Graphics2D) gfx;
		// super.paintComponent(g); // Not needed.
		if(lcd == null)
			return;
		lcd.render(g, scale, colorMode);
	}

	public void setScale(int scale) {
		this.scale = scale;
		preferredSize = new Dimension(160 * scale, 144 * scale);
		this.setSize(preferredSize);
		this.repaint();
	}

	public int getScale() {
		return scale;
	}
	
	public void setColorMode(int mode) {
		colorMode = mode;
	}
	
	public int getColorMode() {
		return colorMode;
	}

	public void drawFrame() {
		this.repaint();
	}
	
	public int cycle(Emulator emulator, Renderer renderer, int cycles) {
		if(lcd == null)
			return cycles;
		return lcd.cycle(emulator, renderer, cycles);
	}
	
	public void setLCD(ILCD lcd) {
		this.lcd = lcd;
	}
	
	public ILCD getLCD() {
		return this.lcd;
	}
}
