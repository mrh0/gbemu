package com.mrh0.gbemu.memory;

public enum MemMap {
	Bank0(0x0000, 0x3FFF),
	BankN(0x4000, 0x7FFF),
	VRAM(0x8000, 0x9FFF),
	ExtRAM(0xA000, 0xBFFF),
	WRAM0(0xC000, 0xCFFF),
	WRAM1(0xD000, 0xDFFF),
	Echo(0xE000, 0xFDFF),
	OAM(0xFE00, 0xFE9F),
	IO(0xFF00, 0xFF7F),
	HRAM(0xFF80, 0xFFFE),
	IER(0xFFFF, 0xFFFF);
	
	private MemMap(int start, int end) {
		this.start = start;
		this.end = end;
	}
	
	public final int start;
	public final int end;
	
	/*public static byte FirstROM(int i, byte[] mem) {
		return mem[i + Bank0.start&0xFFFF];
	}*/
}
