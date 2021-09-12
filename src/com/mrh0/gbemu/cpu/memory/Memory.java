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
		// write(0xFF25, (byte) 0xF3); // NR51
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
			globals.timerEnable = ((data & 0x04) != 0);
			globals.timerLength = globals.timerLengths[data & 0x03];
			globals.timerPrescaler = globals.timerLength;
			mem[addr] = (byte) (0xF8 | data);
			return;
		}

		if (addr == 0xFF26) {
			if ((data & (1 << 7)) > 0) {
				mem[0xFF26] = (byte) (data & (1 << 7));
				globals.soundEnabled = true;
				// audioCtx.resume()
			} else {
				globals.soundEnabled = false;
				// should we set each oscillator to amplitude zero too?
				// audioCtx.suspend()
				// Zero all sound registers
				resetSoundRegisters();
			}
			return;
		}

		if (addr >= 0xFF10 && addr <= 0xFF25) {
			if (!globals.soundEnabled)
				return;
			// FF10 - NR10 - Channel 1 Sweep register (R/W)
			if (addr == 0xFF10) {
				/*
				 * sound[1].sweepTime = (data>>4)&0x7; sound[1].sweepPrescaler =
				 * sound[1].sweepTime; sound[1].sweepDir = (data&(1<<3)) ? 0 : 1;
				 * sound[1].sweepShift = data&0x7;
				 */
				mem[addr] = (byte) (data & 0x80);
				return;
			}
			// FF11 - NR11 - Channel 1 Sound length/Wave pattern duty (R/W)
			if (addr == 0xFF11) {
				mem[addr] = data;
				// sound[1].duty(data>>6)
				return;
			}
			// FF12 - NR12 - Channel 1 Volume Envelope (R/W)
			if (addr == 0xFF12) {
				mem[addr] = data;
				/*
				 * sound[1].envDirection = (data&(1<<3)) ? 1: -1; sound[1].envSpeed = data&0x7;
				 * sound[1].envCounter = 0;
				 */
				return;
			}
			// FF13 - NR13 - Channel 1 Frequency lo (Write Only)
			if (addr == 0xFF13) {
				/*
				 * sound[1].freqnum=(((MEM[0xFF14]&0x7)<<8)+ data); sound[1].freq( 131072/(2048-
				 * sound[1].freqnum ) )
				 */
				mem[addr] = data;
				return;
			}
			// FF14 - NR14 - Channel 1 Frequency hi (R/W)
			if (addr == 0xFF14) {
				// bit 7 is initialize
				/*
				 * sound[1].freqnum=(((data&0x7)<<8)+ MEM[0xFF13]); sound[1].freq( 131072/(2048-
				 * sound[1].freqnum ) )
				 */
				if ((data & (1 << 7)) > 0) {
					/*
					 * sound[1].initialized = true sound[1].env = MEM[0xFF12]>>4; // default
					 * envelope value sound[1].envCounter = 0; sound[1].amp( sound[1].env/15 )
					 * 
					 * sound[1].lengthEnabled = (data&(1<<6)) !=0; sound[1].length =
					 * (64-(MEM[0xFF11]&0x3F));
					 */

					mem[0xFF26] |= (1 << 0); // flag sound 1 as on
					// if (sound[1].sweepShift) {sweepCalculate()}
				}
				mem[addr] = data;
				return;
			}

			// FF16 - NR21 - Channel 2 Sound Length/Wave Pattern Duty (R/W)
			// Bit 7-6 - Wave Pattern Duty (Read/Write)
			// Bit 5-0 - Sound length data (Write Only) (t1: 0-63)
			if (addr == 0xFF16) {
				mem[addr] = data;
				// sound[2].duty(data>>6)
				return;
			}

			// FF17 - NR22 - Channel 2 Volume Envelope (R/W)
			if (addr == 0xFF17) {
				mem[addr] = data;
				/*
				 * sound[2].envDirection = (data&(1<<3)) ? 1: -1; sound[2].envSpeed = data&0x7;
				 * sound[2].envCounter = 0;
				 */
				return;
			}
			// FF18 - NR23 - Channel 2 Frequency lo data (W)
			if (addr == 0xFF18) {
				// sound[2].freq( 131072/(2048- (((MEM[0xFF19]&0x7)<<8)+ data) ) )
				mem[addr] = data;
				return;
			}
			// FF19 - NR24 - Channel 2 Frequency hi data (R/W)
			if (addr == 0xFF19) {
				// sound[2].freq( 131072/(2048- (((data&0x7)<<8)+ MEM[0xFF18]) ) )
				// bit 7 is initialize
				if ((data & (1 << 7)) > 0) {
					/*
					 * sound[2].initialized = true sound[2].env = MEM[0xFF17]>>4; //Default envelope
					 * value sound[2].envCounter = 0; sound[2].amp( sound[2].env/15 )
					 * 
					 * sound[2].lengthEnabled = (data&(1<<6)) !=0; sound[2].length =
					 * (64-(MEM[0xFF16]&0x3F));
					 */
					mem[0xFF26] |= (1 << 1); // flag sound 2 as on
				}
				mem[addr] = data;
				return;
			}

			// Sound 3 - user-defined waveform
			// "it can output a sound while changing its length, frequency, and level"
			// not sure what changing its length means

			// FF1A - NR30 - Channel 3 Sound on/off (R/W)
			if (addr == 0xFF1A) {
				if ((data & (1 << 7)) > 0) {
					// sound[3].initialized=true;

					// is this the right (only?) place to load the waveform?
					// setSound3Waveform()

				} else {
					// sound[3].initialized=false;
					// sound[3].amp(0)
				}
				return;
			}
			// FF1B - NR31 - Channel 3 Sound Length
			if (addr == 0xFF1B) {
				mem[addr] = data;
				return;
			}
			// FF1C - NR32 - Channel 3 Select output level (R/W)
			if (addr == 0xFF1C) {
				// Really we ought to bit-crush it, but whatever
				// if (sound[3].initialized)
				// sound[3].amp( [ 0,0.5,0.25,0.125 ][((data>>5)&0x3)] )
				mem[addr] = data;
				return;
			}

			// FF1D - NR33 - Channel 3 Frequency's lower data (W)
			if (addr == 0xFF1D) {
				// sound[3].freq( 65536/(2048- (((MEM[0xFF1E]&0x7)<<8)+ data) ) )
				mem[addr] = data;
				return;
			}
			// FF1E - NR34 - Channel 3 Frequency's higher data (R/W)
			if (addr == 0xFF1E) {
				// sound[3].freq( 65536/(2048- (((data&0x7)<<8)+ MEM[0xFF1D]) ) )
				// bit 7 is initialize
				if ((data & (1 << 7)) > 0) {

					/*
					 * sound[3].initialized = true
					 * 
					 * sound[3].amp( [ 0,0.5,0.25,0.15 ][((MEM[0xFF1C]>>5)&0x3)] )
					 * 
					 * sound[3].lengthEnabled = (data&(1<<6)) !=0; sound[3].length =
					 * (256-MEM[0xFF1B]);
					 */

					mem[0xFF26] |= (1 << 2); // flag sound 3 as on
				}
				mem[addr] = data;
				return;
			}

			// Sound 4 - Noise
			// FF20 - NR41 - Channel 4 Sound Length (R/W)
			if (addr == 0xFF20) {
				mem[addr] = data;
				return;
			}
			// FF21 - NR42 - Channel 4 Volume Envelope (R/W)
			if (addr == 0xFF21) {
				mem[addr] = data;
				/*
				 * sound[4].envDirection = (data&(1<<3)) ? 1: -1; sound[4].envSpeed = data&0x7;
				 * sound[4].envCounter = 0;
				 */
				return;
			}
			// FF22 - NR43 - Channel 4 Polynomial Counter (R/W)
			if (addr == 0xFF22) {
				/*
				 * sound[4].freq(data>>4, data&0x7) sound[4].polySteps(data&(1<<3))
				 */

				mem[addr] = data;
				return;
			}
			// FF23 - NR44 - Channel 4 Counter/consecutive; Inital (R/W)
			if (addr == 0xFF23) {

				/*
				 * sound[4].initialized = true sound[4].env = MEM[0xFF21]>>4; //Default envelope
				 * value sound[4].envCounter = 0; sound[4].amp( sound[4].env/15 )
				 * sound[4].length = (64-(MEM[0xFF20]&0x3F));
				 */

				mem[0xFF26] |= (1 << 3); // flag sound 4 as on

				// sound[4].lengthEnabled = (data&(1<<6)) !=0;
				mem[addr] = data;
				return;
			}

			// FF24 - NR50 - Channel control / ON-OFF / Volume (R/W)
			if (addr == 0xFF24) {
				// Bit 7 - Output Vin to SO2 terminal (1=Enable)
				// Bit 6-4 - SO2 output level (volume) (0-7)
				// Bit 3 - Output Vin to SO1 terminal (1=Enable)
				// Bit 2-0 - SO1 output level (volume) (0-7)

				// is level zero mute ? "minimum level"
				/*
				 * sound.SO2.gain.setValueAtTime(((data>>4)&0x7)/7, audioCtx.currentTime)
				 * sound.SO1.gain.setValueAtTime((data&0x7)/7, audioCtx.currentTime)
				 */
				mem[addr] = data;
				return;
			}

			// FF25 - NR51 - Selection of Sound output terminal (R/W)
			if (addr == 0xff25) {

				/*
				 * var con = (MEM[0xff25]^data) & data; var dis = (MEM[0xff25]^data) & (~data);
				 * 
				 * for (var i=0;i<4;i++) { if (con&(1<<i))
				 * sound[i+1].gainNode.connect(sound.SO1) if (dis&(1<<i))
				 * sound[i+1].gainNode.disconnect(sound.SO1) if (con&(1<<(4+i)))
				 * sound[i+1].gainNode.connect(sound.SO2) if (dis&(1<<(4+i)))
				 * sound[i+1].gainNode.disconnect(sound.SO2) }
				 */

				mem[addr] = data;
				return;
			}

			return;
		}
		/*
		 * if (addr>=0xFF30 && addr<=0xFF3F) sound[3].waveChanged=true;
		 */

		// LCD control
		if (addr == 0xFF40) {
			int cc = data & 0x80;
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
			int st = (data & 0xFF) << 8;
			for (int i = 0; i <= 0x9F; i++)
				mem[0xFE00 + i] = read(st + i);
			return;
		}

		// disable bootrom
		if (addr == 0xFF50) {
			System.out.println("Disabled Boot-ROM");
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
