package com.mrh0.gbemu.cpu;

import com.mrh0.gbemu.cpu.memory.MemMap;
import com.mrh0.gbemu.cpu.memory.Memory;

public class CPU {
	private byte[] reg;
	private Memory mem;
	private Globals globals;

	public CPU(Memory mem, Globals globals) {
		reg = new byte[8];
		this.mem = mem;
		this.globals = globals;
	}

	private boolean flagZ = false;
	private boolean flagN = false;
	private boolean flagH = false;
	private boolean flagC = false;

	private int pc = 0;
	private int sp = 0;

	private boolean ime = false;
	private boolean halted = false;

	private final byte A = 0b111;
	private final byte B = 0b000;
	private final byte C = 0b001;
	private final byte D = 0b010;
	private final byte E = 0b011;
	private final byte H = 0b100;
	private final byte L = 0b101;

	private final byte HL = 0b110;

	private final int BC = 258;
	private final int DE = 259;
	private final int SPr = 260;

	private enum ALUOP {
		ADD, ADC, SUB, SBC, AND, OR, XOR, CP
	}

	private enum SHIFTOP {
		RLC, RRC, RL, RR, SLA, SRA, SRL
	}

	// Main:

	public void advance() {
		int cycles = 4;

		if (!halted)
			cycles = operation(mem.read(pc));

		if ((globals.divPrescaler += cycles) > 255) {
			globals.divPrescaler -= 256;
			mem.incRaw(0xFF04);
		}
		if (globals.timerEnable) {
			globals.timerPrescaler -= cycles;
			while (globals.timerPrescaler < 0) {
				globals.timerPrescaler += globals.timerLength;
				if (mem.incRaw(0xFF05) == 0xFF) {
					mem.writeRaw(0xFF05, mem.readRaw(0xFF06));
					// Set interrupt flag here
					mem.writeRaw(0xFF0F, (byte) (mem.readRaw(0xFF0F) | (1 << 2)));
					halted = false;
				}
			}
		}
	}

	public int operation(byte optcode) {
		return -1;
	}

	// Helpers:
	private int addr(int a, int b) {
		return (reg[a] << 8) + reg[b];
	}

	private int num(boolean b) {
		return b ? 1 : 0;
	}

	private boolean bool(int b) {
		return b > 0;
	}

	private boolean bool(byte b) {
		return b > 0;
	}

	// ALU:

	private int ALUI(ALUOP optcode) {
		reg[A] = ALUdo(optcode, mem.read(pc + 1));
		pc += 2;
		return 8;
	}

	private int ALUHL(ALUOP optcode) {
		reg[A] = ALUdo(optcode, mem.read(addr(H, L)));
		pc += 1;
		return 8;
	}

	private int ALU(ALUOP optcode, int a) {
		reg[A] = ALUdo(optcode, reg[a]);
		pc += 1;
		return 4;
	}

	private byte ALUdo(ALUOP optcode, int value) {
		byte res = reg[A];
		flagN = false;

		switch (optcode) {
		case ADD: // Add
			flagH = (((reg[A] & 0x0F) + (value & 0x0F)) & 0x10) > 0;
			res += value;
			break;
		case ADC:
			flagH = (((reg[A] & 0x0F) + (value & 0x0F) + num(flagC)) & 0x10) > 0;
			res += value + num(flagC);
			break;
		case SUB:
			res -= value;
			flagN = true;
			flagH = (((reg[A] & 0x0F) - (value & 0x0F)) & 0x10) > 0;
			break;
		case CP:
			res -= value;
			flagN = true;
			flagH = (((reg[A] & 0x0F) - (value & 0x0F)) & 0x10) > 0;
			flagZ = (res & 0xFF) == 0;
			flagC = res > 255 || res < 0;
			return reg[A];
		case SBC:
			res -= value + num(flagC);
			flagN = true;
			flagH = (((reg[A] & 0x0F) - (value & 0x0F) - num(flagC)) & 0x10) > 0;
			break;
		case AND:
			res &= value;
			flagH = true;
			break;
		case OR:
			res |= value;
			flagH = false;
			break;
		case XOR:
			res ^= value;
			flagH = false;
			break;
		}

		flagZ = (res & 0xFF) == 0;
		flagC = res > 255 || res < 0;
		return (byte) (res & 0xFF);
	}

	// Ops:
	private int ldI(int a) {
		reg[a] = mem.read(pc + 1);
		pc += 2;
		return 8;
	}

	private int ld(int a, int b) {
		reg[a] = reg[b];
		pc += 1;
		return 4;
	}

	private int ldFromMemI(int a) {
		reg[a] = mem.read(mem.read(pc + 1) + (mem.read(pc + 2) << 8));
		pc += 3;
		return 16;
	}

	private int ldFromMem(int a, int b, int c) {
		reg[a] = mem.read(addr(b, c));
		pc += 1;
		return 8;
	}

	private int ldToMemI(int a, int b) {
		mem.write(mem.read(pc + 1) + (mem.read(pc + 2) << 8), reg[b]);
		pc += 3;
		return 16;
	}

	private int ldToMemI(int a, int b, int c) {
		mem.write(addr(a, b), reg[c]);
		pc += 2;
		return 12;
	}

	private int ldToMem(int a, int b, int c) {
		mem.write(addr(a, b), reg[c]);
		pc += 1;
		return 8;
	}

	private int ld16I() {
		int addr = mem.read(pc + 1) + (mem.read(pc + 2) << 8);
		reg[H] = mem.read(addr + 1);
		reg[L] = mem.read(addr);
		pc += 3;
		return 12;
	}

	private int ld16sp() {
		sp = mem.read(pc + 1) + (mem.read(pc + 2) << 8);
		pc += 3;
		return 12;
	}

	private int ld16I(int a, int b) {
		reg[a] = mem.read(pc + 2);
		reg[b] = mem.read(pc + 1);
		pc += 3;
		return 12;
	}

	private int ld16() {
		sp = (reg[H] << 8) + reg[L];
		pc += 1;
		return 8;
	}

	private int lddhl() {
		mem.write(addr(H, L), reg[A]);
		if (reg[L] == 0)
			reg[H]--;
		reg[L]--;

		pc += 1;
		return 8;
	}

	private int ldd() {
		reg[A] = mem.read(addr(H, L));

		if (reg[L] == 0)
			reg[H]--;
		reg[L]--;

		pc++;
		return 8;
	}

	private int ldiHL() {
		mem.write(addr(H, L), reg[A]);

		if (reg[L] == 255)
			reg[H]++;
		reg[L]++;

		pc += 1;
		return 8;
	}

	private int ldi() {
		reg[A] = mem.read(addr(H, L));

		if (reg[L] == 255)
			reg[H]++;
		reg[L]++;

		pc += 1;
		return 8;
	}

	private int ldcA() {
		reg[A] = mem.read(MemMap.IO.start + reg[C]);
		pc += 1;
		return 8;
	}

	private int ldc() {
		mem.write(MemMap.IO.start + reg[C], reg[A]);
		pc += 1;
		return 8;
	}

	private int ldhA() {
		reg[A] = mem.read(MemMap.IO.start + mem.read(pc + 1));
		pc += 2;
		return 12;
	}

	private int ldh() {
		mem.write(MemMap.IO.start + mem.read(pc + 1), reg[A]);
		pc += 2;
		return 12;
	}

	private int incHL(int a) {
		mem.write(addr(H, L), offset(mem.read(addr(H, L)), 1));
		pc += 1;
		return 12;
	}

	private int decHL(int a) {
		mem.write(addr(H, L), offset(mem.read(addr(H, L)), -1));
		pc += 1;
		return 12;
	}

	private int inc(int a) {
		reg[a] = offset(reg[a], 1);
		pc += 1;
		return 4;
	}

	private int dec(int a) {
		reg[a] = offset(reg[a], -1);
		pc += 1;
		return 4;
	}

	private byte offset(byte a, int offset) {
		byte res = (byte) (a + offset);
		flagH = (((a & 0x0F) + offset) & 0x10) > 0;
		flagN = offset == -1;
		flagZ = ((res & 0xff) == 0);
		return res;
	}

	private int inc16() {
		sp++;
		pc++;
		return 8;
	}

	private int inc16(int a, int b) {
		if (reg[b] == 255)
			reg[a]++;
		reg[b]++;
		pc++;
		return 8;
	}

	private int dec16() {
		sp--;
		pc++;
		return 8;
	}

	private int dec16(int a, int b) {
		if (reg[b] == 0)
			reg[a]--;
		reg[b]--;
		pc++;
		return 8;
	}

	private int signedOffset(int a) {
		return (a > 127) ? (a - 256) : a;
	}

	private int jrNZ() {
		if (flagZ) {
			pc += 2;
			return 8;
		}
		pc += 2 + signedOffset(mem.read(pc + 1));
		return 12;
	}

	private int jrNC() {
		if (flagC) {
			pc += 2;
			return 8;
		}
		pc += 2 + signedOffset(mem.read(pc + 1));
		return 12;
	}

	private int jrZ() {
		if (!flagZ) {
			pc += 2;
			return 8;
		}
		pc += 2 + signedOffset(mem.read(pc + 1));
		return 12;
	}

	private int jrC() {
		if (!flagC) {
			pc += 2;
			return 8;
		}
		pc += 2 + signedOffset(mem.read(pc + 1));
		return 12;
	}

	private int jr() { // unconditional relative
		pc += 2 + signedOffset(mem.read(pc + 1));
		return 12;
	}

	private int jp() { // unconditional absolute
		pc = mem.read(pc + 1) + (mem.read(pc + 2) << 8);
		return 16;
	}

	private int jpNZ() {
		if (flagZ) {
			pc += 3;
			return 12;
		}
		pc = mem.read(pc + 1) + (mem.read(pc + 2) << 8);
		return 16;
	}

	private int jpNC() {
		if (flagC) {
			pc += 3;
			return 12;
		}
		pc = mem.read(pc + 1) + (mem.read(pc + 2) << 8);
		return 16;
	}

	private int jpZ() {
		if (!flagZ) {
			pc += 3;
			return 12;
		}
		pc = mem.read(pc + 1) + (mem.read(pc + 2) << 8);
		return 16;
	}

	private int jpc() {
		if (!flagC) {
			pc += 3;
			return 12;
		}
		pc = mem.read(pc + 1) + (mem.read(pc + 2) << 8);
		return 16;
	}

	private int jpHL() {
		pc = addr(H, L);
		return 4;
	}

	private int push() {
		byte flags = (byte) ((num(flagZ) << 7) + (num(flagN) << 6) + (num(flagH) << 5) + (num(flagC) << 4));
		sp -= 2;
		mem.write16(sp, reg[A], flags);
		pc += 1;
		return 16;
	}

	private int push(int a, int b) {
		sp -= 2;
		mem.write16(sp, reg[a], reg[b]);
		pc += 1;
		return 16;
	}

	private int pop() {
		reg[A] = mem.read(sp + 1); // 1st of read16
		byte s = mem.read(sp); // 2and of read16
		flagZ = (s & (1 << 7)) != 0;
		flagN = (s & (1 << 6)) != 0;
		flagH = (s & (1 << 5)) != 0;
		flagC = (s & (1 << 4)) != 0;
		sp += 2;
		pc += 1;
		return 12;
	}

	private int pop(int a, int b) {
		reg[a] = mem.read(sp + 1);
		reg[b] = mem.read(sp);
		sp += 2;
		pc += 1;
		return 12;
	}

	private int call() {
		sp -= 2;
		var npc = pc + 3;
		mem.write16(sp, (byte) (npc >> 8), (byte) (npc & 0xFF));
		pc = mem.read(pc + 1) + (mem.read(pc + 2) << 8);
		return 24;
	}

	private int callNZ() {
		if (flagZ) {
			pc += 3;
			return 12;
		}
		return call();
	}

	private int callNC() {
		if (flagC) {
			pc += 3;
			return 12;
		}
		return call();
	}

	private int callZ() {
		if (!flagZ) {
			pc += 3;
			return 12;
		}
		return call();
	}

	private int callC() {
		if (!flagC) {
			pc += 3;
			return 12;
		}
		return call();
	}

	private int ret() {
		sp += 2;
		pc = (mem.read(sp + 1) << 8) + mem.read(sp);
		return 16;
	}

	private int retNZ() {
		if (flagZ) {
			pc++;
			return 8;
		}
		ret();
		return 20;
	}

	private int retNC() {
		if (flagC) {
			pc++;
			return 8;
		}
		ret();
		return 20;
	}

	private int retZ() {
		if (!flagZ) {
			pc++;
			return 8;
		}
		ret();
		return 20;
	}

	private int retC() {
		if (!flagC) {
			pc++;
			return 8;
		}
		ret();
		return 20;
	}

	private int reti() {
		ime = true;
		return ret();
	}

	private int ei() {
		// This needs to wait until the end of the next instruction
		ime = true;
		pc += 1;
		return 4;
	}

	private int di() {
		ime = false;
		pc++;
		return 4;
	}

	private int rst(int a) {
		sp -= 2;
		int npc = pc + 1;
		mem.write16(sp, (byte) (npc >> 8), (byte) (npc & 0xFF));
		pc = a;
		return 16;
	}

	private int shiftFast(SHIFTOP optcode, int a) {
		reg[a] = (byte) shiftDo(optcode, a);
		flagZ = false;
		pc+=1;
		return 4;
	}
	
	private int shift(SHIFTOP optcode) {
		int addr = addr(H, L);
		mem.write(addr, (byte) shiftDo(optcode, mem.read(addr)));
		pc+=1;
		return 16;
	}
	
	private int shift(SHIFTOP optcode, int a) {
		reg[a] = (byte) shiftDo(optcode, reg[a]);
		pc+=1;
		return 8;
	}

	private int shiftDo(SHIFTOP optcode, int a) {

		byte bit7 = (byte) (a >> 7), bit0 = (byte) (a & 1);

		switch (optcode) {
		case RLC: // Rotate byte left, save carry
			a = ((a << 1) & 0xff) + bit7;
			flagC = bool(bit7);
			break;
		case RRC: // Rotate byte right, save carry
			a = ((a >> 1) & 0xff) + (bit0 << 7);
			flagC = bool(bit0);
			break;
		case RL: // Rotate left through carry
			a = ((a << 1) & 0xff) + num(flagC);
			flagC = bool(bit7);
			break;
		case RR: // Rotate right through carry
			a = ((a >> 1) & 0xff) + (num(flagC) << 7);
			flagC = bool(bit0);
			break;
		case SLA: // Shift left
			a = ((a << 1) & 0xff);
			flagC = bool(bit7);
			break;
		case SRA: // Shift right arithmetic
			a = ((a >> 1) & 0xff) + (bit7 << 7);
			flagC = bool(bit0);
			break;
		case SRL: // Shift right logical
			a = ((a >> 1) & 0xff);
			flagC = bool(bit0);
			break;
		}

		flagN = false;
		flagH = false;
		flagZ = (a & 0xFF) == 0;
		return a;
	}

	private int ccf() {
		flagN = false;
		flagH = false;
		flagC = !flagC;
		pc += 1;
		return 4;
	}

	private int scf() {
		flagN = false;
		flagH = false;
		flagC = true;
		pc += 1;
		return 4;
	}

	private int cpl() {
		reg[A] = (byte) ~reg[A];
		flagN = true;
		flagH = true;
		pc += 1;
		return 4;
	}
}
