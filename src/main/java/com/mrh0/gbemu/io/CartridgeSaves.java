package com.mrh0.gbemu.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

public class CartridgeSaves {
	public static final String EXT = ".sav";

	public static boolean save(File rom, String name, byte[] data) {
		if(name.length() == 0)
			return false;
		//System.out.println("Saving " + name + EXT + "'... ");
		File f = new File(rom.getParent(), name + EXT);
		try {
			OutputStream os = new FileOutputStream(f);
			os.write(data);
			// TODO: Save Clock
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		System.out.println("Saved '" + name + EXT + "' (" + data.length + "bytes)");
		return true;
	}

	public static byte[] load(File rom, String name) {
		if(name.length() == 0)
			return new byte[0];
		//System.out.println("Loading '" + name + EXT + "'...");
		File f = new File(rom.getParent(), name + EXT);
		if (!f.exists()) {
			System.out.println("No Existing Save.");
			return new byte[0];
		}
		try {
			byte[] bytes = Files.readAllBytes(f.toPath());
			System.out.println("Loaded '" + name + EXT + "' (" + bytes.length + "bytes)");
			return bytes;
		} catch (IOException e) {
			e.printStackTrace();
			return new byte[0];
		}
	}
}
