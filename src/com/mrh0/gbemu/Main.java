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

public class Main {

	public static void main(String[] args) throws IOException, InterruptedException {

		// IO.compareLogs();
		
		Emulator emu = new Emulator();

		Window window = emu.getWindow();

		// Select ROM file:
		File ROMFile = window.chooseROM();
		if(ROMFile == null) {
			System.err.println("Invalid file selected.");
			System.exit(0);
		}
		emu.setROM(ROMFile);
		emu.run();
	}

	

	

	
	/*
	 * private static void genbitsetres() { for (var i = 0; i < 8; i++) { for (var j
	 * = 0; j < 8; j++) { String ext = j == 0b110 ? "vHL" : "vr";
	 * System.out.println("case 0x" + Integer.toHexString(0x40 + i * 8 +
	 * j).toUpperCase() + ": return bit_" + ext + "(" + i + ", " + j + ");");
	 * System.out.println("case 0x" + Integer.toHexString(0x80 + i * 8 +
	 * j).toUpperCase() + ": return res_" + ext + "(" + i + ", " + j + ");");
	 * System.out.println("case 0x" + Integer.toHexString(0xC0 + i * 8 +
	 * j).toUpperCase() + ": return set_" + ext + "(" + i + ", " + j + ");"); } } }
	 */
}
