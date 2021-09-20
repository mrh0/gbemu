package com.mrh0.gbemu.lcd.color;

public class Palette {
	private final int indexAddr;
	private final int dataAddr;
	private int[][] palettes = new int[8][4];
	private int index;
	private boolean autoInc;

	public Palette(int offset) {
		this.indexAddr = offset;
		this.dataAddr = offset + 1;
	}

	public void clear() {
		for (int i = 0; i < 8; i++)
			for (int j = 0; j < 4; j++)
				palettes[i][j] = 0x7fff;
	}

	public int[] getPalette(int index) {
		return palettes[index];
	}

	public void write(int address, int value) {
		if (address == indexAddr) {
			index = value & 0x3f;
			autoInc = (value & (1 << 7)) != 0;
		} else if (address == dataAddr) {
			int color = palettes[index / 8][(index % 8) / 2];
			if (index % 2 == 0)
				color = (color & 0xff00) | value;
			else
				color = (color & 0x00ff) | (value << 8);

			palettes[index / 8][(index % 8) / 2] = color;
			if (autoInc)
				index = (index + 1) & 0x3f;
		}
	}

	public int read(int address) {
		if (address == indexAddr)
			return index | (autoInc ? 0x80 : 0x00) | 0x40;
		else if (address == dataAddr) {
			int color = palettes[index / 8][(index % 8) / 2];
			if (index % 2 == 0)
				return color & 0xff;
			return (color >> 8) & 0xff;
		}
			throw new IllegalArgumentException();
	}

}
