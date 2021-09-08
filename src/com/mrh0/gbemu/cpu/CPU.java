package com.mrh0.gbemu.cpu;

import com.mrh0.gbemu.cpu.memory.MemMap;
import com.mrh0.gbemu.cpu.memory.Memory;
import com.mrh0.gbemu.lcd.LCD;

public class CPU {
	private byte[] reg;
	private Memory mem;
	private LCD lcd;
	private Globals globals;

	int[][][] pixelDecoder;

	public CPU(Memory mem, LCD lcd, Globals globals) {
		reg = new byte[8];
		this.mem = mem;
		this.lcd = lcd;
		this.globals = globals;

		pixelDecoder = new int[256][256][8];
		for (int d1 = 0; d1 < 256; d1++) {
			for (int d2 = 0; d2 < 256; d2++) {
				pixelDecoder[d1][d2][0] = (((d1 & 128) + 2 * (d2 & 128)) >> 7);
				pixelDecoder[d1][d2][1] = ((d1 & 64) + 2 * (d2 & 64)) >> 6;
				pixelDecoder[d1][d2][2] = ((d1 & 32) + 2 * (d2 & 32)) >> 5;
				pixelDecoder[d1][d2][3] = ((d1 & 16) + 2 * (d2 & 16)) >> 4;
				pixelDecoder[d1][d2][4] = ((d1 & 8) + 2 * (d2 & 8)) >> 3;
				pixelDecoder[d1][d2][5] = ((d1 & 4) + 2 * (d2 & 4)) >> 2;
				pixelDecoder[d1][d2][6] = ((d1 & 2) + 2 * (d2 & 2)) >> 1;
				pixelDecoder[d1][d2][7] = ((d1 & 1) + 2 * (d2 & 1));
			}
		}
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

	private enum ALUOP {
		ADD, ADC, SUB, SBC, AND, OR, XOR, CP
	}

	private enum SHIFTOP {
		RLC, RRC, RL, RR, SLA, SRA, SRL
	}

	// Main:

	public void debug() {
		StringBuilder sb = new StringBuilder();
		sb.append("|pc:" + hex8(mem.read(pc)) + "@" + Integer.toHexString(pc) + "|sp:" + Integer.toHexString(sp) + "("
				+ hex8(mem.read(sp + 1)) + "" + hex8(mem.read(sp)) + ")" + "|");
		sb.append("A:" + hex8(reg[A]) + "|");
		sb.append("B:" + hex8(reg[B]) + "|");
		sb.append("C:" + hex8(reg[C]) + "|");
		sb.append("D:" + hex8(reg[D]) + "|");
		sb.append("E:" + hex8(reg[E]) + "|");
		sb.append("H:" + hex8(reg[H]) + "|");
		sb.append("L:" + hex8(reg[L]) + "|");
		sb.append("(");
		sb.append(flagZ ? "Z" : "-");
		sb.append(flagN ? "N" : "-");
		sb.append(flagH ? "H" : "-");
		sb.append(flagC ? "C" : "-");
		sb.append(")");
		System.out.println(sb.toString());
		
		if(pc>256) {
			//System.out.println("Booted");
			//System.exit(0);
		}
	}

	public void reset() {
		globals.init();
		ime = false;
		halted = false;
		pc = 0;
		sp = 0;
	}

	private int[] grabTile(int n, int offset, boolean tileSigned) {
		int tileptr;
		if (tileSigned && n > 127) {
			tileptr = offset + (n - 256) * 16;
		} else {
			tileptr = offset + n * 16;
		}
		int d1 = mem.raw()[tileptr]&0xFF;
		int d2 = mem.raw()[tileptr + 1]&0xFF;
		return pixelDecoder[d1][d2];
	}

	public int advance() {
		int cycles = 4;

		if (!halted)
			cycles = operation(mem.read(pc)&0xFF);

		if ((globals.divPrescaler += cycles) > 255) {
			globals.divPrescaler -= 256;
			mem.incRaw(0xFF04);
		}
		if (globals.timerEnable) {
			globals.timerPrescaler -= cycles;
			while (globals.timerPrescaler < 0) {
				globals.timerPrescaler += globals.timerLength;
				if (mem.incRaw(0xFF05) == 0xFF) {
					mem.writeRaw(0xFF05, (byte) (mem.readRaw(0xFF06)&0xFF));
					// Set interrupt flag here
					mem.writeRaw(0xFF0F, (byte) ((mem.readRaw(0xFF0F)&0xFF) | (1 << 2)));
					halted = false;
				}
			}
		}

		if (globals.LCD_enabled) {
			globals.LCD_scan += cycles;

			int mode = 0;
			boolean coincidence = false;
			boolean draw = false;
			if (globals.LCD_scan <= 80)
				mode = 2;
			else if (globals.LCD_scan <= 252)
				mode = 3;
			else if (globals.LCD_scan < 456) {
				draw = (globals.LCD_lastmode != 0);
				mode = 0;
			} else {
				mode = 2;
				globals.LCD_scan -= 456;
				mem.raw()[0xFF44]++;
				if ((mem.raw()[0xFF44]&0xFF) > 153)
					mem.raw()[0xFF44] = 0;
				coincidence = ((mem.raw()[0xFF44]&0xFF) == (mem.raw()[0xFF45]&0xFF));
			}

			if ((mem.raw()[0xFF44]&0xFF) >= 144)
				mode = 1; // vblank
			else if (draw) {
				// Draw scanline
				int LY = mem.raw()[0xFF44]&0xFF;
				int dpy = LY * 160;

				boolean drawWindow = bool((mem.raw()[0xFF40]&0xFF) & (1 << 5)) && LY >= (mem.raw()[0xFF4A]&0xFF);
				int bgStopX = drawWindow ? (mem.raw()[0xFF4B]&0xFF) - 7 : 160;

				// FF40 - LCDC - LCD Control (R/W)
				//
				// Bit 7 - LCD Display Enable (0=Off, 1=On)
				// Bit 6 - Window Tile Map Display Select (0=9800-9BFF, 1=9C00-9FFF)
				// Bit 5 - Window Display Enable (0=Off, 1=On)
				// Bit 4 - BG & Window Tile Data Select (0=8800-97FF, 1=8000-8FFF)
				// Bit 3 - BG Tile Map Display Select (0=9800-9BFF, 1=9C00-9FFF)
				// Bit 2 - OBJ (Sprite) Size (0=8x8, 1=8x16)
				// Bit 1 - OBJ (Sprite) Display Enable (0=Off, 1=On)
				// Bit 0 - BG Display (for CGB see below) (0=Off, 1=On)

				int baseTileOffset;
				boolean tileSigned;
				// Tile Data Select
				if (bool((mem.raw()[0xFF40]&0xFF) & (1 << 4))) {
					baseTileOffset = 0x8000;
					tileSigned = false;
				} else {
					baseTileOffset = 0x9000;
					tileSigned = true;
				}
				int[] bgpalette = { ((mem.raw()[0xFF47])&0xFF) & 3, ((mem.raw()[0xFF47]&0xFF) >> 2) & 3, ((mem.raw()[0xFF47]&0xFF) >> 4) & 3,
						((mem.raw()[0xFF47]&0xFF) >> 6) & 3 };

				if (bool((mem.raw()[0xFF40]&0xFF) & 1)) { // BG enabled
					// BG Tile map display select
					int bgTileMapAddr = bool(mem.raw()[0xFF40] & (1 << 3)) ? 0x9C00 : 0x9800;

					// scy FF42
					// scx FF43
					// scanline number FF44
					// pixel row = FF44 + FF42
					// tile row = pixel row >> 3
					// 32 bytes per row
					// pixel column = FF43
					// tile column = pixel column >> 3

					int x = (mem.raw()[0xFF43]&0xFF) >> 3;
					int xoff = (mem.raw()[0xFF43]&0xFF) & 7;
					int y = (LY + (mem.raw()[0xFF42]&0xFF)) & 0xFF;

					// Y doesn't change throughout a scanline
					bgTileMapAddr += (~~(y / 8)) * 32;
					int tileOffset = baseTileOffset + (y & 7) * 2;

					int[] pix = grabTile(mem.raw()[bgTileMapAddr + x]&0xFF, tileOffset, tileSigned);

					for (int i = 0; i < bgStopX; i++) {
						lcd.raw()[dpy + i] = (byte) bgpalette[pix[xoff++]];

						if (xoff == 8) {
							x = (x + 1) & 0x1F; // wrap horizontally in tile map

							pix = grabTile(mem.raw()[bgTileMapAddr + x]&0xFF, tileOffset, tileSigned);
							xoff = 0;
						}

					}
				}

				// FF4A - WY
				// FF4B - WX

				if (drawWindow) { // Window display enable
					// Window Tile map display select
					int wdTileMapAddr = bool((mem.raw()[0xFF40]&0xFF) & (1 << 6)) ? 0x9C00 : 0x9800;

					int xoff = 0;
					int y = LY - mem.raw()[0xFF4A]&0xFF;

					wdTileMapAddr += (~~(y / 8)) * 32;
					int tileOffset = baseTileOffset + (y & 7) * 2;

					int[] pix = grabTile(mem.raw()[wdTileMapAddr]&0xFF, tileOffset, tileSigned);

					for (int i = Math.max(0, bgStopX); i < 160; i++) {
						lcd.raw()[dpy + i] = (byte) bgpalette[pix[xoff++]];
						if (xoff == 8) {
							pix = grabTile(mem.raw()[++wdTileMapAddr]&0xFF, tileOffset, tileSigned);
							xoff = 0;
						}
					}

				}

				if (bool((mem.raw()[0xFF40]&0xFF) & 2)) { // Sprite display enabled

					// Render sprites
					int height, tileNumMask;
					if (bool((mem.raw()[0xFF40]&0xFF) & (1 << 2))) {
						height = 16;
						tileNumMask = 0xFE; // in 8x16 mode, lowest bit of tile number is ignored
					} else {
						height = 8;
						tileNumMask = 0xFF;
					}

					int[] OBP0 = { 0, ((mem.raw()[0xFF48]&0xFF) >> 2) & 3, ((mem.raw()[0xFF48]&0xFF) >> 4) & 3,
							((mem.raw()[0xFF48]&0xFF) >> 6) & 3 };
					int[] OBP1 = { 0, ((mem.raw()[0xFF49]&0xFF) >> 2) & 3, ((mem.raw()[0xFF49]&0xFF) >> 4) & 3,
							((mem.raw()[0xFF49]&0xFF) >> 6) & 3 };
					int background = bgpalette[0];

					// OAM 4 bytes per sprite, 40 sprites
					for (int i = 0xFE9C; i >= 0xFE00; i -= 4) {
						int ypos = (mem.raw()[i]&0xFF) - 16 + height;
						if (LY >= ypos - height && LY < ypos) {

							int tileNum = 0x8000 + ((mem.raw()[i + 2]&0xFF) & tileNumMask) * 16;
							int xpos = mem.raw()[i + 1]&0xFF;
							int att = mem.raw()[i + 3]&0xFF;

							// Bit7 OBJ-to-BG Priority (0=OBJ Above BG, 1=OBJ Behind BG color 1-3)
							// (Used for both BG and Window. BG color 0 is always behind OBJ)
							// Bit6 Y flip (0=Normal, 1=Vertically mirrored)
							// Bit5 X flip (0=Normal, 1=Horizontally mirrored)
							// Bit4 Palette number **Non CGB Mode Only** (0=OBP0, 1=OBP1)

							int[] palette = bool(att & (1 << 4)) ? OBP1 : OBP0;
							boolean behind = bool(att & (1 << 7));

							if (bool(att & (1 << 6))) { // Y flip
								tileNum += (ypos - LY - 1) * 2;
							} else {
								tileNum += (LY - ypos + height) * 2;
							}
							int d1 = mem.raw()[tileNum]&0xFF;
							int d2 = mem.raw()[tileNum + 1]&0xFF;
							int[] row = pixelDecoder[d1][d2];

							if (bool(att & (1 << 5))) { // x flip
								if (behind) {
									for (int j = 0; j < Math.min(xpos, 8); j++) {
										if ((lcd.raw()[dpy + xpos - 1 - j]&0xFF) == background && bool(row[j]))
											lcd.raw()[dpy + xpos - 1 - j] = (byte) palette[row[j]];
									}
								} else {
									for (int j = 0; j < Math.min(xpos, 8); j++) {
										if (bool(row[j]))
											lcd.raw()[dpy + xpos - (j + 1)] = (byte) palette[row[j]];
									}
								}
							} else {
								if (behind) {
									for (int j = Math.max(8 - xpos, 0); j < 8; j++) {
										if ((lcd.raw()[dpy + xpos - 8 + j]&0xFF) == background && bool(row[j]))
											lcd.raw()[dpy + xpos - 8 + j] = (byte) palette[row[j]];
									}
								} else {
									for (int j = Math.max(8 - xpos, 0); j < 8; j++) {
										if (bool(row[j]))
											lcd.raw()[dpy + xpos - 8 + j] = (byte) palette[row[j]];
									}
								}
							}
						}
					}
				}
			}

			// 0xFF41 - LCDC Status
			// Bit 6 - LYC=LY Coincidence Interrupt (1=Enable) (Read/Write)
			// Bit 5 - Mode 2 OAM Interrupt (1=Enable) (Read/Write)
			// Bit 4 - Mode 1 V-Blank Interrupt (1=Enable) (Read/Write)
			// Bit 3 - Mode 0 H-Blank Interrupt (1=Enable) (Read/Write)
			// Bit 2 - Coincidence Flag (0:LYC<>LY, 1:LYC=LY) (Read Only)

			if (coincidence) {
				if (bool((mem.raw()[0xFF41]&0xFF) & (1 << 6))) { // coincidence interrupt enabled
					mem.raw()[0xFF0F] |= 1 << 1; // LCD STAT Interrupt flag
					mem.raw()[0xFF41] |= 1 << 2; // coincidence flag
				}
			} else
				mem.raw()[0xFF41] &= 0xFB;// ~(1<<2)
			if (globals.LCD_lastmode != mode) { // Mode change
				if (mode == 0) {
					if (bool((mem.raw()[0xFF41]&0xFF) & (1 << 3)))
						mem.raw()[0xFF0F] |= 1 << 1;
				} else if (mode == 1) {

					// LCD STAT interrupt on v-blank
					if (bool((mem.raw()[0xFF41]&0xFF) & (1 << 4)))
						mem.raw()[0xFF0F] |= 1 << 1;

					// Main V-Blank interrupt
					if (bool((mem.raw()[0xFFFF]&0xFF) & 1))
						mem.raw()[0xFF0F] |= 1 << 0;

					// renderDisplayCanvas(); mrh0: what it do?
					lcd.update();

				} else if (mode == 2) {
					if (bool((mem.raw()[0xFF41]&0xFF) & (1 << 5)))
						mem.raw()[0xFF0F] |= 1 << 1;
				}

				mem.raw()[0xFF41] &= 0xF8;
				mem.raw()[0xFF41] += mode;
				globals.LCD_lastmode = mode;
			}

		}

		if (ime) {
			// if enabled and flag set
			byte i = (byte) ((mem.raw()[0xFF0F]&0xFF) & (mem.raw()[0xFFFF]&0xFF));

			if (bool(i & (1 << 0))) {
				mem.raw()[0xFF0F] &= ~(1 << 0);
				cycles += interrupt(0x40);
			} else if (bool(i & (1 << 1))) {
				mem.raw()[0xFF0F] &= ~(1 << 1);
				cycles += interrupt(0x48);
			} else if (bool(i & (1 << 2))) {
				mem.raw()[0xFF0F] &= ~(1 << 2);
				cycles += interrupt(0x50);
			} else if (bool(i & (1 << 3))) {
				mem.raw()[0xFF0F] &= ~(1 << 3);
				cycles += interrupt(0x58);
			} else if (bool(i & (1 << 4))) {
				mem.raw()[0xFF0F] &= ~(1 << 4);
				cycles += interrupt(0x60);
			}

		} // else cpu_halted=false

		return cycles;
	}

	public int operation(int opcode) {
		switch (opcode & 0xFF) {
		case 0x00:
			return nop();
		case 0x01:
			return ld16_rrI(B, C);
		case 0x02:
			return ldToMem_rrr(B, C, A);
		case 0x03:
			return inc16_rr(B, C);
		case 0x04:
			return inc_r(B);
		case 0x05:
			return dec_r(B);
		case 0x06:
			return ld_rI(B);
		case 0x07:
			return shiftFast_r(SHIFTOP.RLC, A); // rlca
		case 0x08:
			return ld_imm_sp_(); // LD (nn),SP
		case 0x09:
			return addHL_rr(B, C);
		case 0x0A:
			return ldFromMem_rrr(A, B, C);
		case 0x0B:
			return dec16_rr(B, C);
		case 0x0C:
			return inc_r(C);
		case 0x0D:
			return dec_r(C);
		case 0x0E:
			return ld_rI(C);
		case 0x0F:
			return shiftFast_r(SHIFTOP.RRC, A);

		case 0x10:
			return stop_();
		case 0x11:
			return ld16_rrI(D, E);
		case 0x12:
			return ldToMem_rrr(D, E, A);
		case 0x13:
			return inc16_rr(D, E);
		case 0x14:
			return inc_r(D);
		case 0x15:
			return dec_r(D);
		case 0x16:
			return ld_rI(D);
		case 0x17:
			return shiftFast_r(SHIFTOP.RL, A);
		case 0x18:
			return jr_();
		case 0x19:
			return addHL_rr(D, E); // ADD HL, DE
		case 0x1A:
			return ldFromMem_rrr(A, D, E);
		case 0x1B:
			return dec16_rr(D, E);
		case 0x1C:
			return inc_r(E);
		case 0x1D:
			return dec_r(E);
		case 0x1E:
			return ld_rI(E);
		case 0x1F:
			return shiftFast_r(SHIFTOP.RR, A);

		case 0x20:
			return jrNZ_();
		case 0x21:
			return ld16_rrI(H, L);
		case 0x22:
			return ldi_HLA();
		case 0x23:
			return inc16_rr(H, L);
		case 0x24:
			return inc_r(H);
		case 0x25:
			return dec_r(H);
		case 0x26:
			return ld_rI(H);
		case 0x27:
			return daa_();
		case 0x28:
			return jrZ_();
		case 0x29:
			return addHL_rr(H, L);
		case 0x2A:
			return ldi_AHL();
		case 0x2B:
			return dec16_rr(H, L);
		case 0x2C:
			return inc_r(L);
		case 0x2D:
			return dec_r(L);
		case 0x2E:
			return ld_rI(L);
		case 0x2F:
			return cpl_();

		case 0x30:
			return jrNC_();
		case 0x31:
			return ld16_I();
		case 0x32:
			return ldd_HLA();
		case 0x33:
			return inc16_();
		case 0x34:
			return inc_HL();
		case 0x35:
			return dec_HL();
		case 0x36:
			return ldToMem_rrI(H, L);
		case 0x37:
			return scf_();
		case 0x38:
			return jrC_();
		case 0x39:
			return addHL_();
		case 0x3A:
			return ldd_AHL();
		case 0x3B:
			return dec16_();
		case 0x3C:
			return inc_r(A);
		case 0x3D:
			return dec_r(A);
		case 0x3E:
			return ld_rI(A);
		case 0x3F:
			return ccf_();

		case 0x40:
			return ld_rr(B, B);
		case 0x41:
			return ld_rr(B, C);
		case 0x42:
			return ld_rr(B, D);
		case 0x43:
			return ld_rr(B, E);
		case 0x44:
			return ld_rr(B, H);
		case 0x45:
			return ld_rr(B, L);
		case 0x46:
			return ldFromMem_rrr(B, H, L);
		case 0x47:
			return ld_rr(B, A);

		case 0x48:
			return ld_rr(C, B);
		case 0x49:
			return ld_rr(C, C);
		case 0x4A:
			return ld_rr(C, D);
		case 0x4B:
			return ld_rr(C, E);
		case 0x4C:
			return ld_rr(C, H);
		case 0x4D:
			return ld_rr(C, L);
		case 0x4E:
			return ldFromMem_rrr(C, H, L);
		case 0x4F:
			return ld_rr(C, A);

		case 0x50:
			return ld_rr(D, B);
		case 0x51:
			return ld_rr(D, C);
		case 0x52:
			return ld_rr(D, D);
		case 0x53:
			return ld_rr(D, E);
		case 0x54:
			return ld_rr(D, H);
		case 0x55:
			return ld_rr(D, L);
		case 0x56:
			return ldFromMem_rrr(D, H, L);
		case 0x57:
			return ld_rr(D, A);

		case 0x58:
			return ld_rr(E, B);
		case 0x59:
			return ld_rr(E, C);
		case 0x5A:
			return ld_rr(E, D);
		case 0x5B:
			return ld_rr(E, E);
		case 0x5C:
			return ld_rr(E, H);
		case 0x5D:
			return ld_rr(E, L);
		case 0x5E:
			return ldFromMem_rrr(E, H, L);
		case 0x5F:
			return ld_rr(E, A);

		case 0x60:
			return ld_rr(H, B);
		case 0x61:
			return ld_rr(H, C);
		case 0x62:
			return ld_rr(H, D);
		case 0x63:
			return ld_rr(H, E);
		case 0x64:
			return ld_rr(H, H);
		case 0x65:
			return ld_rr(H, L);
		case 0x66:
			return ldFromMem_rrr(H, H, L);
		case 0x67:
			return ld_rr(H, A);

		case 0x68:
			return ld_rr(L, B);
		case 0x69:
			return ld_rr(L, C);
		case 0x6A:
			return ld_rr(L, D);
		case 0x6B:
			return ld_rr(L, E);
		case 0x6C:
			return ld_rr(L, H);
		case 0x6D:
			return ld_rr(L, L);
		case 0x6E:
			return ldFromMem_rrr(L, H, L);
		case 0x6F:
			return ld_rr(L, A);

		case 0x70:
			return ldToMem_rrr(H, L, B);
		case 0x71:
			return ldToMem_rrr(H, L, C);
		case 0x72:
			return ldToMem_rrr(H, L, D);
		case 0x73:
			return ldToMem_rrr(H, L, E);
		case 0x74:
			return ldToMem_rrr(H, L, H);
		case 0x75:
			return ldToMem_rrr(H, L, L);
		case 0x76:
			return halt_();
		case 0x77:
			return ldToMem_rrr(H, L, A);

		case 0x78:
			return ld_rr(A, B);
		case 0x79:
			return ld_rr(A, C);
		case 0x7A:
			return ld_rr(A, D);
		case 0x7B:
			return ld_rr(A, E);
		case 0x7C:
			return ld_rr(A, H);
		case 0x7D:
			return ld_rr(A, L);
		case 0x7E:
			return ldFromMem_rrr(A, H, L);
		case 0x7F:
			return ld_rr(A, A);

		case 0x80:
			return alu_rr(ALUOP.ADD, A, B);
		case 0x81:
			return alu_rr(ALUOP.ADD, A, C);
		case 0x82:
			return alu_rr(ALUOP.ADD, A, D);
		case 0x83:
			return alu_rr(ALUOP.ADD, A, E);
		case 0x84:
			return alu_rr(ALUOP.ADD, A, H);
		case 0x85:
			return alu_rr(ALUOP.ADD, A, L);
		case 0x86:
			return alu_HL(ALUOP.ADD);
		case 0x87:
			return alu_rr(ALUOP.ADD, A, A);

		case 0x88:
			return alu_rr(ALUOP.ADC, A, B);
		case 0x89:
			return alu_rr(ALUOP.ADC, A, C);
		case 0x8A:
			return alu_rr(ALUOP.ADC, A, D);
		case 0x8B:
			return alu_rr(ALUOP.ADC, A, E);
		case 0x8C:
			return alu_rr(ALUOP.ADC, A, H);
		case 0x8D:
			return alu_rr(ALUOP.ADC, A, L);
		case 0x8E:
			return alu_HL(ALUOP.ADC);
		case 0x8F:
			return alu_rr(ALUOP.ADC, A, A);

		case 0x90:
			return alu_rr(ALUOP.SUB, A, B);
		case 0x91:
			return alu_rr(ALUOP.SUB, A, C);
		case 0x92:
			return alu_rr(ALUOP.SUB, A, D);
		case 0x93:
			return alu_rr(ALUOP.SUB, A, E);
		case 0x94:
			return alu_rr(ALUOP.SUB, A, H);
		case 0x95:
			return alu_rr(ALUOP.SUB, A, L);
		case 0x96:
			return alu_HL(ALUOP.SUB);
		case 0x97:
			return alu_rr(ALUOP.SUB, A, A);

		case 0x98:
			return alu_rr(ALUOP.SBC, A, B);
		case 0x99:
			return alu_rr(ALUOP.SBC, A, C);
		case 0x9A:
			return alu_rr(ALUOP.SBC, A, D);
		case 0x9B:
			return alu_rr(ALUOP.SBC, A, E);
		case 0x9C:
			return alu_rr(ALUOP.SBC, A, H);
		case 0x9D:
			return alu_rr(ALUOP.SBC, A, L);
		case 0x9E:
			return alu_HL(ALUOP.SBC);
		case 0x9F:
			return alu_rr(ALUOP.SBC, A, A);

		case 0xA0:
			return alu_rr(ALUOP.AND, A, B);
		case 0xA1:
			return alu_rr(ALUOP.AND, A, C);
		case 0xA2:
			return alu_rr(ALUOP.AND, A, D);
		case 0xA3:
			return alu_rr(ALUOP.AND, A, E);
		case 0xA4:
			return alu_rr(ALUOP.AND, A, H);
		case 0xA5:
			return alu_rr(ALUOP.AND, A, L);
		case 0xA6:
			return alu_HL(ALUOP.AND);
		case 0xA7:
			return alu_rr(ALUOP.AND, A, A);

		case 0xA8:
			return alu_rr(ALUOP.XOR, A, B);
		case 0xA9:
			return alu_rr(ALUOP.XOR, A, C);
		case 0xAA:
			return alu_rr(ALUOP.XOR, A, D);
		case 0xAB:
			return alu_rr(ALUOP.XOR, A, E);
		case 0xAC:
			return alu_rr(ALUOP.XOR, A, H);
		case 0xAD:
			return alu_rr(ALUOP.XOR, A, L);
		case 0xAE:
			return alu_HL(ALUOP.XOR);
		case 0xAF:
			return alu_rr(ALUOP.XOR, A, A);

		case 0xB0:
			return alu_rr(ALUOP.OR, A, B);
		case 0xB1:
			return alu_rr(ALUOP.OR, A, C);
		case 0xB2:
			return alu_rr(ALUOP.OR, A, D);
		case 0xB3:
			return alu_rr(ALUOP.OR, A, E);
		case 0xB4:
			return alu_rr(ALUOP.OR, A, H);
		case 0xB5:
			return alu_rr(ALUOP.OR, A, L);
		case 0xB6:
			return alu_HL(ALUOP.OR);
		case 0xB7:
			return alu_rr(ALUOP.OR, A, A);

		case 0xB8:
			return alu_rr(ALUOP.CP, A, B);
		case 0xB9:
			return alu_rr(ALUOP.CP, A, C);
		case 0xBA:
			return alu_rr(ALUOP.CP, A, D);
		case 0xBB:
			return alu_rr(ALUOP.CP, A, E);
		case 0xBC:
			return alu_rr(ALUOP.CP, A, H);
		case 0xBD:
			return alu_rr(ALUOP.CP, A, L);
		case 0xBE:
			return alu_HL(ALUOP.CP);
		case 0xBF:
			return alu_rr(ALUOP.CP, A, A);

		case 0xC0:
			return retNZ_();
		case 0xC1:
			return pop_rr(B, C);
		case 0xC2:
			return jpNZ_();
		case 0xC3:
			return jp_();
		case 0xC4:
			return callNZ_();
		case 0xC5:
			return push_rr(B, C);
		case 0xC6:
			return alu_I(ALUOP.ADD);
		case 0xC7:
			return rst_v(0x00);
		case 0xC8:
			return retZ_();
		case 0xC9:
			return ret_();
		case 0xCA:
			return jpZ_();
		case 0xCB:
			return CBop(mem.read(++pc)&0xFF);
		case 0xCC:
			return callZ_();
		case 0xCD:
			return call_();
		case 0xCE:
			return alu_I(ALUOP.ADC);
		case 0xCF:
			return rst_v(0x08);

		case 0xD0:
			return retNC_();
		case 0xD1:
			return pop_rr(D, E);
		case 0xD2:
			return jpNC_();
		case 0xD3:
			return unused_();
		case 0xD4:
			return callNC_();
		case 0xD5:
			return push_rr(D, E);
		case 0xD6:
			return alu_I(ALUOP.SUB);
		case 0xD7:
			return rst_v(0x10);
		case 0xD8:
			return retC_();
		case 0xD9:
			return reti_(); // RETI
		case 0xDA:
			return jpC_();
		case 0xDB:
			return unused_();
		case 0xDC:
			return callC_();
		case 0xDD:
			return unused_();
		case 0xDE:
			return alu_I(ALUOP.SBC);
		case 0xDF:
			return rst_v(0x18);

		case 0xE0:
			return ldh_IA(); // LD (FF00+n),A
		case 0xE1:
			return pop_rr(H, L);
		case 0xE2:
			return ldc_CA(); // LD (FF00+C),A
		case 0xE3:
			return unused_();
		case 0xE4:
			return unused_();
		case 0xE5:
			return push_rr(H, L);
		case 0xE6:
			return alu_I(ALUOP.AND);
		case 0xE7:
			return rst_v(0x20);
		case 0xE8:
			return add_sp_n_(); // ADD SP,dd
		case 0xE9:
			return jpHL_();
		case 0xEA:
			return ldToMem_Ir(A); // LD (nn),A
		case 0xEB:
			return unused_();
		case 0xEC:
			return unused_();
		case 0xED:
			return unused_();
		case 0xEE:
			return alu_I(ALUOP.XOR);
		case 0xEF:
			return rst_v(0x28);

		case 0xF0:
			return ldh_AI(); // LD A,(FF00+n)
		case 0xF1:
			return pop_();
		case 0xF2:
			return ldc_AC(); // LD A,(FF00+C)
		case 0xF3:
			return di_();
		case 0xF4:
			return unused_();
		case 0xF5:
			return push_();
		case 0xF6:
			return alu_I(ALUOP.OR);
		case 0xF7:
			return rst_v(0x30);
		case 0xF8:
			return ld_hl_spdd_(); // LD HL,SP+dd
		case 0xF9:
			return ld16_();
		case 0xFA:
			return ldFromMem_rI(A); // LD A,(nn)
		case 0xFB:
			return ei_();
		case 0xFC:
			return unused_();
		case 0xFD:
			return unused_();
		case 0xFE:
			return alu_I(ALUOP.CP);
		case 0xFF:
			return rst_v(0x38);
		}
		System.out.println("Unknown Op: " + hex8(opcode));
		return -1;
	}

	public int CBop(int opcode) {
		switch (opcode) {
		case 0x00:
			return shift_r(SHIFTOP.RLC, B);
		case 0x01:
			return shift_r(SHIFTOP.RLC, C);
		case 0x02:
			return shift_r(SHIFTOP.RLC, D);
		case 0x03:
			return shift_r(SHIFTOP.RLC, E);
		case 0x04:
			return shift_r(SHIFTOP.RLC, H);
		case 0x05:
			return shift_r(SHIFTOP.RLC, L);
		case 0x06:
			return shift_HL(SHIFTOP.RLC);
		case 0x07:
			return shift_r(SHIFTOP.RLC, A);
		case 0x08:
			return shift_r(SHIFTOP.RRC, B);
		case 0x09:
			return shift_r(SHIFTOP.RRC, C);
		case 0x0A:
			return shift_r(SHIFTOP.RRC, D);
		case 0x0B:
			return shift_r(SHIFTOP.RRC, E);
		case 0x0C:
			return shift_r(SHIFTOP.RRC, H);
		case 0x0D:
			return shift_r(SHIFTOP.RRC, L);
		case 0x0E:
			return shift_HL(SHIFTOP.RRC);
		case 0x0F:
			return shift_r(SHIFTOP.RRC, A);

		case 0x10:
			return shift_r(SHIFTOP.RL, B);
		case 0x11:
			return shift_r(SHIFTOP.RL, C);
		case 0x12:
			return shift_r(SHIFTOP.RL, D);
		case 0x13:
			return shift_r(SHIFTOP.RL, E);
		case 0x14:
			return shift_r(SHIFTOP.RL, H);
		case 0x15:
			return shift_r(SHIFTOP.RL, L);
		case 0x16:
			return shift_HL(SHIFTOP.RL);
		case 0x17:
			return shift_r(SHIFTOP.RL, A);
		case 0x18:
			return shift_r(SHIFTOP.RR, B);
		case 0x19:
			return shift_r(SHIFTOP.RR, C);
		case 0x1A:
			return shift_r(SHIFTOP.RR, D);
		case 0x1B:
			return shift_r(SHIFTOP.RR, E);
		case 0x1C:
			return shift_r(SHIFTOP.RR, H);
		case 0x1D:
			return shift_r(SHIFTOP.RR, L);
		case 0x1E:
			return shift_HL(SHIFTOP.RR);
		case 0x1F:
			return shift_r(SHIFTOP.RR, A);

		case 0x20:
			return shift_r(SHIFTOP.SLA, B);
		case 0x21:
			return shift_r(SHIFTOP.SLA, C);
		case 0x22:
			return shift_r(SHIFTOP.SLA, D);
		case 0x23:
			return shift_r(SHIFTOP.SLA, E);
		case 0x24:
			return shift_r(SHIFTOP.SLA, H);
		case 0x25:
			return shift_r(SHIFTOP.SLA, L);
		case 0x26:
			return shift_HL(SHIFTOP.SLA);
		case 0x27:
			return shift_r(SHIFTOP.SLA, A);
		case 0x28:
			return shift_r(SHIFTOP.SRA, B);
		case 0x29:
			return shift_r(SHIFTOP.SRA, C);
		case 0x2A:
			return shift_r(SHIFTOP.SRA, D);
		case 0x2B:
			return shift_r(SHIFTOP.SRA, E);
		case 0x2C:
			return shift_r(SHIFTOP.SRA, H);
		case 0x2D:
			return shift_r(SHIFTOP.SRA, L);
		case 0x2E:
			return shift_HL(SHIFTOP.SRA);
		case 0x2F:
			return shift_r(SHIFTOP.SRA, A);

		case 0x38:
			return shift_r(SHIFTOP.SRL, B);
		case 0x39:
			return shift_r(SHIFTOP.SRL, C);
		case 0x3A:
			return shift_r(SHIFTOP.SRL, D);
		case 0x3B:
			return shift_r(SHIFTOP.SRL, E);
		case 0x3C:
			return shift_r(SHIFTOP.SRL, H);
		case 0x3D:
			return shift_r(SHIFTOP.SRL, L);
		case 0x3E:
			return shift_HL(SHIFTOP.SRL);
		case 0x3F:
			return shift_r(SHIFTOP.SRL, A);

		case 0x30:
			return swap_r(B);
		case 0x31:
			return swap_r(C);
		case 0x32:
			return swap_r(D);
		case 0x33:
			return swap_r(E);
		case 0x34:
			return swap_r(H);
		case 0x35:
			return swap_r(L);
		case 0x36:
			return swap_();
		case 0x37:
			return swap_r(A);

		case 0x40:
			return bit_vr(0, 0);
		case 0x80:
			return res_vr(0, 0);
		case 0xC0:
			return set_vr(0, 0);
		case 0x41:
			return bit_vr(0, 1);
		case 0x81:
			return res_vr(0, 1);
		case 0xC1:
			return set_vr(0, 1);
		case 0x42:
			return bit_vr(0, 2);
		case 0x82:
			return res_vr(0, 2);
		case 0xC2:
			return set_vr(0, 2);
		case 0x43:
			return bit_vr(0, 3);
		case 0x83:
			return res_vr(0, 3);
		case 0xC3:
			return set_vr(0, 3);
		case 0x44:
			return bit_vr(0, 4);
		case 0x84:
			return res_vr(0, 4);
		case 0xC4:
			return set_vr(0, 4);
		case 0x45:
			return bit_vr(0, 5);
		case 0x85:
			return res_vr(0, 5);
		case 0xC5:
			return set_vr(0, 5);
		case 0x46:
			return bit_vHL(0, 6);
		case 0x86:
			return res_vHL(0, 6);
		case 0xC6:
			return set_vHL(0, 6);
		case 0x47:
			return bit_vr(0, 7);
		case 0x87:
			return res_vr(0, 7);
		case 0xC7:
			return set_vr(0, 7);
		case 0x48:
			return bit_vr(1, 0);
		case 0x88:
			return res_vr(1, 0);
		case 0xC8:
			return set_vr(1, 0);
		case 0x49:
			return bit_vr(1, 1);
		case 0x89:
			return res_vr(1, 1);
		case 0xC9:
			return set_vr(1, 1);
		case 0x4A:
			return bit_vr(1, 2);
		case 0x8A:
			return res_vr(1, 2);
		case 0xCA:
			return set_vr(1, 2);
		case 0x4B:
			return bit_vr(1, 3);
		case 0x8B:
			return res_vr(1, 3);
		case 0xCB:
			return set_vr(1, 3);
		case 0x4C:
			return bit_vr(1, 4);
		case 0x8C:
			return res_vr(1, 4);
		case 0xCC:
			return set_vr(1, 4);
		case 0x4D:
			return bit_vr(1, 5);
		case 0x8D:
			return res_vr(1, 5);
		case 0xCD:
			return set_vr(1, 5);
		case 0x4E:
			return bit_vHL(1, 6);
		case 0x8E:
			return res_vHL(1, 6);
		case 0xCE:
			return set_vHL(1, 6);
		case 0x4F:
			return bit_vr(1, 7);
		case 0x8F:
			return res_vr(1, 7);
		case 0xCF:
			return set_vr(1, 7);
		case 0x50:
			return bit_vr(2, 0);
		case 0x90:
			return res_vr(2, 0);
		case 0xD0:
			return set_vr(2, 0);
		case 0x51:
			return bit_vr(2, 1);
		case 0x91:
			return res_vr(2, 1);
		case 0xD1:
			return set_vr(2, 1);
		case 0x52:
			return bit_vr(2, 2);
		case 0x92:
			return res_vr(2, 2);
		case 0xD2:
			return set_vr(2, 2);
		case 0x53:
			return bit_vr(2, 3);
		case 0x93:
			return res_vr(2, 3);
		case 0xD3:
			return set_vr(2, 3);
		case 0x54:
			return bit_vr(2, 4);
		case 0x94:
			return res_vr(2, 4);
		case 0xD4:
			return set_vr(2, 4);
		case 0x55:
			return bit_vr(2, 5);
		case 0x95:
			return res_vr(2, 5);
		case 0xD5:
			return set_vr(2, 5);
		case 0x56:
			return bit_vHL(2, 6);
		case 0x96:
			return res_vHL(2, 6);
		case 0xD6:
			return set_vHL(2, 6);
		case 0x57:
			return bit_vr(2, 7);
		case 0x97:
			return res_vr(2, 7);
		case 0xD7:
			return set_vr(2, 7);
		case 0x58:
			return bit_vr(3, 0);
		case 0x98:
			return res_vr(3, 0);
		case 0xD8:
			return set_vr(3, 0);
		case 0x59:
			return bit_vr(3, 1);
		case 0x99:
			return res_vr(3, 1);
		case 0xD9:
			return set_vr(3, 1);
		case 0x5A:
			return bit_vr(3, 2);
		case 0x9A:
			return res_vr(3, 2);
		case 0xDA:
			return set_vr(3, 2);
		case 0x5B:
			return bit_vr(3, 3);
		case 0x9B:
			return res_vr(3, 3);
		case 0xDB:
			return set_vr(3, 3);
		case 0x5C:
			return bit_vr(3, 4);
		case 0x9C:
			return res_vr(3, 4);
		case 0xDC:
			return set_vr(3, 4);
		case 0x5D:
			return bit_vr(3, 5);
		case 0x9D:
			return res_vr(3, 5);
		case 0xDD:
			return set_vr(3, 5);
		case 0x5E:
			return bit_vHL(3, 6);
		case 0x9E:
			return res_vHL(3, 6);
		case 0xDE:
			return set_vHL(3, 6);
		case 0x5F:
			return bit_vr(3, 7);
		case 0x9F:
			return res_vr(3, 7);
		case 0xDF:
			return set_vr(3, 7);
		case 0x60:
			return bit_vr(4, 0);
		case 0xA0:
			return res_vr(4, 0);
		case 0xE0:
			return set_vr(4, 0);
		case 0x61:
			return bit_vr(4, 1);
		case 0xA1:
			return res_vr(4, 1);
		case 0xE1:
			return set_vr(4, 1);
		case 0x62:
			return bit_vr(4, 2);
		case 0xA2:
			return res_vr(4, 2);
		case 0xE2:
			return set_vr(4, 2);
		case 0x63:
			return bit_vr(4, 3);
		case 0xA3:
			return res_vr(4, 3);
		case 0xE3:
			return set_vr(4, 3);
		case 0x64:
			return bit_vr(4, 4);
		case 0xA4:
			return res_vr(4, 4);
		case 0xE4:
			return set_vr(4, 4);
		case 0x65:
			return bit_vr(4, 5);
		case 0xA5:
			return res_vr(4, 5);
		case 0xE5:
			return set_vr(4, 5);
		case 0x66:
			return bit_vHL(4, 6);
		case 0xA6:
			return res_vHL(4, 6);
		case 0xE6:
			return set_vHL(4, 6);
		case 0x67:
			return bit_vr(4, 7);
		case 0xA7:
			return res_vr(4, 7);
		case 0xE7:
			return set_vr(4, 7);
		case 0x68:
			return bit_vr(5, 0);
		case 0xA8:
			return res_vr(5, 0);
		case 0xE8:
			return set_vr(5, 0);
		case 0x69:
			return bit_vr(5, 1);
		case 0xA9:
			return res_vr(5, 1);
		case 0xE9:
			return set_vr(5, 1);
		case 0x6A:
			return bit_vr(5, 2);
		case 0xAA:
			return res_vr(5, 2);
		case 0xEA:
			return set_vr(5, 2);
		case 0x6B:
			return bit_vr(5, 3);
		case 0xAB:
			return res_vr(5, 3);
		case 0xEB:
			return set_vr(5, 3);
		case 0x6C:
			return bit_vr(5, 4);
		case 0xAC:
			return res_vr(5, 4);
		case 0xEC:
			return set_vr(5, 4);
		case 0x6D:
			return bit_vr(5, 5);
		case 0xAD:
			return res_vr(5, 5);
		case 0xED:
			return set_vr(5, 5);
		case 0x6E:
			return bit_vHL(5, 6);
		case 0xAE:
			return res_vHL(5, 6);
		case 0xEE:
			return set_vHL(5, 6);
		case 0x6F:
			return bit_vr(5, 7);
		case 0xAF:
			return res_vr(5, 7);
		case 0xEF:
			return set_vr(5, 7);
		case 0x70:
			return bit_vr(6, 0);
		case 0xB0:
			return res_vr(6, 0);
		case 0xF0:
			return set_vr(6, 0);
		case 0x71:
			return bit_vr(6, 1);
		case 0xB1:
			return res_vr(6, 1);
		case 0xF1:
			return set_vr(6, 1);
		case 0x72:
			return bit_vr(6, 2);
		case 0xB2:
			return res_vr(6, 2);
		case 0xF2:
			return set_vr(6, 2);
		case 0x73:
			return bit_vr(6, 3);
		case 0xB3:
			return res_vr(6, 3);
		case 0xF3:
			return set_vr(6, 3);
		case 0x74:
			return bit_vr(6, 4);
		case 0xB4:
			return res_vr(6, 4);
		case 0xF4:
			return set_vr(6, 4);
		case 0x75:
			return bit_vr(6, 5);
		case 0xB5:
			return res_vr(6, 5);
		case 0xF5:
			return set_vr(6, 5);
		case 0x76:
			return bit_vHL(6, 6);
		case 0xB6:
			return res_vHL(6, 6);
		case 0xF6:
			return set_vHL(6, 6);
		case 0x77:
			return bit_vr(6, 7);
		case 0xB7:
			return res_vr(6, 7);
		case 0xF7:
			return set_vr(6, 7);
		case 0x78:
			return bit_vr(7, 0);
		case 0xB8:
			return res_vr(7, 0);
		case 0xF8:
			return set_vr(7, 0);
		case 0x79:
			return bit_vr(7, 1);
		case 0xB9:
			return res_vr(7, 1);
		case 0xF9:
			return set_vr(7, 1);
		case 0x7A:
			return bit_vr(7, 2);
		case 0xBA:
			return res_vr(7, 2);
		case 0xFA:
			return set_vr(7, 2);
		case 0x7B:
			return bit_vr(7, 3);
		case 0xBB:
			return res_vr(7, 3);
		case 0xFB:
			return set_vr(7, 3);
		case 0x7C:
			return bit_vr(7, 4);
		case 0xBC:
			return res_vr(7, 4);
		case 0xFC:
			return set_vr(7, 4);
		case 0x7D:
			return bit_vr(7, 5);
		case 0xBD:
			return res_vr(7, 5);
		case 0xFD:
			return set_vr(7, 5);
		case 0x7E:
			return bit_vHL(7, 6);
		case 0xBE:
			return res_vHL(7, 6);
		case 0xFE:
			return set_vHL(7, 6);
		case 0x7F:
			return bit_vr(7, 7);
		case 0xBF:
			return res_vr(7, 7);
		case 0xFF:
			return set_vr(7, 7);
		}
		System.err.println("Unknown CB: " + hex8(opcode));
		return -1;
	}

	// Helpers:
	private int addr(int a, int b) {
		System.out.println("ADDR " + hex8(reg[a]) + ":" + hex8(reg[b]) + "=" + ((((reg[a]&0xFF) << 8) | (reg[b]&0xFF))&0xFFFF));
		return (((reg[a]&0xFF) << 8) | (reg[b]&0xFF))&0xFFFF;
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

	private String hex8(int h) {
		return ((h & 0xFF) < 0x10 ? "0" : "") + Integer.toHexString(h & 0xFF).toUpperCase();
	}

	// ALU:

	private int alu_I(ALUOP opcode) {
		reg[A] = ALUdo(opcode, mem.read(pc + 1)&0xFF);
		pc += 2;
		return 8;
	}

	private int alu_HL(ALUOP opcode) {
		reg[A] = ALUdo(opcode, mem.read(addr(H, L))&0xFF);
		pc += 1;
		return 8;
	}

	private int alu_rr(ALUOP opcode, int a, int b) {
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
	private int ld_rI(int a) {
		reg[a] = (byte) (mem.read(pc + 1)&0xFF);
		pc += 2;
		return 8;
	}

	private int ld_rr(int a, int b) {
		reg[a] = reg[b];
		pc += 1;
		return 4;
	}

	private int ldFromMem_rI(int a) {
		reg[a] = mem.read(mem.read(pc + 1)&0xFF + ((mem.read(pc + 2)&0xFF) << 8));
		pc += 3;
		return 16;
	}

	private int ldFromMem_rrr(int a, int b, int c) {
		reg[a] = (byte) (mem.read(addr(b, c)&0xFFFF)&0xFF);
		pc += 1;
		return 8;
	}

	private int ldToMem_Ir(int b) {
		mem.write(mem.read(pc + 1)&0xFF + ((mem.read(pc + 2)&0xFF) << 8), reg[b]);
		pc += 3;
		return 16;
	}

	private int ldToMem_rrI(int a, int b) {
		mem.write(addr(a, b)&0xFFFF, (byte) (mem.read(pc + 1)&0xFF));
		pc += 2;
		return 12;
	}

	private int ldToMem_rrr(int a, int b, int c) {
		mem.write(addr(a, b)&0xFFFF, reg[c]);
		pc += 1;
		return 8;
	}

	private int ld16_HLI() { // TODO: Where????
		int addr = (mem.read(pc + 1)&0xFF + ((mem.read(pc + 2)&0xFF) << 8))&0xFFFF;
		reg[H] = mem.read(addr + 1);
		reg[L] = mem.read(addr);
		pc += 3;
		return 12;
	}

	private int ld16_I() {
		sp = (mem.read(pc + 1)&0xFF + ((mem.read(pc + 2)&0xFF) << 8))&0xFFFF;
		pc += 3;
		return 12;
	}

	private int ld16_rrI(int a, int b) {
		reg[a] = (byte) (mem.read(pc + 2)&0xFF);
		reg[b] = (byte) (mem.read(pc + 1)&0xFF);
		pc += 3;
		return 12;
	}

	private int ld16_() {
		sp = addr(H, L)&0xFFFF;
		pc += 1;
		return 8;
	}

	private int ldd_HLA() {
		mem.write(addr(H, L), reg[A]);
		if (reg[L] == 0)
			reg[H]--;
		reg[L]--;

		pc += 1;
		return 8;
	}

	private int ldd_AHL() {
		reg[A] = (byte) (mem.read(addr(H, L)&0xFFFF)&0xFF);

		if (reg[L] == 0)
			reg[H]--;
		reg[L]--;

		pc++;
		return 8;
	}

	private int ldi_HLA() {
		mem.write(addr(H, L)&0xFFFF, reg[A]);

		if (reg[L] == 255)
			reg[H]++;
		reg[L]++;

		pc += 1;
		return 8;
	}

	private int ldi_AHL() {
		reg[A] = (byte) (mem.read(addr(H, L)&0xFFFF)&0xFF);

		if (reg[L] == 255)
			reg[H]++;
		reg[L]++;

		pc += 1;
		return 8;
	}

	private int ldc_AC() {
		reg[A] = (byte) (mem.read(MemMap.IO.start + reg[C])&0xFF);
		pc += 1;
		return 8;
	}

	private int ldc_CA() {
		mem.write(MemMap.IO.start + reg[C], reg[A]);
		pc += 1;
		return 8;
	}

	private int ldh_AI() {
		reg[A] = (byte) (mem.read(MemMap.IO.start + (mem.read(pc + 1)&0xFF))&0xFF);
		pc += 2;
		return 12;
	}

	private int ldh_IA() {
		mem.write(MemMap.IO.start + (mem.read(pc + 1)&0xFF), reg[A]);
		pc += 2;
		return 12;
	}

	private int inc_HL() {
		mem.write(addr(H, L), offset((byte) (mem.read(addr(H, L)&0xFFFF)&0xFF), 1));
		pc += 1;
		return 12;
	}

	private int dec_HL() {
		mem.write(addr(H, L), offset((byte) (mem.read(addr(H, L)&0xFFFF)&0xFF), -1));
		pc += 1;
		return 12;
	}

	private int inc_r(int a) {
		reg[a] = offset(reg[a], 1);
		pc += 1;
		return 4;
	}

	private int dec_r(int a) {
		reg[a] = offset(reg[a], -1);
		pc += 1;
		return 4;
	}

	private byte offset(byte a, int offset) {
		int res = (a + offset);
		flagH = (((a & 0x0F) + offset) & 0x10) > 0;
		flagN = offset == -1;
		flagZ = ((res & 0xff) == 0);
		return (byte) res;
	}

	private int inc16_() {
		sp++;
		pc++;
		return 8;
	}

	private int inc16_rr(int a, int b) {
		if (reg[b] == 255)
			reg[a]++;
		reg[b]++;
		pc++;
		return 8;
	}

	private int dec16_() {
		sp--;
		pc++;
		return 8;
	}

	private int dec16_rr(int a, int b) {
		if (reg[b] == 0)
			reg[a]--;
		reg[b]--;
		pc++;
		return 8;
	}

	private int signedOffset(int a) {
		return (a > 127) ? (a - 256) : a;
	}

	private int jrNZ_() {
		if (flagZ) {
			pc += 2;
			return 8;
		}
		pc += 2 + signedOffset(mem.read(pc + 1)&0xFF);
		return 12;
	}

	private int jrNC_() {
		if (flagC) {
			pc += 2;
			return 8;
		}
		pc += 2 + signedOffset(mem.read(pc + 1)&0xFF);
		return 12;
	}

	private int jrZ_() {
		if (!flagZ) {
			pc += 2;
			return 8;
		}
		pc += 2 + signedOffset(mem.read(pc + 1)&0xFF);
		return 12;
	}

	private int jrC_() {
		if (!flagC) {
			pc += 2;
			return 8;
		}
		pc += 2 + signedOffset(mem.read(pc + 1)&0xFF);
		return 12;
	}

	private int jr_() { // unconditional relative
		pc += 2 + signedOffset(mem.read(pc + 1)&0xFF);
		return 12;
	}

	private int jp_() { // unconditional absolute
		pc = mem.read(pc + 1) + ((mem.read(pc + 2)&0xFF) << 8);
		return 16;
	}

	private int jpNZ_() {
		if (flagZ) {
			pc += 3;
			return 12;
		}
		pc = mem.read(pc + 1)&0xFF + ((mem.read(pc + 2)&0xFF) << 8);
		return 16;
	}

	private int jpNC_() {
		if (flagC) {
			pc += 3;
			return 12;
		}
		pc = mem.read(pc + 1)&0xFF + ((mem.read(pc + 2)&0xFF) << 8);
		return 16;
	}

	private int jpZ_() {
		if (!flagZ) {
			pc += 3;
			return 12;
		}
		pc = mem.read(pc + 1)&0xFF + ((mem.read(pc + 2)&0xFF) << 8);
		return 16;
	}

	private int jpC_() {
		if (!flagC) {
			pc += 3;
			return 12;
		}
		pc = mem.read(pc + 1)&0xFF + ((mem.read(pc + 2)&0xFF) << 8);
		return 16;
	}

	private int jpHL_() {
		pc = addr(H, L);
		return 4;
	}

	private int push_() {
		byte flags = (byte) ((num(flagZ) << 7) + (num(flagN) << 6) + (num(flagH) << 5) + (num(flagC) << 4));
		sp -= 2;
		mem.write16(sp, reg[A], flags);
		pc += 1;
		return 16;
	}

	private int push_rr(int a, int b) {
		sp -= 2;
		mem.write16(sp, reg[a], reg[b]);
		pc += 1;
		System.out.println("PUSH " + hex8(reg[a]) + hex8(reg[b]) + ":" + Integer.toHexString(sp));
		return 16;
	}

	private int pop_() {
		reg[A] = (byte) (mem.read(sp + 1)&0xFF); // 1st of read16
		byte s = (byte) (mem.read(sp)&0xFF); // 2and of read16
		flagZ = (s & (1 << 7)) != 0;
		flagN = (s & (1 << 6)) != 0;
		flagH = (s & (1 << 5)) != 0;
		flagC = (s & (1 << 4)) != 0;
		sp += 2;
		pc += 1;
		return 12;
	}

	private int pop_rr(int a, int b) {
		reg[a] = (byte) (mem.read(sp + 1)&0xFF);
		reg[b] = (byte) (mem.read(sp)&0xFF);
		
		System.out.println("POP " + hex8(reg[a]) + hex8(reg[b]) + ":" + Integer.toHexString(sp));
		
		sp += 2;
		pc += 1;
		
		return 12;
	}

	private int call_() {
		sp -= 2;
		int npc = pc + 3;
		mem.write16(sp, (byte) (npc >> 8), (byte) (npc & 0xFF));
		// System.out.println(Integer.toHexString(mem.read(pc + 1)) + "|" +
		// Integer.toHexString(mem.read(pc + 2)) + "|" + Integer.toHexString(mem.read(pc
		// + 2)<<8));
		pc = ((mem.read(pc + 1) & 0xFF) + ((mem.read(pc + 2)&0xFF) << 8));

		return 24;
	}

	private int callNZ_() {
		if (flagZ) {
			pc += 3;
			return 12;
		}
		return call_();
	}

	private int callNC_() {
		if (flagC) {
			pc += 3;
			return 12;
		}
		return call_();
	}

	private int callZ_() {
		if (!flagZ) {
			pc += 3;
			return 12;
		}
		return call_();
	}

	private int callC_() {
		if (!flagC) {
			pc += 3;
			return 12;
		}
		return call_();
	}

	private int ret_() {
		pc = (((mem.read(sp + 1)&0xFF) << 8) + (mem.read(sp)&0xFF))&0xFFFF;
		System.out.println("RET " + hex8(mem.read(sp + 1)) + ":" + hex8(mem.read(sp)) + ":" + Integer.toHexString(pc));
		sp += 2;
		return 16;
	}

	private int retNZ_() {
		if (flagZ) {
			pc++;
			return 8;
		}
		ret_();
		return 20;
	}

	private int retNC_() {
		if (flagC) {
			pc++;
			return 8;
		}
		ret_();
		return 20;
	}

	private int retZ_() {
		if (!flagZ) {
			pc++;
			return 8;
		}
		ret_();
		return 20;
	}

	private int retC_() {
		if (!flagC) {
			pc++;
			return 8;
		}
		ret_();
		return 20;
	}

	private int reti_() {
		ime = true;
		return ret_();
	}

	private int ei_() {
		// This needs to wait until the end of the next instruction
		ime = true;
		pc += 1;
		return 4;
	}

	private int di_() {
		ime = false;
		pc++;
		return 4;
	}

	private int rst_v(int a) {
		sp -= 2;
		int npc = pc + 1;
		mem.write16(sp, (byte) (npc >> 8), (byte) (npc & 0xFF));
		pc = a & 0xFFFF;
		return 16;
	}

	private int shiftFast_r(SHIFTOP opcode, int r) {
		reg[r] = (byte) shiftDo(opcode, reg[r]&0xFF);
		flagZ = false;
		pc += 1;
		return 4;
	}

	private int shift_HL(SHIFTOP opcode) {
		int addr = addr(H, L);
		mem.write(addr, (byte) shiftDo(opcode, mem.read(addr)&0xFF));
		pc += 1;
		return 16;
	}

	private int shift_r(SHIFTOP opcode, int r) {
		reg[r] = (byte) shiftDo(opcode, reg[r]&0xFF);
		pc += 1;
		return 8;
	}

	private int shiftDo(SHIFTOP opcode, int v) {
		int bit7 = ((v&0xFF) >> 7);
		int bit0 = (v & 1);

		switch (opcode) {
		case RLC: // Rotate byte left, save carry
			v = (((v&0xFF) << 1) & 0xff) + bit7;
			flagC = bool(bit7);
			break;
		case RRC: // Rotate byte right, save carry
			v = (((v&0xFF) >> 1) & 0xff) + (bit0 << 7);
			flagC = bool(bit0);
			break;
		case RL: // Rotate left through carry
			//System.out.println("SHIFT RL1 " + v);
			v = (((v&0xFF) << 1) & 0xff) + (num(flagC));
			//System.out.println("SHIFT RL2 " + bit7 + ":" + bit0 + "|" + v + ":" + bool(bit7));
			flagC = bool(bit7);
			break;
		case RR: // Rotate right through carry
			v = (((v&0xFF) >> 1) & 0xff) + ((num(flagC)&0xFF) << 7);
			flagC = bool(bit0);
			break;
		case SLA: // Shift left
			v = (((v&0xFF) << 1) & 0xff);
			flagC = bool(bit7);
			break;
		case SRA: // Shift right arithmetic
			v = (((v&0xFF) >> 1) & 0xff) + (bit7 << 7);
			flagC = bool(bit0);
			break;
		case SRL: // Shift right logical
			v = (((v&0xFF) >> 1) & 0xff);
			flagC = bool(bit0);
			break;
		}

		flagN = false;
		flagH = false;
		flagZ = (v & 0xFF) == 0;
		
		return v&0xFF;
	}

	private int ccf_() {
		flagN = false;
		flagH = false;
		flagC = !flagC;
		pc += 1;
		return 4;
	}

	private int scf_() {
		flagN = false;
		flagH = false;
		flagC = true;
		pc += 1;
		return 4;
	}

	private int cpl_() {
		reg[A] = (byte) ~reg[A];
		flagN = true;
		flagH = true;
		pc += 1;
		return 4;
	}

	private int addHL_() {
		int c = (reg[L] += (sp & 0xFF)) > 255 ? 1 : 0;
		int h = reg[H] + (sp >> 8) + c;
		flagH = bool(((reg[H] & 0x0F) + ((sp >> 8) & 0x0F) + c) & 0x10);
		reg[H] = (byte) h;
		flagC = (h > 255);
		flagN = false;
		pc += 1;
		return 8;
	}

	private int addHL_rr(int a, int b) {
		int c = (reg[L] += reg[b]) > 255 ? 1 : 0;
		int h = reg[H] + reg[a] + c;
		flagH = bool(((reg[H] & 0x0F) + (reg[a] & 0x0F) + c) & 0x10);
		reg[H] = (byte) h;
		flagC = (h > 255);
		flagN = false;
		pc += 1;
		return 8;
	}

	private int daa_() {
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

	private int ld_imm_sp_() {
		mem.write16((mem.read(pc + 1)&0xFF) + ((mem.read(pc + 2)&0xFF) << 8), (byte) (sp >> 8), (byte) (sp & 0xFF));
		pc += 3;
		return 20;
	}

	private int ld_hl_spdd_() {
		int b = signedOffset(mem.read(pc + 1)&0xFF);

		flagH = bool(((sp & 0x0F) + (b & 0x0F)) & 0x010);
		flagC = bool(((sp & 0xFF) + (b & 0xFF)) & 0x100);

		int n = sp + b;
		reg[H] = (byte) (n >> 8);
		reg[L] = (byte) (n & 0xFF);

		flagN = false;
		flagZ = false;
		pc += 2;
		return 12;
	}

	private int add_sp_n_() {
		int b = signedOffset(mem.read(pc + 1)&0xFF);

		flagH = bool(((sp & 0x0F) + (b & 0x0F)) & 0x010);
		flagC = bool(((sp & 0xFF) + (b & 0xFF)) & 0x100);

		sp += b;
		flagN = false;
		flagZ = false;

		sp &= 0xFFFF;
		pc += 2;
		return 16;
	}

	private int halt_() {
		halted = true;
		pc++;
		return 4;
	};

	private int stop_() {
		pc += 2;
		return 4;
	}

	private int unused_() {
		System.err.println("UNUSED");
		System.exit(-1);
		return 4;
	}

	private int nop() {
		pc += 1;
		return 4;
	}

	private int swap_() {
		byte a = (byte) (mem.read(addr(H, L)&0xFFFF)&0xFF);
		a = (byte) ((a >> 4) + ((a << 4) & 0xFF));
		mem.write(addr(H, L), a);
		flagZ = (a == 0);
		flagN = false;
		flagH = false;
		flagC = false;
		pc++;
		return 16;
	}

	private int swap_r(int r) {
		reg[r] = (byte) ((reg[r] >> 4) + ((reg[r] << 4) & 0xFF));
		flagZ = (reg[r] == 0);
		flagN = false;
		flagH = false;
		flagC = false;
		pc++;
		return 8;
	}

	private int bit_vHL(int b, int hl) {
		b = (1 << b);
		flagZ = (((mem.read(addr(H, L)&0xFFFF) & b)&0xFF) == 0);
		flagH = true;
		flagN = false;
		pc++;
		return 12;
	}

	private int bit_vr(int b, int r) {
		b = (1 << b);
		flagZ = ((reg[r] & b) == 0);
		flagH = true;
		flagN = false;
		pc++;
		return 8;
	}

	private int set_vHL(int b, int hl) {
		b = (1 << b);
		mem.write(addr(H, L)&0xFFFF, (byte) ((mem.read(addr(H, L)&0xFFFF)&0xFF) | b));
		pc++;
		return 16;
	}

	private int set_vr(int b, int r) {
		b = (1 << b);
		reg[r] |= b;
		pc++;
		return 8;
	}

	private int res_vHL(int b, int hl) {
		mem.write(addr(H, L)&0xFFFF, (byte) ((mem.read(addr(H, L)&0xFFFF)&0xFF) & b));
		pc++;
		return 16;
	}

	private int res_vr(int b, int r) {
		reg[r] &= b;
		pc++;
		return 8;
	}

	// Interrupt:

	private int interrupt(int npc) {
		halted = false;
		mem.write16(sp -= 2, (byte) (pc >> 8), (byte) (pc & 0xFF));
		pc = npc;
		ime = false;

		return 20;
	}
}
