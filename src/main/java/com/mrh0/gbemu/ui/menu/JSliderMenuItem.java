package com.mrh0.gbemu.ui.menu;

import java.awt.Dimension;

import javax.swing.JMenuItem;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;

public class JSliderMenuItem extends JMenuItem {
	private static final long serialVersionUID = 1L;
	private JSlider slider;

	public JSliderMenuItem(int min, int max, int init) {
		this.slider = new JSlider(JSlider.HORIZONTAL, min, max, init);
		this.add(slider);
	}
	
	@Override
	public Dimension getPreferredSize() {
		return new Dimension(128, 46); // 22 with no ticks
	}
	
	public JSlider getSlider() {
		return slider;
	}
	
	public JSliderMenuItem setTicks(int major, int minor) {
		slider.setMajorTickSpacing(major);
		slider.setMinorTickSpacing(minor);
		slider.setPaintTicks(true);
		slider.setPaintLabels(true);
		return this;
	}
	
	@Override
	public void addChangeListener(ChangeListener l) {
		slider.addChangeListener(l);
	}
	
	public JSliderMenuItem change(ChangeListener l) {
		addChangeListener(l);
		return this;
	}
	
	public int getValue() {
		return slider.getValue();
	}
}
