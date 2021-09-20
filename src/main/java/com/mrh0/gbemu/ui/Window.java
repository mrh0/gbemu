package com.mrh0.gbemu.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.mrh0.gbemu.Emulator;
import com.mrh0.gbemu.ui.menu.MenuBar;

public class Window extends JFrame implements WindowListener {
	
	private static final long serialVersionUID = 1L;
	
	public final GridBagLayout layout;
	private boolean fullscreen = false;
	
	public Window() {
		this.layout = new GridBagLayout();
		this.addWindowListener(this);
		this.setLayout(layout);//new FlowLayout(FlowLayout.LEFT, 0, 0)
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.getContentPane().setBackground(Color.BLACK);
	}
	
	public void init(Emulator emulator) {
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1d;
		c.weighty = 0d;
		c.anchor = GridBagConstraints.NORTH;
		c.gridx = 0;
		c.gridy = 0;
		this.add(new MenuBar(emulator), c);
		this.addKeyListener(emulator.getInput());
		c.fill = GridBagConstraints.CENTER;
		c.gridx = 0;
		c.gridy = 1;
		c.weightx = 0.5d;
		c.weighty = 0.5d;
		c.anchor = GridBagConstraints.CENTER;
		this.add(emulator.getLCD(), c);
		this.pack();
	}
	
	public File chooseROM() {
		JFileChooser fc = new JFileChooser();
		fc.setAcceptAllFileFilterUsed(false);
		fc.addChoosableFileFilter(new FileNameExtensionFilter("GameBoy .gb", "gb", "gbc"));
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		return fc.getSelectedFile();
	}

	@Override
	public void windowOpened(WindowEvent e) {
	}

	@Override
	public void windowClosing(WindowEvent e) {
	}

	@Override
	public void windowClosed(WindowEvent e) {
	}

	@Override
	public void windowIconified(WindowEvent e) {
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
	}

	@Override
	public void windowActivated(WindowEvent e) {
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
	}

	public void setFullscreen(boolean state) {
		if(state)
			this.setExtendedState(JFrame.MAXIMIZED_BOTH);
		else
			this.pack();
		fullscreen = state;
	}
	
	public boolean isFullscreen() {
		return fullscreen;
	}
}
