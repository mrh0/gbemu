package com.mrh0.gbemu;

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

	public byte[] FirstROMPage;

	public final int[] timerLengths = { 1024, 16, 64, 256 };
	
	public final long frameTime = 16750418;
	public final int frameCycles = 70256;
	public final float secondCycles = ((float)frameCycles)*59.7f;
	
	//public boolean soundEnabled = false;
	public boolean cpuEnabled = true;
	
	public static final int soundTicksPerSec = 4194283;//4194304;
	
	public boolean[] uiChannelEnable = {true, true, true, true};
	
	public boolean gbcMode = false;

	public final String bootcode = "31 FE FF AF 21 FF 9F 32 CB 7C 20 FB 21 26 FF 0E 11 3E 80 32 E2 0C 3E F3 E2 32 3E 77 77 3E FC E0 47 11 A8 00 21 10 80 1A CD 95 00 CD 96 00 13 7B FE 34 20 F3 11 D8 00 06 08 1A 13 22 23 05 20 F9 3E 19 EA 10 99 21 2F 99 0E 0C 3D 28 08 32 0D 20 F9 2E 0F 18 F3 67 3E 64 57 E0 42 3E 91 E0 40 04 1E 02 0E 0C F0 44 FE 90 20 FA 0D 20 F7 1D 20 F2 0E 13 24 7C 1E 83 FE 62 28 06 1E C1 FE 64 20 06 7B E2 0C 3E 87 E2 F0 42 90 E0 42 15 20 D2 05 20 4F 16 20 18 CB 4F 06 04 C5 CB 11 17 C1 CB 11 17 05 20 F5 22 23 22 23 C9 00 00 00 0D 00 09 11 09 89 39 08 C9 00 0B 00 03 00 0C CC CC 00 0F 00 00 00 00 EC CC EC CC DD DD 99 99 98 89 EE FB 67 63 6E 0E CC DD 1F 9F 88 88 00 00 00 00 00 00 00 00 21 A8 00 11 A8 00 1A 13 BE 20 FE 23 7D FE 34 20 F5 06 19 78 86 23 05 20 FB 86 20 FE 3E 01 E0 50";
	
	public void init() {
		ROMbank = 1;
		ROMbankoffset = (ROMbank - 1) * 0x4000;
		RAMbank = 0;
		RAMbankoffset = RAMbank * 0x2000 - 0xA000;
		RAMenabled = false;
		MBCRamMode = 0;
		divPrescaler = 0;
		timerPrescaler = 0;
		timerLength = 1;
		timerEnable = false;
		LCD_enabled = false;
		LCD_lastmode = 1;
		LCD_scan = 0;
	}
}
