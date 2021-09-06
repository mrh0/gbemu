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

	public int operation(int opcode) {
		switch(opcode) {
			case 0x00: return nop();
			case 0x01: return ld16I(B,C);
			case 0x02: return ldToMem(B,C,A);
			case 0x03: return inc16(B,C);
			case 0x04: return inc(B);
			case 0x05: return dec(B);
			case 0x06: return ldI(B);
			case 0x07: return shiftFast(SHIFTOP.RLC, A); //rlca
			case 0x08: return ld_imm_sp(); //LD   (nn),SP
			case 0x09: return addHL(B,C);
			case 0x0A: return ldFromMem(A, B, C);
			case 0x0B: return dec16(B,C);
			case 0x0C: return inc(C);
			case 0x0D: return dec(C);
			case 0x0E: return ldI(C);
			case 0x0F: return shiftFast(SHIFTOP.RRC, A);
	
	
			case 0x10: return stop();
			case 0x11: return ld16I(D,E);
			case 0x12: return ldToMem(D,E,A);
			case 0x13: return inc16(D,E);
			case 0x14: return inc(D);
			case 0x15: return dec(D);
			case 0x16: return ldI(D);
			case 0x17: return shiftFast(SHIFTOP.RL, A);
			case 0x18: return jr();
			case 0x19: return addHL(D,E); //ADD HL, DE
			case 0x1A: return ldFromMem(A, D, E);
			case 0x1B: return dec16(D,E);
			case 0x1C: return inc(E);
			case 0x1D: return dec(E);
			case 0x1E: return ldI(E);
			case 0x1F: return shiftFast(SHIFTOP.RR, A);
	
			case 0x20: return jrNZ();
			case 0x21: return ld16I(H,L);
			case 0x22: return ldi();
			case 0x23: return inc16(H,L);
			case 0x24: return inc(H);
			case 0x25: return dec(H);
			case 0x26: return ldI(H);
			case 0x27: return daa();
			case 0x28: return jrZ();
			case 0x29: return addHL(H,L);
			case 0x2A: return ldi();
			case 0x2B: return dec16(H,L);
			case 0x2C: return inc(L);
			case 0x2D: return dec(L);
			case 0x2E: return ldI(L);
			case 0x2F: return cpl();
	
			case 0x30: return jrNC();
			case 0x31: return ld16sp();
			case 0x32: return ldd();
			case 0x33: return inc16();
			case 0x34: return inc(HL);
			case 0x35: return dec(HL);
			case 0x36: return ldToMem(A, H, L); //was ldToMem(H,L);
			case 0x37: return scf();
			case 0x38: return jrC();
			case 0x39: return addHL();
			case 0x3A: return ldd();
			case 0x3B: return dec16();
			case 0x3C: return inc(A);
			case 0x3D: return dec(A);
			case 0x3E: return ldI(A);
			case 0x3F: return ccf();
	
	
			case 0x40: return ld(B,B);
			case 0x41: return ld(B,C);
			case 0x42: return ld(B,D);
			case 0x43: return ld(B,E);
			case 0x44: return ld(B,H);
			case 0x45: return ld(B,L);
			case 0x46: return ldFromMem(B, H, L);
			case 0x47: return ld(B,A);
	
			case 0x48: return ld(C,B);
			case 0x49: return ld(C,C);
			case 0x4A: return ld(C,D);
			case 0x4B: return ld(C,E);
			case 0x4C: return ld(C,H);
			case 0x4D: return ld(C,L);
			case 0x4E: return ldFromMem(C, H, L);
			case 0x4F: return ld(C,A);
	
			case 0x50: return ld(D,B);
			case 0x51: return ld(D,C);
			case 0x52: return ld(D,D);
			case 0x53: return ld(D,E);
			case 0x54: return ld(D,H);
			case 0x55: return ld(D,L);
			case 0x56: return ldFromMem(D, H, L);
			case 0x57: return ld(D,A);
	
			case 0x58: return ld(E,B);
			case 0x59: return ld(E,C);
			case 0x5A: return ld(E,D);
			case 0x5B: return ld(E,E);
			case 0x5C: return ld(E,H);
			case 0x5D: return ld(E,L);
			case 0x5E: return ldFromMem(E, H, L);
			case 0x5F: return ld(E,A);
	
			case 0x60: return ld(H,B);
			case 0x61: return ld(H,C);
			case 0x62: return ld(H,D);
			case 0x63: return ld(H,E);
			case 0x64: return ld(H,H);
			case 0x65: return ld(H,L);
			case 0x66: return ldFromMem(H, H, L);
			case 0x67: return ld(H,A);
	
			case 0x68: return ld(L,B);
			case 0x69: return ld(L,C);
			case 0x6A: return ld(L,D);
			case 0x6B: return ld(L,E);
			case 0x6C: return ld(L,H);
			case 0x6D: return ld(L,L);
			case 0x6E: return ldFromMem(L, H, L);
			case 0x6F: return ld(L,A);
	
			case 0x70: return ldToMem(H,L, B);
			case 0x71: return ldToMem(H,L, C);
			case 0x72: return ldToMem(H,L, D);
			case 0x73: return ldToMem(H,L, E);
			case 0x74: return ldToMem(H,L, H);
			case 0x75: return ldToMem(H,L, L);
			case 0x76: return halt();
			case 0x77: return ldToMem(H,L, A);
	
			case 0x78: return ld(A,B);
			case 0x79: return ld(A,C);
			case 0x7A: return ld(A,D);
			case 0x7B: return ld(A,E);
			case 0x7C: return ld(A,H);
			case 0x7D: return ld(A,L);
			case 0x7E: return ldFromMem(A, H, L );
			case 0x7F: return ld(A,A);
	
			case 0x80: return ALU(ALUOP.ADD,A,B);
			case 0x81: return ALU(ALUOP.ADD,A,C);
			case 0x82: return ALU(ALUOP.ADD,A,D);
			case 0x83: return ALU(ALUOP.ADD,A,E);
			case 0x84: return ALU(ALUOP.ADD,A,H);
			case 0x85: return ALU(ALUOP.ADD,A,L);
			case 0x86: return ALU(ALUOP.ADD);
			case 0x87: return ALU(ALUOP.ADD,A,A);
	
			case 0x88: return ALU(ALUOP.ADC,A,B);
			case 0x89: return ALU(ALUOP.ADC,A,C);
			case 0x8A: return ALU(ALUOP.ADC,A,D);
			case 0x8B: return ALU(ALUOP.ADC,A,E);
			case 0x8C: return ALU(ALUOP.ADC,A,H);
			case 0x8D: return ALU(ALUOP.ADC,A,L);
			case 0x8E: return ALU(ALUOP.ADC);
			case 0x8F: return ALU(ALUOP.ADC,A,A);
	
			case 0x90: return ALU(ALUOP.SUB,A,B);
			case 0x91: return ALU(ALUOP.SUB,A,C);
			case 0x92: return ALU(ALUOP.SUB,A,D);
			case 0x93: return ALU(ALUOP.SUB,A,E);
			case 0x94: return ALU(ALUOP.SUB,A,H);
			case 0x95: return ALU(ALUOP.SUB,A,L);
			case 0x96: return ALU(ALUOP.SUB);
			case 0x97: return ALU(ALUOP.SUB,A,A);
	
			case 0x98: return ALU(ALUOP.SBC,A,B);
			case 0x99: return ALU(ALUOP.SBC,A,C);
			case 0x9A: return ALU(ALUOP.SBC,A,D);
			case 0x9B: return ALU(ALUOP.SBC,A,E);
			case 0x9C: return ALU(ALUOP.SBC,A,H);
			case 0x9D: return ALU(ALUOP.SBC,A,L);
			case 0x9E: return ALU(ALUOP.SBC);
			case 0x9F: return ALU(ALUOP.SBC,A,A);
	
			case 0xA0: return ALU(ALUOP.AND,A,B);
			case 0xA1: return ALU(ALUOP.AND,A,C);
			case 0xA2: return ALU(ALUOP.AND,A,D);
			case 0xA3: return ALU(ALUOP.AND,A,E);
			case 0xA4: return ALU(ALUOP.AND,A,H);
			case 0xA5: return ALU(ALUOP.AND,A,L);
			case 0xA6: return ALU(ALUOP.AND);
			case 0xA7: return ALU(ALUOP.AND,A,A);
	
			case 0xA8: return ALU(ALUOP.XOR,A,B);
			case 0xA9: return ALU(ALUOP.XOR,A,C);
			case 0xAA: return ALU(ALUOP.XOR,A,D);
			case 0xAB: return ALU(ALUOP.XOR,A,E);
			case 0xAC: return ALU(ALUOP.XOR,A,H);
			case 0xAD: return ALU(ALUOP.XOR,A,L);
			case 0xAE: return ALU(ALUOP.XOR);
			case 0xAF: return ALU(ALUOP.XOR,A,A);
	
			case 0xB0: return ALU(ALUOP.OR,A,B);
			case 0xB1: return ALU(ALUOP.OR,A,C);
			case 0xB2: return ALU(ALUOP.OR,A,D);
			case 0xB3: return ALU(ALUOP.OR,A,E);
			case 0xB4: return ALU(ALUOP.OR,A,H);
			case 0xB5: return ALU(ALUOP.OR,A,L);
			case 0xB6: return ALU(ALUOP.OR);
			case 0xB7: return ALU(ALUOP.OR,A,A);
	
			case 0xB8: return ALU(ALUOP.CP,A,B);
			case 0xB9: return ALU(ALUOP.CP,A,C);
			case 0xBA: return ALU(ALUOP.CP,A,D);
			case 0xBB: return ALU(ALUOP.CP,A,E);
			case 0xBC: return ALU(ALUOP.CP,A,H);
			case 0xBD: return ALU(ALUOP.CP,A,L);
			case 0xBE: return ALU(ALUOP.CP,A, HL );
			case 0xBF: return ALU(ALUOP.CP,A,A);
	
			case 0xC0: return retNZ();
			case 0xC1: return pop(B,C);
			case 0xC2: return jpNZ();
			case 0xC3: return jp();
			case 0xC4: return callNZ();
			case 0xC5: return push(B,C);
			case 0xC6: return ALUI(ALUOP.ADD);
			case 0xC7: return rst(0x00);
			case 0xC8: return retZ();
			case 0xC9: return ret();
			case 0xCA: return jpZ();
			case 0xCB: return CBop(mem.read(++pc));
			case 0xCC: return callZ();
			case 0xCD: return call();
			case 0xCE: return ALUI(ALUOP.ADC);
			case 0xCF: return rst(0x08);
	
			case 0xD0: return retNC();
			case 0xD1: return pop(D,E);
			case 0xD2: return jpNC();
			case 0xD3: return unused();
			case 0xD4: return callNC();
			case 0xD5: return push(D,E);
			case 0xD6: return ALUI(ALUOP.SUB);
			case 0xD7: return rst(0x10);
			case 0xD8: return retC();
			case 0xD9: return reti(); //RETI
			case 0xDA: return jpC();
			case 0xDB: return unused();
			case 0xDC: return callC();
			case 0xDD: return unused();
			case 0xDE: return ALUI(ALUOP.SBC);
			case 0xDF: return rst(0x18);
	
			case 0xE0: return ldh(); //LD   (FF00+n),A
			case 0xE1: return pop(H,L);
			case 0xE2: return ldc(); //LD   (FF00+C),A
			case 0xE3: return unused();
			case 0xE4: return unused();
			case 0xE5: return push(H,L);
			case 0xE6: return ALUI(ALUOP.AND);
			case 0xE7: return rst(0x20);
			case 0xE8: return add_sp_n(); //ADD  SP,dd
			case 0xE9: return jpHL();
			case 0xEA: return ldToMemI(A); //LD   (nn),A
			case 0xEB: return unused();
			case 0xEC: return unused();
			case 0xED: return unused();
			case 0xEE: return ALUI(ALUOP.XOR);
			case 0xEF: return rst(0x28);
	
			case 0xF0: return ldhA(); //LD   A,(FF00+n)
			case 0xF1: return pop();
			case 0xF2: return ldcA(); //LD   A,(FF00+C)
			case 0xF3: return di();
			case 0xF4: return unused();
			case 0xF5: return push();
			case 0xF6: return ALUI(ALUOP.OR);
			case 0xF7: return rst(0x30);
			case 0xF8: return ld_hl_spdd(); //LD   HL,SP+dd
			case 0xF9: return ld16();
			case 0xFA: return ldFromMemI(A); //LD   A,(nn)
			case 0xFB: return ei();
			case 0xFC: return unused();
			case 0xFD: return unused();
			case 0xFE: return ALUI(ALUOP.CP);
			case 0xFF: return rst(0x38);
		}
		return -1;
	}
	
	public int CBop(int opcode) {
		switch(opcode) {
			case 0x00: return shift(SHIFTOP.RLC, B);
			case 0x01: return shift(SHIFTOP.RLC, C);
			case 0x02: return shift(SHIFTOP.RLC, D);
			case 0x03: return shift(SHIFTOP.RLC, E);
			case 0x04: return shift(SHIFTOP.RLC, H);
			case 0x05: return shift(SHIFTOP.RLC, L);
			case 0x06: return shift(SHIFTOP.RLC, HL);
			case 0x07: return shift(SHIFTOP.RLC, A);
			case 0x08: return shift(SHIFTOP.RRC, B);
			case 0x09: return shift(SHIFTOP.RRC, C);
			case 0x0A: return shift(SHIFTOP.RRC, D);
			case 0x0B: return shift(SHIFTOP.RRC, E);
			case 0x0C: return shift(SHIFTOP.RRC, H);
			case 0x0D: return shift(SHIFTOP.RRC, L);
			case 0x0E: return shift(SHIFTOP.RRC, HL);
			case 0x0F: return shift(SHIFTOP.RRC, A);
	
			case 0x10: return shift(SHIFTOP.RL, B);
			case 0x11: return shift(SHIFTOP.RL, C);
			case 0x12: return shift(SHIFTOP.RL, D);
			case 0x13: return shift(SHIFTOP.RL, E);
			case 0x14: return shift(SHIFTOP.RL, H);
			case 0x15: return shift(SHIFTOP.RL, L);
			case 0x16: return shift(SHIFTOP.RL, HL);
			case 0x17: return shift(SHIFTOP.RL, A);
			case 0x18: return shift(SHIFTOP.RR, B);
			case 0x19: return shift(SHIFTOP.RR, C);
			case 0x1A: return shift(SHIFTOP.RR, D);
			case 0x1B: return shift(SHIFTOP.RR, E);
			case 0x1C: return shift(SHIFTOP.RR, H);
			case 0x1D: return shift(SHIFTOP.RR, L);
			case 0x1E: return shift(SHIFTOP.RR, HL);
			case 0x1F: return shift(SHIFTOP.RR, A);
	
			case 0x20: return shift(SHIFTOP.SLA, B);
			case 0x21: return shift(SHIFTOP.SLA, C);
			case 0x22: return shift(SHIFTOP.SLA, D);
			case 0x23: return shift(SHIFTOP.SLA, E);
			case 0x24: return shift(SHIFTOP.SLA, H);
			case 0x25: return shift(SHIFTOP.SLA, L);
			case 0x26: return shift(SHIFTOP.SLA, HL);
			case 0x27: return shift(SHIFTOP.SLA, A);
			case 0x28: return shift(SHIFTOP.SRA, B);
			case 0x29: return shift(SHIFTOP.SRA, C);
			case 0x2A: return shift(SHIFTOP.SRA, D);
			case 0x2B: return shift(SHIFTOP.SRA, E);
			case 0x2C: return shift(SHIFTOP.SRA, H);
			case 0x2D: return shift(SHIFTOP.SRA, L);
			case 0x2E: return shift(SHIFTOP.SRA, HL);
			case 0x2F: return shift(SHIFTOP.SRA, A);
	
			case 0x38: return shift(SHIFTOP.SRL, B);
			case 0x39: return shift(SHIFTOP.SRL, C);
			case 0x3A: return shift(SHIFTOP.SRL, D);
			case 0x3B: return shift(SHIFTOP.SRL, E);
			case 0x3C: return shift(SHIFTOP.SRL, H);
			case 0x3D: return shift(SHIFTOP.SRL, L);
			case 0x3E: return shift(SHIFTOP.SRL, HL);
			case 0x3F: return shift(SHIFTOP.SRL, A);
		}
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

	private int ALUI(ALUOP opcode) {
		reg[A] = ALUdo(opcode, mem.read(pc + 1));
		pc += 2;
		return 8;
	}

	private int ALU(ALUOP opcode) {
		reg[A] = ALUdo(opcode, mem.read(addr(H, L)));
		pc += 1;
		return 8;
	}

	private int ALU(ALUOP opcode, int a, int b) {
		reg[a] = ALUdo(opcode, reg[b]);
		pc += 1;
		return 4;
	}

	private byte ALUdo(ALUOP opcode, int value) {
		byte res = reg[A];
		flagN = false;

		switch (opcode) {
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

	private int ldToMemI(int b) {
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

	private int jpC() {
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

	private int shiftFast(SHIFTOP opcode, byte a) {
		reg[a] = (byte) shiftDo(opcode, a);
		flagZ = false;
		pc += 1;
		return 4;
	}

	private int shift(SHIFTOP opcode) {
		int addr = addr(H, L);
		mem.write(addr, (byte) shiftDo(opcode, mem.read(addr)));
		pc += 1;
		return 16;
	}

	private int shift(SHIFTOP opcode, byte a) {
		reg[a] = (byte) shiftDo(opcode, reg[a]);
		pc += 1;
		return 8;
	}

	private int shiftDo(SHIFTOP opcode, int a) {

		byte bit7 = (byte) (a >> 7), bit0 = (byte) (a & 1);

		switch (opcode) {
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

	private int addHL() {
		int c = (reg[L] += (sp & 0xFF)) > 255 ? 1 : 0;
		int h = reg[H] + (sp >> 8) + c;
		flagH = bool(((reg[H] & 0x0F) + ((sp >> 8) & 0x0F) + c) & 0x10);
		reg[H] = (byte) h;
		flagC = (h > 255);
		flagN = false;
		pc += 1;
		return 8;
	}

	private int addHL(int a, int b) {
		int c = (reg[L] += reg[b]) > 255 ? 1 : 0;
		int h = reg[H] + reg[a] + c;
		flagH = bool(((reg[H] & 0x0F) + (reg[a] & 0x0F) + c) & 0x10);
		reg[H] = (byte) h;
		flagC = (h > 255);
		flagN = false;
		pc += 1;
		return 8;
	}

	private int daa() {
		if (flagN) {
			if (flagC)
				reg[A] -= 0x60;
			if (flagH)
				reg[A] -= 0x06;
		} else {
			if (reg[A] > 0x99 || flagC) {
				reg[A] += 0x60;
				flagC = true;
			}
			if ((reg[A] & 0x0f) > 0x09 || flagH)
				reg[A] += 0x06;
		}
		flagZ = reg[A] == 0;
		flagH = false;
		pc++;
		return 4;
	}

	private int ld_imm_sp() {
		mem.write16(mem.read(pc + 1) + (mem.read(pc + 2) << 8), (byte) (sp >> 8), (byte) (sp & 0xFF));
		pc += 3;
		return 20;
	}

	private int ld_hl_spdd() {
		var b = signedOffset(mem.read(pc + 1));

		flagH = bool(((sp & 0x0F) + (b & 0x0F)) & 0x010);
		flagC = bool(((sp & 0xFF) + (b & 0xFF)) & 0x100);

		var n = sp + b;
		reg[H] = (byte) (n >> 8);
		reg[L] = (byte) (n & 0xFF);

		flagN = false;
		flagZ = false;
		pc += 2;
		return 12;
	}

	private int add_sp_n() {
		var b = signedOffset(mem.read(pc + 1));

		flagH = bool(((sp & 0x0F) + (b & 0x0F)) & 0x010);
		flagC = bool(((sp & 0xFF) + (b & 0xFF)) & 0x100);

		sp += b;
		flagN = false;
		flagZ = false;

		sp &= 0xFFFF;
		pc += 2;
		return 16;
	}

	private int halt() {
		halted = true;
		pc++;
		return 4;
	};

	private int stop() {
		pc += 2;
		return 4;
	}
	
	private int unused() {
		System.err.println("UNUSED");
		System.exit(-1);
		return 4;
	}
	
	private int nop() {
		pc+=1;
		return 4;
	}
}
