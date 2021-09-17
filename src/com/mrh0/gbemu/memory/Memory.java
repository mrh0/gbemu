package com.mrh0.gbemu.memory;

import com.mrh0.gbemu.Emulator;
import com.mrh0.gbemu.Globals;
import com.mrh0.gbemu.sound.SoundManager;

public class Memory {

	private byte[] mem;
	private byte[] cartRAM;
	private byte[] rom;

	private Emulator emulator;

	public Memory(Emulator emulator, int size) {
		this.emulator = emulator;
		this.mem = new byte[size];
		this.cartRAM = new byte[0x8000];
	}
	
	public void setROM(byte[] rom) {
		this.rom = rom;
	}

	public byte[] rom() {
		return rom;
	}

	public void resetSoundRegisters() {
		/*write(0xFF10, (byte) 0x80); // NR10
		write(0xFF11, (byte) 0xBF); // NR11
		write(0xFF12, (byte) 0xF3); // NR12
		write(0xFF13, (byte) 0x00); // NR13
		write(0xFF14, (byte) 0xBF); // NR14
		write(0xFF15, (byte) 0xFF); // NA
		write(0xFF16, (byte) 0x3F); // NR21
		write(0xFF17, (byte) 0x00); // NR22
		write(0xFF18, (byte) 0x00); // NR23
		write(0xFF19, (byte) 0xBF); // NR24
		write(0xFF1A, (byte) 0x7F); // NR30
		write(0xFF1B, (byte) 0xFF); // NR31
		write(0xFF1C, (byte) 0x9F); // NR32
		write(0xFF1D, (byte) 0x00);
		write(0xFF1E, (byte) 0xBF); // NR33
		write(0xFF1F, (byte) 0xFF); // NA
		write(0xFF20, (byte) 0xFF); // NR41
		write(0xFF21, (byte) 0x00); // NR42
		write(0xFF22, (byte) 0x00); // NR43
		write(0xFF23, (byte) 0xBF); // NR30
		write(0xFF24, (byte) 0x77); // NR50
		// write(0xFF25, (byte) 0xF3)); // NR51
		write(0xFF26, (byte) 0xF1); // NR52*/

	}

	public byte read(int addr) {
		addr &= 0xFFFF;

		if (addr <= MemMap.Bank0.end)
			return rom[addr];
		if (addr <= MemMap.BankN.end)
			return (byte) (rom[addr + emulator.getGlobals().ROMbankoffset] & 0xFF);

		// Cartridge RAM
		if (addr >= MemMap.ExtRAM.start && addr <= MemMap.ExtRAM.end)
			return (byte) (cartRAM[addr + emulator.getGlobals().RAMbankoffset] & 0xFF);
		
		// Sound
		if (addr >= 0xFF10 && addr < 0xFF30) {
			byte r = (byte) (emulator.getSound().read(addr)&0xFF);
			System.out.println("Reading: " + Integer.toHexString(addr) + ":" + Integer.toHexString(r&0xFF));
			return r;
		}
		if(addr >= 0xFF30 && addr <= 0xFF3F) {
			byte r = (byte) (emulator.getSound().readWaveRAM(addr)&0xFF);
			//System.out.println("ReadingWave: " + Integer.toHexString(addr) + ":" + Integer.toHexString(r&0xFF));
			return r;
		}

		// Joypad
		if (addr == MemMap.IO.start) {
			if ((mem[MemMap.IO.start] & 0x20) > 0)
				return emulator.getGlobals().dpad;
			else if ((mem[MemMap.IO.start] & 0x10) > 0) {
				return emulator.getGlobals().buttons;
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

		if (addr >= 0xA000 && addr <= 0xBFFF && emulator.getGlobals().RAMenabled) {
			cartRAM[addr + emulator.getGlobals().RAMbankoffset] = data;
			return;
		}

		// DIV register: reset
		if (addr == 0xFF04) {
			mem[0xFF04] = 0;
			return;
		}

		// Timer control
		if (addr == 0xFF07) {
			emulator.getGlobals().timerEnable = ((data & 0x04) != 0);
			emulator.getGlobals().timerLength = emulator.getGlobals().timerLengths[data & 0x03];
			emulator.getGlobals().timerPrescaler = emulator.getGlobals().timerLength;
			mem[addr] = (byte) (0xF8 | data);
			return;
		}

		/*if (addr == 0xFF26) {
			if ((data & (1 << 7)) > 0) {
				mem[0xFF26] = (byte) (data & (1 << 7));
				emulator.getGlobals().soundEnabled = true;
				// audioCtx.resume()
			} else {
				emulator.getGlobals().soundEnabled = false;
				// should we set each oscillator to amplitude zero too?
				// audioCtx.suspend()
				// Zero all sound registers
				resetSoundRegisters();
			}
			return;
		}*/

		if (addr >= 0xFF10 && addr < 0xFF30) {
			//System.out.println("Writing: " + Integer.toHexString(addr) + ":" + Integer.toHexString(((int)data)&0xFF));
			emulator.getSound().write(addr, ((int)data)&0xFF);
		}
		
		if(addr >= 0xFF30 && addr <= 0xFF3F) {
			//System.out.println("WritingWave: " + Integer.toHexString(addr) + ":" + Integer.toHexString(((int)data)&0xFF));
			emulator.getSound().writeWaveRAM(addr, ((int)data)&0xFF);
		}

		// LCD control
		if (addr == 0xFF40) {
			int cc = data & 0x80;
			if (emulator.getGlobals().LCD_enabled != cc > 0) {
				emulator.getGlobals().LCD_enabled = cc > 0;
				if (!emulator.getGlobals().LCD_enabled) { // Disabling the display sets it to mode 1
					// this should also probably set all pixels to white
					emulator.getGlobals().LCD_scan = 0;
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
			int st = (data & 0xFF) << 8;
			for (int i = 0; i <= 0x9F; i++)
				mem[0xFE00 + i] = read(st + i);
			return;
		}

		// disable bootrom
		if (addr == 0xFF50) {
			System.out.println("Disabled Boot-ROM");
			for (int i = 0; i < 256; i++)
				rom[i] = (byte) (emulator.getGlobals().FirstROMPage[i] & 0xFF);
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
				emulator.getGlobals().RAMenabled = ((data & 0x0F) == 0xA);
			} else if (addr <= 0x3FFF) {
				data &= 0x1F;
				if (data == 0)
					data = 1; // MBC1 translates bank 0 to bank 1 (apparently regardless of upper bits)
				// set lowest 5 bits of bank number
				emulator.getGlobals().ROMbank = (emulator.getGlobals().ROMbank & 0xE0) | (data & 0x1F);
				emulator.getGlobals().ROMbankoffset = (emulator.getGlobals().ROMbank - 1) * 0x4000 % rom.length;
			} else if (addr <= 0x5fff) {
				data &= 0x3;
				if (emulator.getGlobals().MBCRamMode == 0) {
					emulator.getGlobals().ROMbank = (emulator.getGlobals().ROMbank & 0x1F) | (data << 5);
					emulator.getGlobals().ROMbankoffset = (emulator.getGlobals().ROMbank - 1) * 0x4000 % rom.length;
				} else {
					emulator.getGlobals().RAMbank = data;
					emulator.getGlobals().RAMbankoffset = emulator.getGlobals().RAMbank * 0x2000 - 0xA000;
				}
			} else {
				emulator.getGlobals().MBCRamMode = data & 1;
				if (emulator.getGlobals().MBCRamMode == 0) {
					emulator.getGlobals().RAMbank = 0;
					emulator.getGlobals().RAMbankoffset = emulator.getGlobals().RAMbank * 0x2000 - 0xA000;
				} else {
					emulator.getGlobals().ROMbank &= 0x1F;
					emulator.getGlobals().ROMbankoffset = (emulator.getGlobals().ROMbank - 1) * 0x4000 % rom.length;
				}
			}

			break;

		case 0x05: // MBC2
		case 0x06: // MBC2+BATTERY

			if (addr <= 0x1FFF) {
				if ((addr & 0x0100) == 0)
					emulator.getGlobals().RAMenabled = ((data & 0x0F) == 0xA);
			} else if (addr <= 0x3FFF) {
				data &= 0x0F;
				if (data == 0)
					data = 1;
				emulator.getGlobals().ROMbank = data;
				emulator.getGlobals().ROMbankoffset = (emulator.getGlobals().ROMbank - 1) * 0x4000 % rom.length;
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
				emulator.getGlobals().RAMenabled = ((data & 0x0F) == 0xA);
			} else if (addr <= 0x3FFF) {
				if (data == 0)
					data = 1; // allows access to banks 0x20, 0x40, 0x60
				emulator.getGlobals().ROMbank = data & 0x7F;
				emulator.getGlobals().ROMbankoffset = (emulator.getGlobals().ROMbank - 1) * 0x4000 % rom.length;
			} else if (addr <= 0x5fff) {
				if (data < 8) {
					emulator.getGlobals().RAMbank = data;
					emulator.getGlobals().RAMbankoffset = emulator.getGlobals().RAMbank * 0x2000 - 0xA000;
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
				emulator.getGlobals().RAMenabled = ((data & 0x0F) == 0xA);
			} else if (addr <= 0x2FFF) {
				// Allows access to bank 0
				emulator.getGlobals().ROMbank &= 0x100;
				emulator.getGlobals().ROMbank |= data;
				emulator.getGlobals().ROMbankoffset = (emulator.getGlobals().ROMbank - 1) * 0x4000;
				while (emulator.getGlobals().ROMbankoffset > rom.length)
					emulator.getGlobals().ROMbankoffset -= rom.length;
			} else if (addr <= 0x3FFF) {
				emulator.getGlobals().ROMbank &= 0xFF;
				if ((data & 1) > 0)
					emulator.getGlobals().ROMbank += 0x100;
				emulator.getGlobals().ROMbankoffset = (emulator.getGlobals().ROMbank - 1) * 0x4000;
				while (emulator.getGlobals().ROMbankoffset > rom.length)
					emulator.getGlobals().ROMbankoffset -= rom.length;
			} else if (addr <= 0x5fff) {
				emulator.getGlobals().RAMbank = data & 0x0F;
				emulator.getGlobals().RAMbankoffset = emulator.getGlobals().RAMbank * 0x2000 - 0xA000;
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
