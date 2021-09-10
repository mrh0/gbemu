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
		resetSoundRegisters();
	}

	public byte[] rom() {
		return rom;
	}

	private void resetSoundRegisters() {
		mem[0xFF10] = (byte) 0x80; // NR10
		mem[0xFF11] = (byte) 0xBF; // NR11
		mem[0xFF12] = (byte) 0xF3; // NR12
		mem[0xFF13] = 0x00;
		mem[0xFF14] = (byte) 0xBF; // NR14
		mem[0xFF15] = (byte) 0xFF; // NA
		mem[0xFF16] = 0x3F; // NR21
		mem[0xFF17] = 0x00; // NR22
		mem[0xFF18] = 0x00;
		mem[0xFF19] = (byte) 0xBF; // NR24
		mem[0xFF1A] = 0x7F; // NR30
		mem[0xFF1B] = (byte) 0xFF; // NR31
		mem[0xFF1C] = (byte) 0x9F; // NR32
		mem[0xFF1D] = 0x00;
		mem[0xFF1E] = (byte) 0xBF; // NR33
		mem[0xFF1F] = (byte) 0xFF; // NA
		mem[0xFF20] = (byte) 0xFF; // NR41
		mem[0xFF21] = 0x00; // NR42
		mem[0xFF22] = 0x00; // NR43
		mem[0xFF23] = (byte) 0xBF; // NR30
		mem[0xFF24] = 0x77; // NR50
		write(0xFF25, (byte) 0xF3); // NR51
		mem[0xFF26] = (byte) 0xF1; // NR52

	}

	public byte read(int addr) {
		addr &= 0xFFFF;

		if (addr <= MemMap.Bank0.end)
			return rom[addr];
		if (addr <= MemMap.BankN.end)
			return (byte) (rom[addr + globals.ROMbankoffset] & 0xFF);

		// Cartridge RAM
		if (addr >= MemMap.ExtRAM.start && addr <= MemMap.ExtRAM.end)
			return (byte) (cartRAM[addr + globals.RAMbankoffset] & 0xFF);

		// Joypad
		if (addr == MemMap.IO.start) {
			if ((mem[MemMap.IO.start] & 0x20) > 0)
				return globals.dpad;
			else if ((mem[MemMap.IO.start] & 0x10) > 0) {
				return globals.buttons;
			} else
				return (byte) 0xFF;
		}
		return (byte) (mem[addr] & 0xFF);
	}

	public byte readRaw(int addr) {
		addr &= 0xFFFF;
		return (byte) (mem[addr] & 0xFF);
	}

	public void write(int addr, byte data) {
		addr &= 0xFFFF;
		data &= 0xFF;

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
		if (addr == 0xFF07) {
			globals.timerEnable = ((data & (1 << 2)) != 0);
			globals.timerLength = globals.timerLengths[data & 0x3];
			globals.timerPrescaler = globals.timerLength; // +cycles for this instruction?
			mem[addr] = (byte) (0xF8 | data);
			return;
		}

		if (addr == 0xFF26) {
			if ((data & (1 << 7)) > 0) {
				mem[0xFF26] = (byte) (data & (1 << 7));
				// SoundEnabled = true;
				// audioCtx.resume()
			} else {
				// SoundEnabled = false;
				// should we set each oscillator to amplitude zero too?
				// audioCtx.suspend()
				// Zero all sound registers
				resetSoundRegisters();
			}
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
			System.out.println("disable bootrom");
			for (int i = 0; i < 256; i++)
				rom[i] = (byte) (globals.FirstROMPage[i] & 0xFF);
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
		mem[addr] = (byte) (data & 0xFF);
	}

	public byte[] raw() {
		return mem;
	}

	private void doMBC(int addr, byte data) {

		switch (rom[0x147] & 0xFF) {

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
