package com.mrh0.gbemu.lcd;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

import com.mrh0.gbemu.cpu.Globals;

public class LCD extends JPanel {

	private static final long serialVersionUID = 1L;

	byte[] pixels;
	private Globals globals;
	private final int SIZE = 160 * 144, SCALE = 2;

	private Color[] colors = {
		new Color(224, 248, 208),
		new Color(136, 192, 112),
		new Color(52, 104, 86),
		new Color(8, 24, 32)
	};

	public LCD(Globals globals) {
		this.globals = globals;
		this.pixels = new byte[SIZE];
	}

	public byte[] raw() {
		return pixels;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(160*SCALE, 144*SCALE);
	}

	@Override
	protected void paintComponent(Graphics gfx) {
		Graphics2D g = (Graphics2D) gfx;
		super.paintComponent(g);
		
		for(int i = 0; i < SIZE; i++) {
			g.setColor(colors[pixels[i]]);
			g.fillRect((i%160)*SCALE, (i/160)*SCALE, SCALE, SCALE);
		}
	}

	public void update() {
		this.repaint();
	}
}
