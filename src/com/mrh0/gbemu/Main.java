package com.mrh0.gbemu;

import java.io.File;
import java.io.IOException;

public class Main {

	public static void main(String[] args) throws IOException, InterruptedException {
		Emulator emu = new Emulator();

		File ROMFile = emu.getWindow().chooseROM();
		if(!emu.setROM(ROMFile)) {
			System.err.println("Invalid file selected.");
			System.exit(0);
		}
		emu.run();
	}
}
