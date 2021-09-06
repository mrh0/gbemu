package com.mrh0.gbemu.cpu.memory;

import com.mrh0.gbemu.cpu.Globals;

public class Memory {

	private byte[] mem;
	private byte[] rom;
	
	private Globals globals;

	public Memory(Globals globals, int size, byte[] rom) {
		this.globals = globals;
		this.mem = new byte[size];
		this.rom = rom;
	}

	public byte read(int addr) {
		if (addr <= 0x3fff)
			return rom[addr];
		if (addr <= 0x7fff)
			return rom[addr + globals.ROMbankoffset];

		// Cartridge RAM
		// if (addr >= 0xA000 && addr <=0xBFFF) return cartRAM[ addr + RAMbankoffset ];

		// Joypad
		if (addr == 0xFF00) {
			if ((mem[0xFF00] & 0x20) > 0)
				return globals.dpad;
			else if ((mem[0xFF00] & 0x10) > 0)
				return globals.buttons;
			else
				return (byte) 0xFF;
		}
		return mem[addr];
	}
	
	public byte readRaw(int addr) {
		return mem[addr];
	}

	public void write(int addr, byte data) {
		if (addr <= 0x7FFF) {
			// doMBC(addr, data);
			return;
		}

		/*
		 * if (addr >= 0xA000 && addr <=0xBFFF && RAMenabled){ cartRAM[ addr +
		 * RAMbankoffset ] = data; return; }
		 */

		// DIV register: reset
		if (addr == 0xFF04) {
			mem[0xFF04] = 0;
			return;
		}
		// Timer control
		if (addr == 0xFF07) {
			globals.timerEnable = ((data & (1 << 2)) != 0);
			globals.timerLength = globals.timerLengths[data & 0x3];
			globals.timerPrescaler = globals.timerLength; // +cycles for this instruction?
			mem[addr] = (byte) (0xF8 | data);
			return;
		}

		// LCD control
		if (addr == 0xFF40) {
			int cc = data & (1 << 7);
			if (globals.LCD_enabled != cc > 0) {
				globals.LCD_enabled = cc > 0;
				if (!globals.LCD_enabled) { // Disabling the display sets it to mode 1
					// this should also probably set all pixels to white
					globals.LCD_scan = 0;
					mem[0xFF41] = (byte) ((mem[0xFF41] & 0xFC) + 1);
				}
			}
		}
		if (addr == 0xFF41) {
			// don't overwrite the lowest two bits (mode)
			mem[0xFF41] &= 0x3;
			data &= 0xFC;
			mem[0xFF41] |= 0x80 | data; // BGB has highest bit always set
			return;
		}

		// LY - write causes reset
		if (addr == 0xFF44) {
			mem[0xFF44] = 0;
			return;
		}

		// FF46 - DMA - DMA Transfer and Start Address (W)
		if (addr == 0xFF46) {
			int st = data << 8;
			for (int i = 0; i <= 0x9F; i++)
				mem[0xFE00 + i] = read(st + i);
			return;
		}

		// disable bootrom
		if (addr == 0xFF50) {
			for (int i = 0; i < 256; i++)
				rom[i] = MemMap.FirstROM(i, mem);
			return;
		}

		mem[addr] = data;
	}
	
	public void write16(int addr, byte data1, byte data2) {
		write(addr+1, data1);
		write(addr, data2);
	}
	
	public void writeRaw(int addr, byte data) {
		mem[addr] = data;
	}
	
	public byte incRaw(int addr) {
		return mem[addr]++;
	}
	
	public byte[] raw() {
		return mem;
	}
}
