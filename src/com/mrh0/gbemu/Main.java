package com.mrh0.gbemu;

import com.mrh0.gbemu.cpu.CPU;
import com.mrh0.gbemu.cpu.Globals;
import com.mrh0.gbemu.cpu.memory.Memory;

public class Main {

	public static void main(String[] args) {
		byte[] rom = new byte[0];
		
		Globals globals = new Globals();
		Memory memory = new Memory(globals, 0x1000, rom);
		CPU cpu = new CPU(memory, globals);
	}

	private static void genbitsetres() {
		for (var i = 0; i < 8; i++) {
			for (var j = 0; j < 8; j++) {
				String ext = j == 0b110 ? "vHL" : "vr";
				System.out.println("case 0x" + Integer.toHexString(0x40 + i * 8 + j).toUpperCase() + ": return bit_" + ext + "(" + i + ", " + j + ");");
				System.out.println("case 0x" + Integer.toHexString(0x80 + i * 8 + j).toUpperCase() + ": return res_" + ext + "(" + i + ", " + j + ");");
				System.out.println("case 0x" + Integer.toHexString(0xC0 + i * 8 + j).toUpperCase() + ": return set_" + ext + "(" + i + ", " + j + ");");
			}
		}
	}
}
