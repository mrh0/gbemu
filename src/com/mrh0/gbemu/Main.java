package com.mrh0.gbemu;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.mrh0.gbemu.io.IO;

public class Main {

	public static void main(String[] args) throws IOException, InterruptedException {
		//IO.compareLogs();
		
		Emulator emu = new Emulator();

		File ROMFile = emu.getWindow().chooseROM();
		if(!emu.setROM(ROMFile)) {
			System.err.println("Invalid file selected.");
			System.exit(0);
		}
		new Thread(emu).start();
	}
}
