package com.mrh0.gbemu.lcd;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

public class DebugPanel extends JPanel {
	
	private static final long serialVersionUID = 1L;
	
	@Override
	public Dimension getPreferredSize() {
		return new Dimension(500, 500);
	}
	
	@Override
	protected void paintComponent(Graphics gfx) {
		Graphics2D g = (Graphics2D)gfx;
		super.paintComponent(g);
	}
}
