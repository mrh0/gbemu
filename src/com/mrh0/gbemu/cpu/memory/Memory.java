package com.mrh0.gbemu.cpu.memory;

import com.mrh0.gbemu.cpu.Globals;

public class Memory {

	private byte[] mem;
	private byte[] cartRAM;
	private byte[] rom;

	private Globals globals;

	public Memory(Globals globals, int size, byte[] rom) {
		this.globals = globals;
		this.mem = new byte[size];
		this.cartRAM = new byte[0x8000];
		this.rom = rom;
	}

	public byte read(int addr) {
		addr &= 0xFFFF;
		if(addr >= 0x00A8 && addr < 0x00D8) {
			System.out.println("LOGO READ " + Integer.toHexString(addr) + ":" + Integer.toHexString(rom[addr]));
		}
		if (addr <= 0x3FFF)
			return rom[addr];
		if (addr <= 0x7FFF)
			return rom[addr + globals.ROMbankoffset];

		// Cartridge RAM
		if (addr >= 0xA000 && addr <= 0xBFFF)
			return cartRAM[addr + globals.RAMbankoffset];

		// Joypad
		if (addr == 0xFF00) {
			if ((mem[0xFF00] & 0x20) > 0)
				return globals.dpad;
			else if ((mem[0xFF00] & 0x10) > 0)
				return globals.buttons;
			else
				return (byte) 0xFF;
		}
		if(addr == 0xFF44) {
			//System.out.println("Read: wait for frame - " + mem[0xFF44]);
			//return (byte) 0x90;
		}
		return mem[addr];
	}

	public byte readRaw(int addr) {
		addr &= 0xFFFF;
		return mem[addr];
	}

	public void write(int addr, byte data) {
		addr &= 0xFFFF;
		if (addr <= 0x7FFF) {
			doMBC(addr, data);
			return;
		}

		if (addr >= 0xA000 && addr <= 0xBFFF && globals.RAMenabled) {
			cartRAM[addr + globals.RAMbankoffset] = data;
			return;
		}

		// DIV register: reset
		if (addr == 0xFF04) {
			mem[0xFF04] = 0;
			return;
		}
		// Timer control
		else if (addr == 0xFF07) {
			globals.timerEnable = ((data & (1 << 2)) != 0);
			globals.timerLength = globals.timerLengths[data & 0x3];
			globals.timerPrescaler = globals.timerLength; // +cycles for this instruction?
			mem[addr] = (byte) (0xF8 | data);
			return;
		}

		// LCD control
		else if (addr == 0xFF40) {
			int cc = data & (1 << 7);
			if (globals.LCD_enabled != cc > 0) {
				globals.LCD_enabled = cc > 0;
				if (!globals.LCD_enabled) { // Disabling the display sets it to mode 1
					// this should also probably set all pixels to white
					globals.LCD_scan = 0;
					mem[0xFF41] = (byte) ((mem[0xFF41] & 0xFC) + 1);
				}
			}
		} else if (addr == 0xFF41) {
			// don't overwrite the lowest two bits (mode)
			mem[0xFF41] &= 0x3;
			data &= 0xFC;
			mem[0xFF41] |= 0x80 | data; // BGB has highest bit always set
			return;
		}

		// LY - write causes reset
		else if (addr == 0xFF44) {
			mem[0xFF44] = 0;
			return;
		}

		// FF46 - DMA - DMA Transfer and Start Address (W)
		else if (addr == 0xFF46) {
			int st = data << 8;
			for (int i = 0; i <= 0x9F; i++)
				mem[0xFE00 + i] = read(st + i);
			return;
		}

		// disable bootrom
		else if (addr == 0xFF50) {
			for (int i = 0; i < 256; i++)
				rom[i] = MemMap.FirstROM(i, mem);
			return;
		}

		mem[addr] = data;
	}

	public void write16(int addr, byte data1, byte data2) {
		write(addr + 1, data1);
		write(addr, data2);
	}

	public void writeRaw(int addr, byte data) {
		addr &= 0xFFFF;
		mem[addr] = data;
	}

	public byte incRaw(int addr) {
		addr &= 0xFFFF;
		return mem[addr]++;
	}

	public byte[] raw() {
		return mem;
	}

	private void doMBC(int addr, byte data) {

		switch (rom[0x147]) {

		// Cartridge Type = ROM[0x147]

		case 0: // ROM ONLY
			// do any type 0 carts have switchable ram?
			break;

		case 0x01: // MBC1
		case 0x02: // MBC1+RAM
		case 0x03: // MBC1+RAM+BATTERY
			if (addr <= 0x1FFF) {
				globals.RAMenabled = ((data & 0x0F) == 0xA);
			} else if (addr <= 0x3FFF) {
				data &= 0x1F;
				if (data == 0)
					data = 1; // MBC1 translates bank 0 to bank 1 (apparently regardless of upper bits)
				// set lowest 5 bits of bank number
				globals.ROMbank = (globals.ROMbank & 0xE0) | (data & 0x1F);
				globals.ROMbankoffset = (globals.ROMbank - 1) * 0x4000 % rom.length;
			} else if (addr <= 0x5fff) {
				data &= 0x3;
				if (globals.MBCRamMode == 0) {
					globals.ROMbank = (globals.ROMbank & 0x1F) | (data << 5);
					globals.ROMbankoffset = (globals.ROMbank - 1) * 0x4000 % rom.length;
				} else {
					globals.RAMbank = data;
					globals.RAMbankoffset = globals.RAMbank * 0x2000 - 0xA000;
				}
			} else {
				globals.MBCRamMode = data & 1;
				if (globals.MBCRamMode == 0) {
					globals.RAMbank = 0;
					globals.RAMbankoffset = globals.RAMbank * 0x2000 - 0xA000;
				} else {
					globals.ROMbank &= 0x1F;
					globals.ROMbankoffset = (globals.ROMbank - 1) * 0x4000 % rom.length;
				}
			}

			break;

		case 0x05: // MBC2
		case 0x06: // MBC2+BATTERY

			if (addr <= 0x1FFF) {
				if ((addr & 0x0100) == 0)
					globals.RAMenabled = ((data & 0x0F) == 0xA);
			} else if (addr <= 0x3FFF) {
				data &= 0x0F;
				if (data == 0)
					data = 1;
				globals.ROMbank = data;
				globals.ROMbankoffset = (globals.ROMbank - 1) * 0x4000 % rom.length;
			}

			break;

		// case 0x08: // ROM+RAM
		// case 0x09: // ROM+RAM+BATTERY
		// case 0x0B: // MMM01
		// case 0x0C: // MMM01+RAM
		// case 0x0D: // MMM01+RAM+BATTERY
		// case 0x0F: // MBC3+TIMER+BATTERY
		// case 0x10: // MBC3+TIMER+RAM+BATTERY
		case 0x11: // MBC3
		case 0x12: // MBC3+RAM
		case 0x13: // MBC3+RAM+BATTERY

			if (addr <= 0x1FFF) {
				globals.RAMenabled = ((data & 0x0F) == 0xA);
			} else if (addr <= 0x3FFF) {
				if (data == 0)
					data = 1; // allows access to banks 0x20, 0x40, 0x60
				globals.ROMbank = data & 0x7F;
				globals.ROMbankoffset = (globals.ROMbank - 1) * 0x4000 % rom.length;
			} else if (addr <= 0x5fff) {
				if (data < 8) {
					globals.RAMbank = data;
					globals.RAMbankoffset = globals.RAMbank * 0x2000 - 0xA000;
				} else {
					// RTC registers here
				}
			} else {
				// RTC latch
			}
			break;

		case 0x19: // MBC5
		case 0x1A: // MBC5+RAM
		case 0x1B: // MBC5+RAM+BATTERY
			// case 0x1C: // MBC5+RUMBLE
			// case 0x1D: // MBC5+RUMBLE+RAM
			// case 0x1E: // MBC5+RUMBLE+RAM+BATTERY
			if (addr <= 0x1FFF) {
				globals.RAMenabled = ((data & 0x0F) == 0xA);
			} else if (addr <= 0x2FFF) {
				// Allows access to bank 0
				globals.ROMbank &= 0x100;
				globals.ROMbank |= data;
				globals.ROMbankoffset = (globals.ROMbank - 1) * 0x4000;
				while (globals.ROMbankoffset > rom.length)
					globals.ROMbankoffset -= rom.length;
			} else if (addr <= 0x3FFF) {
				globals.ROMbank &= 0xFF;
				if ((data & 1) > 0)
					globals.ROMbank += 0x100;
				globals.ROMbankoffset = (globals.ROMbank - 1) * 0x4000;
				while (globals.ROMbankoffset > rom.length)
					globals.ROMbankoffset -= rom.length;
			} else if (addr <= 0x5fff) {
				globals.RAMbank = data & 0x0F;
				globals.RAMbankoffset = globals.RAMbank * 0x2000 - 0xA000;
			}
			break;

		// case 0x20: // MBC6
		// case 0x22: // MBC7+SENSOR+RUMBLE+RAM+BATTERY
		// case 0xFC: // POCKET CAMERA
		// case 0xFD: // BANDAI TAMA5
		// case 0xFE: // HuC3
		// case 0xFF: // HuC1+RAM+BATTERY

		default:
			System.err.println("NOT IMPLEMENTED MEM" + addr);
			System.exit(0);
			break;

		}
	}
}
