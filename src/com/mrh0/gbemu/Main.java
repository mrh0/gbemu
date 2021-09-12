package com.mrh0.gbemu;

import java.io.File;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.mrh0.gbemu.cpu.CPU;
import com.mrh0.gbemu.cpu.Globals;
import com.mrh0.gbemu.cpu.memory.Memory;
import com.mrh0.gbemu.io.IO;
import com.mrh0.gbemu.io.Input;
import com.mrh0.gbemu.lcd.LCD;
import com.mrh0.gbemu.lcd.Window;

public class Main {

	public static void main(String[] args) throws IOException, InterruptedException {

		// IO.compareLogs();

		Window window = createWindow();

		JFileChooser fc = new JFileChooser();
		fc.setAcceptAllFileFilterUsed(false);
		fc.addChoosableFileFilter(new FileNameExtensionFilter("GameBoy .gb", "gb"));
		if (fc.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) {
			System.err.println("Invalid file selected!");
			System.exit(0);
		}

		Globals globals = new Globals();

		byte[] rom = loadROM(fc.getSelectedFile(), globals);

		System.out.println("Loaded ROM (" + rom.length + "bytes).");

		Memory memory = new Memory(globals, 0x10000, rom);

		memory.raw()[0xFF41] = 1;
		memory.raw()[0xFF43] = 0;

		// Unknown function:
		memory.raw()[0xFFF4] = (byte) 0x31;
		memory.raw()[0xFFF7] = (byte) 0xFF;

		LCD lcd = new LCD(globals);

		window.addKeyListener(new Input(globals));
		window.add(lcd);
		window.pack();

		CPU cpu = new CPU(memory, lcd, globals);
		cpu.reset();
		long time = System.nanoTime();
		long ntime = time + globals.frameTime;
		int cycles = globals.frameCycles;
		while (true) {
			windowTick(window, globals);
			if(!globals.cpuEnabled)
				continue;
			//cpu.debug();
			cycles -= cpu.advance();
			while (cycles < 0) {
				if ((time = System.nanoTime()) > ntime) {
					ntime = time + globals.frameTime;
					cycles = globals.frameCycles;
				}
			}
		}
	}
	
	private static String getWindowTitle(Window window, Globals globals) {
		return "gbemu" + (globals.cpuEnabled?"":" - PAUSED");
	}
	
	private static String lastTitle = "";
	private static void windowTick(Window window, Globals globals) {
		if(!lastTitle.equals(lastTitle=getWindowTitle(window, globals)))
			window.setTitle(lastTitle);
	}

	private static byte[] loadROM(File file, Globals globals) throws IOException {
		byte[] rom = IO.readROM(file);
		globals.FirstROMPage = new byte[256];
		byte[] bootcode = parseBootcode(globals);
		for (int i = 0; i < 256; i++) {
			globals.FirstROMPage[i] = rom[i];
			rom[i] = bootcode[i];
		}
		return rom;
	}

	private static byte[] parseBootcode(Globals globals) {
		String[] splt = globals.bootcode.split(" ");
		byte[] bytes = new byte[splt.length];
		for (int i = 0; i < splt.length; i++) {
			bytes[i] = (byte) Integer.parseInt(splt[i], 16);
		}
		return bytes;
	}

	private static Window createWindow() {
		Window win = new Window();
		win.setVisible(true);
		return win;
	}

	private static void genbitsetres() {
		for (var i = 0; i < 8; i++) {
			for (var j = 0; j < 8; j++) {
				String ext = j == 0b110 ? "vHL" : "vr";
				System.out.println("case 0x" + Integer.toHexString(0x40 + i * 8 + j).toUpperCase() + ": return bit_"
						+ ext + "(" + i + ", " + j + ");");
				System.out.println("case 0x" + Integer.toHexString(0x80 + i * 8 + j).toUpperCase() + ": return res_"
						+ ext + "(" + i + ", " + j + ");");
				System.out.println("case 0x" + Integer.toHexString(0xC0 + i * 8 + j).toUpperCase() + ": return set_"
						+ ext + "(" + i + ", " + j + ");");
			}
		}
	}
}
