package com.mrh0.gbemu;

public class Main {

	public static void main(String[] args) {
		genbitsetres();
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
