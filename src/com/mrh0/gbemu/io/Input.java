package com.mrh0.gbemu.io;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import com.mrh0.gbemu.cpu.Globals;

public class Input implements KeyListener {

	private Globals globals;
	
	public Input(Globals globals) {
		this.globals = globals;
		this.btns = new boolean[8];
		//updateGlobals();
	}
	
	// Game Input
	private final int btnA = 90;
	private final int btnB = 88;
	private final int btnUp = 38;
	private final int btnDown = 40;
	private final int btnLeft = 37;
	private final int btnRight = 39;
	private final int btnStart = 10;
	private final int btnSelect = 16;
	
	// Emulation Input
	
	private final int btnPause = 19;
	
	private boolean[] btns;
	
	public int getButtonIndex(int btn) {
		switch(btn) {
			case btnA:
				return 0;
			case btnB:
				return 1;
			case btnUp:
				return 2;
			case btnDown:
				return 3;
			case btnLeft:
				return 4;
			case btnRight:
				return 5;
			case btnStart:
				return 6;
			case btnSelect:
				return 7;
		}
		return -1;
	}
	
	public static final String[] BUTTON_NAMES = {"A", "B", "Up", "Down", "Left", "Right", "Start", "Select"};
	
	private void setButton(int btn, boolean state) {
		int index = getButtonIndex(btn);
		if(index < 0)
			return;
		btns[index] = state;
		//System.out.println(buttonNames[index] + " : " + (state?"Pressed":"Released"));
		updateGlobals();
	}
	
	private int getBitVal(int index, int val) {
		return btns[index]?0:val;
	}
	
	private void updateGlobals() {
		globals.dpad = (byte) (getBitVal(2, 0b0100) | getBitVal(3, 0b1000) | getBitVal(4, 0b0010) | getBitVal(5, 0b0001));
		globals.buttons = (byte) (getBitVal(0, 0b0001) | getBitVal(1, 0b0010) | getBitVal(6, 0b1000) | getBitVal(7, 0b0100));
	}
	
	private void emulatorButton(int btn, boolean state){
		if(!state)
			return;
		switch(btn) {
			case btnPause:
				globals.cpuEnabled = !globals.cpuEnabled;
				break;
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
		setButton(e.getKeyCode(), true);
		emulatorButton(e.getKeyCode(), true);
	}

	@Override
	public void keyReleased(KeyEvent e) {
		setButton(e.getKeyCode(), false);
		emulatorButton(e.getKeyCode(), false);
	}
}
