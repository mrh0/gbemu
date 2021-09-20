package com.mrh0.gbemu.ui.lcd;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

import com.mrh0.gbemu.Globals;

public class LCD extends JPanel {

	private static final long serialVersionUID = 1L;

	byte[] pixels;
	private Globals globals;
	private final int SIZE = 160 * 144;
	private int scale = 3;
	private Dimension preferredSize = new Dimension(160 * scale, 144 * scale);
	private int colorMode = 0;

	private Color[] greenscale = { new Color(224, 248, 208), new Color(136, 192, 112), new Color(52, 104, 86),
			new Color(8, 24, 32) };

	private Color[] grayscale = { new Color(226, 226, 226), new Color(146, 146, 146), new Color(80, 80, 80), new Color(21, 21, 21) };
	
	public Color[] getColors(int select) {
		switch(select) {
			case 0: return greenscale;
			case 1: return grayscale;
		}
		return null;
	}

	public LCD(Globals globals) {
		this.globals = globals;
		this.pixels = new byte[SIZE];
	}

	public byte[] raw() {
		return pixels;
	}

	@Override
	public Dimension getPreferredSize() {
		return preferredSize;
	}

	@Override
	protected void paintComponent(Graphics gfx) {
		Graphics2D g = (Graphics2D) gfx;
		// super.paintComponent(g); // Not needed (i think)

		for (int i = 0; i < SIZE; i++) {
			g.setColor(getColors(colorMode)[pixels[i]]);
			g.fillRect((i % 160) * scale, (i / 160) * scale, scale, scale);
		}
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

	public void update(int pc) {
		this.repaint();
	}
}
