package com.mrh0.gbemu.memory;

import com.mrh0.gbemu.Emulator;
import com.mrh0.gbemu.io.CartridgeSaves;

public class Memory {

	private byte[] ram;
	private byte[] cram;
	private byte[] rom;
	
	private byte[] wram;
	
	private byte[] vram1;

	private Emulator emulator;

	public Memory(Emulator emulator, int size) {
		this.emulator = emulator;
		this.ram = new byte[size];
		this.cram = new byte[0x8000];
	}
	
	public void setCGB() {
		wram = new byte[0x8000];
		vram1 = new byte[0x2000];
	}
	
	public void setROM(byte[] rom) {
		this.rom = rom;
	}

	public byte[] rom() {
		return rom;
	}

	public byte read(int addr) {
		addr &= 0xFFFF;
		
		if(emulator.getGlobals().bootROMEnabled) {
			if(emulator.isCGB()) {
				if(addr >= 0 && addr < 0x0900) {
					if(addr >= 0x0000 && addr < 0x0100)
						return emulator.getGlobals().bootROM[addr];
					else if(addr >= 0x0200 && addr < 0x0900)
						return emulator.getGlobals().bootROM[addr - 0x0100];
					else if(addr == 0xFF50)
						return (byte) 0xFF;
				}
					
			}
			else {
				if(addr >= 0 && addr < 0x0100)
					return emulator.getGlobals().bootROM[addr];
			}
		}

		if (addr <= MemMap.Bank0.end)
			return rom[addr];
		if (addr <= MemMap.BankN.end)
			return (byte) (rom[addr + emulator.getGlobals().ROMbankoffset] & 0xFF);

		// Cartridge RAM
		if (addr >= MemMap.ExtRAM.start && addr <= MemMap.ExtRAM.end)
			return (byte) (cram[addr + emulator.getGlobals().RAMbankoffset] & 0xFF);
		
		// Sound
		if (addr >= 0xFF10 && addr <= 0xFF30)
			return (byte) (emulator.getSound().read(addr)&0xFF);
		
		if(addr >= 0xFF30 && addr <= 0xFF3F)
			return (byte) (emulator.getSound().readWaveRAM(addr)&0xFF);

		// Joypad
		if (addr == MemMap.IO.start) {
			if ((ram[MemMap.IO.start] & 0x20) > 0)
				return emulator.getGlobals().dpad;
			else if ((ram[MemMap.IO.start] & 0x10) > 0)
				return emulator.getGlobals().buttons;
			return (byte) 0xFF;
		}
		
		
		//WRAM
		if(emulator.isCGB()) {
			if(addr >= 0xC000 && addr <= 0xCFFF)
				return (byte) (wram[addr-0xC000] & 0xFF);
			if(addr >= 0xD000 && addr <= 0xDFFF) {
				int bank = ram[0xFF70]&0xF9;
				return (byte) (wram[addr-0xD000+(bank*0x1000)] & 0xFF);
			}
		}
		
		return (byte) (ram[addr] & 0xFF);
	}

	public byte readRaw(int addr) {
		addr &= 0xFFFF;
		return (byte) (ram[addr] & 0xFF);
	}

	public void write(int addr, byte data) {
		addr &= 0xFFFF;
		data &= 0xFF;

		if (addr <= 0x7FFF) {
			doMBC(addr, data);
			return;
		}

		if (addr >= 0xA000 && addr <= 0xBFFF && emulator.getGlobals().RAMenabled) {
			cram[addr + emulator.getGlobals().RAMbankoffset] = data;
			return;
		}

		// Reset DIV register
		if (addr == 0xFF04) {
			ram[0xFF04] = 0;
			return;
		}

		// Timer control
		if (addr == 0xFF07) {
			emulator.getGlobals().timerEnable = ((data & 0x04) != 0);
			emulator.getGlobals().timerLength = emulator.getGlobals().timerLengths[data & 0x03];
			emulator.getGlobals().timerPrescaler = emulator.getGlobals().timerLength;
			ram[addr] = (byte) (0xF8 | data);
			return;
		}

		if (addr >= 0xFF10 && addr < 0xFF30) {
			emulator.getSound().write(addr, ((int)data)&0xFF);
		}
		
		if(addr >= 0xFF30 && addr <= 0xFF3F) {
			emulator.getSound().writeWaveRAM(addr, ((int)data)&0xFF);
		}
		
		//WRAM
		if(emulator.getGlobals().CGBMode) {
			if(addr >= 0xC000 && addr <= 0xCFFF)
				wram[addr-0xC000] = (byte) (data&0xFF);
			if(addr >= 0xD000 && addr <= 0xDFFF) {
				int bank = ram[0xFF70]&0xF9;
				wram[addr-0xD000+(bank*0x1000)] = (byte) (data&0xFF);
			}
		}

		// LCD control
		if(emulator.isCGB()) {
			// CGB
			byte vbk = ram[0xFF4F];
			if(addr >= 0x8000 && addr <= 0x9FFF) {
				if(vbk == 0x01)
					vram1[addr-0x8000] = (byte) (data&0xFF);
			}
		}
		else {
			// DMG
			if (addr == 0xFF40) {
				int cc = data & 0x80;
				if (emulator.getGlobals().LCD_enabled != cc > 0) {
					emulator.getGlobals().LCD_enabled = cc > 0;
					if (!emulator.getGlobals().LCD_enabled) {
						emulator.getGlobals().LCD_scan = 0;
						ram[0xFF41] = (byte) ((ram[0xFF41] & 0xFC) + 1);
					}
				}
			}
	
			if (addr == 0xFF41) {
				ram[0xFF41] &= 0x3;
				data &= 0xFC;
				ram[0xFF41] |= 0x80 | data;
				return;
			}
	
			if (addr == 0xFF44) {
				ram[0xFF44] = 0;
				return;
			}
	
			if (addr == 0xFF46) {
				int st = (data & 0xFF) << 8;
				for (int i = 0; i <= 0x9F; i++)
					ram[0xFE00 + i] = read(st + i);
				return;
			}
		}

		// Boot-ROM
		if (addr == 0xFF50) {
			System.out.println("Disabled Boot-ROM");
			/*for (int i = 0; i < emulator.getBootromLength(); i++)
				rom[i] = (byte) (emulator.getGlobals().FirstROMPage[i] & 0xFF);*/
			emulator.getGlobals().bootROMEnabled = false;
			return;
		}
		
		//CGB LCD control
		if(addr == 0xFF55) {
			
		}

		ram[addr] = data;
	}

	public void write16(int addr, byte data1, byte data2) {
		write(addr + 1, data1);
		write(addr, data2);
	}

	public void writeRaw(int addr, byte data) {
		addr &= 0xFFFF;
		ram[addr] = (byte) (data & 0xFF);
	}

	public byte[] raw() {
		return ram;
	}

	private void doMBC(int addr, byte data) {
		switch (rom[0x147] & 0xFF) {

		case 0: // ROM ONLY
			break;

		case 0x01: // MBC1
		case 0x02: // MBC1+RAM
		case 0x03: // MBC1+RAM+BATTERY
			if (addr <= 0x1FFF) {
				emulator.getGlobals().RAMenabled = ((data & 0x0F) == 0xA);
			}
			else if (addr <= 0x3FFF) {
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
			System.err.println("NOT IMPLEMENTED MEM: " + Integer.toHexString(addr));
			throw new IllegalArgumentException();
		}
	}
	
	public void RAMSave() {
		if(emulator.getGlobals().gameHasLoaded)
			CartridgeSaves.save(emulator.getGlobals().currentROMFile, emulator.getGlobals().currentROMName, cram);
	}
	public void RAMLoad() {
		byte[] bytes = CartridgeSaves.load(emulator.getGlobals().currentROMFile, emulator.getGlobals().currentROMName);
		if(bytes.length == cram.length)
			cram = bytes;
	}
}
