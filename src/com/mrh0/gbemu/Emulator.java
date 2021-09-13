package com.mrh0.gbemu;

import java.io.File;
import java.io.IOException;

import com.mrh0.gbemu.cpu.CPU;
import com.mrh0.gbemu.cpu.Globals;
import com.mrh0.gbemu.cpu.memory.Memory;
import com.mrh0.gbemu.io.IO;
import com.mrh0.gbemu.io.Input;
import com.mrh0.gbemu.ui.Window;
import com.mrh0.gbemu.ui.lcd.LCD;

public class Emulator {
	private Globals globals;
	private CPU cpu;
	private Memory memory;
	private LCD lcd;
	private Window window;
	
	public Emulator() {
		globals = new Globals();
		lcd = new LCD(globals);
		
		// Setup Memory:
		memory = new Memory(globals, 0x10000);
		memory.raw()[0xFF41] = 1;
		memory.raw()[0xFF43] = 0;
		// Unknown function:
		memory.raw()[0xFFF4] = (byte) 0x31;
		memory.raw()[0xFFF7] = (byte) 0xFF;
		
		cpu = new CPU(memory, lcd, globals);
		window = createWindow();
		window.init(lcd, new Input(globals));
	}
	
	public void setROM(byte[] rom) {
		memory.setROM(rom);
	}
	
	public void setROM(File rom) {
		try {
			memory.setROM(readROM(rom));
		} catch (IOException e) {
			System.err.println("Failed to load ROM file.");
			e.printStackTrace();
		}
	}
	
	public Globals getGlobals() {
		return globals;
	}
	
	public CPU getCPU() {
		return cpu;
	}
	
	public LCD getLCD() {
		return lcd;
	}
	
	public Window getWindow() {
		return window;
	}
	
	private static Window createWindow() {
		Window win = new Window();
		win.setVisible(true);
		return win;
	}
	
	public void run() {
		cpu.reset();
		float relativeSpeed = 1f;
		int relativePercent = 1;
		long sec = System.nanoTime();
		long time = System.nanoTime();
		long ntime = time + globals.frameTime;
		int cycles = globals.frameCycles;
		long cycleSum = 0;
		while (true) {
			//System.out.println("testing");
			long nano = System.nanoTime();
			if(sec < nano) {
				sec = nano + 1000000000;
				relativePercent = (int) (100f * ((float)cycleSum)/globals.secondCycles);
				cycleSum = 0;
			}
			windowTick(window, globals, relativePercent);
			if (!globals.cpuEnabled)
				continue;
			// cpu.debug();
			int c = cpu.advance();
			cycleSum += c;
			cycles -= c;
			while (cycles < 0) {
				if ((time = System.nanoTime()) > ntime) {
					
					ntime = time + globals.frameTime;
					cycles = globals.frameCycles;
				}
			}
		}
	}
	
	private String getWindowTitle(int precent) {
		return "gbemu" + (globals.cpuEnabled ? " - " + precent + "%" : " - PAUSED");
	}

	private static String lastTitle = "";

	private void windowTick(Window window, Globals globals, int precent) {
		if (!lastTitle.equals(lastTitle = getWindowTitle(precent)))
			window.setTitle(lastTitle);
	}

	private byte[] readROM(File file) throws IOException {
		byte[] rom = IO.readROM(file);
		globals.FirstROMPage = new byte[256];
		byte[] bootcode = parseBootcode();
		for (int i = 0; i < 256; i++) {
			globals.FirstROMPage[i] = rom[i];
			rom[i] = bootcode[i];
		}
		System.out.println("Loaded ROM (" + rom.length + "bytes).");
		return rom;
	}

	private byte[] parseBootcode() {
		String[] splt = globals.bootcode.split(" ");
		byte[] bytes = new byte[splt.length];
		for (int i = 0; i < splt.length; i++) {
			bytes[i] = (byte) Integer.parseInt(splt[i], 16);
		}
		return bytes;
	}
}
