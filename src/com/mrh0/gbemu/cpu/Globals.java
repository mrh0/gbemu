package com.mrh0.gbemu.cpu;

public class Globals {
	public int ROMbank = 1;
	public int ROMbankoffset = (ROMbank - 1) * 0x4000;

	public int RAMbank = 0;
	public int RAMbankoffset = RAMbank * 0x2000 - 0xA000;
	public boolean RAMenabled = false;
	public int MBCRamMode = 0; // for MBC1

	public int divPrescaler = 0, timerPrescaler = 0, timerLength = 1;
	public boolean LCD_enabled = false, timerEnable = false;
	public int LCD_lastmode = 1, LCD_scan = 0;
	public byte dpad = (byte) 0xef, buttons = (byte) 0xdf; // 0=pressed
	
	public final int[] timerLengths = { 1024, 16, 64, 256 };
}
