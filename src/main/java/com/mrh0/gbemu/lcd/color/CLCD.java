package com.mrh0.gbemu.lcd.color;

import java.awt.Graphics2D;

import com.mrh0.gbemu.Emulator;
import com.mrh0.gbemu.Globals;
import com.mrh0.gbemu.lcd.ILCD;
import com.mrh0.gbemu.memory.Memory;
import com.mrh0.gbemu.ui.game.Renderer;

public class CLCD implements ILCD{

	private final Palette bg;
    private final Palette oam;
    
    private IntQueue pixelQueue;
    private IntQueue paletteQueue;
    private IntQueue priorityQueue;
    
    private byte[] pixels;
	private final int SIZE = 160 * 144;
	
	public CLCD() {
		this.bg = new Palette(0xff68);
        this.oam = new Palette(0xff6a);
        
        this.pixelQueue = new IntQueue(16);
        this.paletteQueue = new IntQueue(16);
        this.priorityQueue = new IntQueue(16);
        
        this.pixels = new byte[SIZE];
	}

	@Override
	public void render(Graphics2D g, int scale, int mode) {
		for (int i = 0; i < SIZE; i++) {
			//g.setColor(getColor(pixels[i]));
			g.fillRect((i % 160) * scale, (i / 160) * scale, scale, scale);
		}
	}
	
	private boolean bool(int b) {
		return b > 0;
	}
	
	//Fifo
	private int getPixel() {
		return getColor(priorityQueue.dequeue(), paletteQueue.dequeue(), pixelQueue.dequeue());
	}
	
	public void putPixels(int[] pixelLine, int att) {
        for (int p : pixelLine) {
        	pixelQueue.enqueue(p);
            paletteQueue.enqueue(getAttPalette(att));
            priorityQueue.enqueue(isAttPriority(att) ? 100 : -1);
        }
    }
	
    private int getColor(int priority, int palette, int color) {
        if (priority >= 0 && priority < 10)
            return oam.getPalette(palette)[color];
        return bg.getPalette(palette)[color];
    }
    
    public static int getColor(int c) {
        int r = (c >> 0) & 0x1f;
        int g = (c >> 5) & 0x1f;
        int b = (c >> 10) & 0x1f;
        return ((r * 8) << 16) | ((g * 8) << 8) | (b * 8);
    }
	
	  // FF41 - STAT - LCDC Status (R/W)
	  // FF42 - SCY - Scroll Y (R/W)
	  // FF43 - SCX - Scroll X (R/W)
	  // FF44 - LY - LCDC Y-Coordinate (R)
	  // FF45 - LYC - LY Compare (R/W)
	  // FF46 - DMA - DMA Transfer and Start Address (W)
	  // FF47 - BGP - BG Palette Data (R/W) - Non CGB Mode Only
	  // FF48 - OBP0 - Object Palette 0 Data (R/W) - Non CGB Mode Only
	  // FF49 - OBP1 - Object Palette 1 Data (R/W) - Non CGB Mode Only
	  // FF4A - WY - Window Y Position (R/W)
	  // FF4B - WX - Window X Position minus 7 (R/W)
	
	  // Complete scan line takes 456 clks.
	
	  //  Mode 0 H-blank period        - 204 clks
	  //  Mode 1 V-blank period        - 4560 clks
	  //  Mode 2 Reading OAM           - 80 clks
	  //  Mode 3 Reading OAM and VRAM  - 172 clks
	  //
	  //  Mode 2  2_____2_____2_____2_____2_____2___________________2____
	  //  Mode 3  _33____33____33____33____33____33__________________3___
	  //  Mode 0  ___000___000___000___000___000___000________________000
	  //  Mode 1  ____________________________________11111111111111_____
	
	@Override
	public int cycle(Emulator emulator, Renderer renderer, int cycles) {
		/*Memory mem = emulator.getMemory();
		Globals globals = emulator.getGlobals();
		
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
				mode = 1; // V-Blank
			else if (draw) {
				int LY = mem.raw()[0xFF44]&0xFF;
				int dpy = LY * 160;

				boolean drawWindow = bool((mem.raw()[0xFF40]&0xFF) & ((1 << 5)&0xFF)) && LY >= (mem.raw()[0xFF4A]&0xFF);
				int bgStopX = drawWindow ? (mem.raw()[0xFF4B]&0xFF) - 7 : 160;


				int baseTileOffset;
				boolean tileSigned;
				// Tile Data Select
				if (bool((mem.raw()[0xFF40]&0xFF) & ((1 << 4)&0xFF))) {
					baseTileOffset = 0x8000;
					tileSigned = false;
				} else {
					baseTileOffset = 0x9000;
					tileSigned = true;
				}
				int[] bgpalette = { ((mem.raw()[0xFF47])&0xFF) & 3, ((mem.raw()[0xFF47]&0xFF) >> 2) & 3, ((mem.raw()[0xFF47]&0xFF) >> 4) & 3,
						((mem.raw()[0xFF47]&0xFF) >> 6) & 3 };

				if (bool((mem.raw()[0xFF40]&0xFF) & 1)) {

					int bgTileMapAddr = bool(mem.raw()[0xFF40] & ((1 << 3)&0xFF)) ? 0x9C00 : 0x9800;


					int x = (mem.raw()[0xFF43]&0xFF) >> 3;
					int xoff = (mem.raw()[0xFF43]&0xFF) & 7;
					int y = ((LY&0xFF) + (mem.raw()[0xFF42]&0xFF)) & 0xFF;

					bgTileMapAddr += (~~(y / 8)) * 32;
					int tileOffset = baseTileOffset + (y & 7) * 2;

					int[] pix = grabTile(mem, mem.raw()[bgTileMapAddr + x]&0xFF, tileOffset, tileSigned);

					for (int i = 0; i < bgStopX; i++) {
						pixels[dpy + i] = (byte) bgpalette[pix[xoff++]];

						if (xoff == 8) {
							x = (x + 1) & 0x1F;

							pix = grabTile(mem, mem.raw()[bgTileMapAddr + x]&0xFF, tileOffset, tileSigned);
							xoff = 0;
						}

					}
				}

				if (drawWindow) {
					// Render Window
					int wdTileMapAddr = bool((mem.raw()[0xFF40]&0xFF) & ((1 << 6)&0xFF)) ? 0x9C00 : 0x9800;

					int xoff = 0;
					int y = LY - mem.raw()[0xFF4A]&0xFF;

					wdTileMapAddr += (~~(y / 8)) * 32;
					int tileOffset = baseTileOffset + (y & 7) * 2;

					int[] pix = grabTile(mem, mem.raw()[wdTileMapAddr]&0xFF, tileOffset, tileSigned);

					for (int i = Math.max(0, bgStopX); i < 160; i++) {
						pixels[dpy + i] = (byte) bgpalette[pix[xoff++]];
						if (xoff == 8) {
							pix = grabTile(mem, mem.raw()[++wdTileMapAddr]&0xFF, tileOffset, tileSigned);
							xoff = 0;
						}
					}

				}

				if (bool((mem.raw()[0xFF40]&0xFF) & 2)) { 
					// Render Sprites
					int height, tileNumMask;
					if (bool((mem.raw()[0xFF40]&0xFF) & ((1 << 2)&0xFF))) {
						height = 16;
						tileNumMask = 0xFE;
					} else {
						height = 8;
						tileNumMask = 0xFF;
					}

					int[] OBP0 = { 0, ((mem.raw()[0xFF48]&0xFF) >> 2) & 3, ((mem.raw()[0xFF48]&0xFF) >> 4) & 3,
							((mem.raw()[0xFF48]&0xFF) >> 6) & 3 };
					int[] OBP1 = { 0, ((mem.raw()[0xFF49]&0xFF) >> 2) & 3, ((mem.raw()[0xFF49]&0xFF) >> 4) & 3,
							((mem.raw()[0xFF49]&0xFF) >> 6) & 3 };
					int background = bgpalette[0];

					for (int i = 0xFE9C; i >= 0xFE00; i -= 4) {
						int ypos = (mem.raw()[i]&0xFF) - 16 + height;
						if (LY >= ypos - height && LY < ypos) {

							int tileNum = 0x8000 + ((mem.raw()[i + 2]&0xFF) & tileNumMask) * 16;
							int xpos = mem.raw()[i + 1]&0xFF;
							int att = mem.raw()[i + 3]&0xFF;

							int[] palette = bool(att & ((1 << 4)&0xFF)) ? OBP1 : OBP0;
							boolean behind = bool(att & ((1 << 7)&0xFF));

							if (bool(att & ((1 << 6)&0xFF))) {
								tileNum += (ypos - LY - 1) * 2;
							} else {
								tileNum += (LY - ypos + height) * 2;
							}
							int d1 = mem.raw()[tileNum]&0xFF;
							int d2 = mem.raw()[tileNum + 1]&0xFF;
							int[] row = pixelDecoder[d1][d2];

							if (bool(att & ((1 << 5)&0xFF))) {
								if (behind) {
									for (int j = 0; j < Math.min(xpos, 8); j++) {
										if ((pixels[dpy + xpos - 1 - j]&0xFF) == background && bool(row[j]))
											pixels[dpy + xpos - 1 - j] = (byte) palette[row[j]];
									}
								} else {
									for (int j = 0; j < Math.min(xpos, 8); j++) {
										if (bool(row[j]))
											pixels[dpy + xpos - (j + 1)] = (byte) palette[row[j]];
									}
								}
							} else {
								if (behind) {
									for (int j = Math.max(8 - xpos, 0); j < 8; j++) {
										if ((pixels[dpy + xpos - 8 + j]&0xFF) == background && bool(row[j]))
											pixels[dpy + xpos - 8 + j] = (byte) palette[row[j]];
									}
								} else {
									for (int j = Math.max(8 - xpos, 0); j < 8; j++) {
										if (bool(row[j]))
											pixels[dpy + xpos - 8 + j] = (byte) palette[row[j]];
									}
								}
							}
						}
					}
				}
			}

			if (coincidence) {
				if (bool((mem.raw()[0xFF41]&0xFF) & 0x40)) {
					mem.raw()[0xFF0F] |= 0x02; // LCD STAT Interrupt flag
					mem.raw()[0xFF41] |= 0x04; // Coincidence flag
				}
			} else
				mem.raw()[0xFF41] &= 0xFB;
			if (globals.LCD_lastmode != mode) { // Mode changed
				if (mode == 0) {
					if (bool((mem.raw()[0xFF41]&0xFF) & 0x08))
						mem.raw()[0xFF0F] |= 0x02;
				} else if (mode == 1) {

					if (bool((mem.raw()[0xFF41]&0xFF) & 0x10))
						mem.raw()[0xFF0F] |= 0x02;

					// V-Blank interrupt
					if (bool((mem.raw()[0xFFFF]&0xFF) & 0x01))
						mem.raw()[0xFF0F] |= 0x01;
						
						renderer.drawFrame();

				} else if (mode == 2) {
					if (bool((mem.raw()[0xFF41]&0xFF) & 0x20))
						mem.raw()[0xFF0F] |= 0x02;
				}

				mem.raw()[0xFF41] &= 0xF8;
				int k = mem.raw()[0xFF41]&0xFF;
				mem.raw()[0xFF41] = (byte) ((k + mode)&0xFF);
				globals.LCD_lastmode = mode;
			}
		}*/
		return cycles;
	}
	
	private boolean getBit(byte n, int k) {
	    return bool((n >> k) & 0x01);
	}
	
	private boolean isTileBGPriority(byte b) {
		return getBit(b, 7);
	}
	
	private boolean isTileOAMPriority(byte b) {
		return !isTileBGPriority(b);
	}
	
	private boolean isTileVFlip(byte b) {
		return getBit(b, 6);
	}
	
	private boolean isTileHFlip(byte b) {
		return getBit(b, 5);
	}
	
	private int getTileBank(byte b) {
		return (b >> 3) & 0x01;
	}
	
	private int getTilePalette(byte b) {
		return b & 0x07;
	}
	
	
	// LCDC
	public boolean isBgAndWindowDisplay(Memory mem) {
        return (mem.raw()[0xFF40] & 0x01) != 0;
    }

    public boolean isObjDisplay(Memory mem) {
        return (mem.raw()[0xFF40] & 0x02) != 0;
    }

    public int getSpriteHeight(Memory mem) {
        return (mem.raw()[0xFF40] & 0x04) == 0 ? 8 : 16;
    }

    public int getBgTileMapDisplay(Memory mem) {
        return (mem.raw()[0xFF40] & 0x08) == 0 ? 0x9800 : 0x9c00;
    }

    public int getBgWindowTileData(Memory mem) {
        return (mem.raw()[0xFF40] & 0x10) == 0 ? 0x9000 : 0x8000;
    }

    public boolean isBgWindowTileDataSigned(Memory mem) {
        return (mem.raw()[0xFF40] & 0x10) == 0;
    }

    public boolean isWindowDisplay(Memory mem) {
        return (mem.raw()[0xFF40] & 0x20) != 0;
    }

    public int getWindowTileMapDisplay(Memory mem) {
        return (mem.raw()[0xFF40] & 0x40) == 0 ? 0x9800 : 0x9c00;
    }

    public boolean isLcdEnabled(Memory mem) {
        return (mem.raw()[0xFF40] & 0x80) != 0;
    }
    
    //Attribute
    public boolean isAttPriority(int att) {
        return (att & (1 << 7)) != 0;
    }

    public boolean isAttYflip(int att) {
        return (att & (1 << 6)) != 0;
    }

    public boolean isAttXflip(int att) {
        return (att & (1 << 5)) != 0;
    }

    /*public GpuRegister getDmgPalette() {
        return (att & (1 << 4)) == 0 ? GpuRegister.OBP0 : GpuRegister.OBP1;
    }*/

    public int getAttBank(int att) {
        return (att & (1 << 3)) == 0 ? 0 : 1;
    }

    public int getAttPalette(int att) {
        return att & 0x07;
    }
    
    
}
