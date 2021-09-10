package com.mrh0.gbemu.lcd;

import java.awt.FlowLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JFrame;

import com.mrh0.gbemu.io.Input;

public class Window extends JFrame implements WindowListener {
	
	private static final long serialVersionUID = 1L;
	
	public Window() {
		this.addWindowListener(this);
		this.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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

}
