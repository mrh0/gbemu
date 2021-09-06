package com.mrh0.gbemu.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class IO {
	public static byte[] readROM(File file) throws IOException {
		return Files.readAllBytes(file.toPath());
	}
}
