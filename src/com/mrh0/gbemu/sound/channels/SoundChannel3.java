package com.mrh0.gbemu.sound.channels;

import com.mrh0.gbemu.Globals;

public class SoundChannel3 extends AbstractSoundChannel {
	
	private static final int[] DMG_WAVE = new int[] { 0x84, 0x40, 0x43, 0xaa, 0x2d, 0x78, 0x92, 0x3c, 0x60, 0x59, 0x59,
			0xb0, 0x34, 0xb8, 0x2e, 0xda };

	private static final int[] CGB_WAVE = new int[] { 0x00, 0xff, 0x00, 0xff, 0x00, 0xff, 0x00, 0xff, 0x00, 0xff, 0x00,
			0xff, 0x00, 0xff, 0x00, 0xff };

	private int freqDivider;
	private int lastOutput;
	private int i;
	private int ticksSinceRead = 65536;
	private int lastReadAddr;
	private int buffer;
	private boolean triggered;
	
	private static int WAVE_RAM = 0xFF30;
	
	private int[] waveRAM;
	
	public SoundChannel3(Globals globals) {
		super(globals, 256);
		waveRAM = new int[getWave().length];
		for(int i = 0; i < getWave().length; i++)
			waveRAM[i] = (byte) (getWave()[i]&0xFF);
	}

	private int[] getWave() {
		return globals.gbcMode ? CGB_WAVE : DMG_WAVE;
	}

	@Override
	public void set0(int value) {
		super.set0(value);
		DACEnabled = (value & (1 << 7)) != 0;
		enabled &= DACEnabled;
	}

	@Override
	public void set1(int value) {
		super.set1(value);
		length.setLength(256 - value);
	}

	@Override
	public void set3(int value) {
		super.set3(value);
	}

	@Override
	public void set4(int value) {
		if (!globals.gbcMode && (value & (1 << 7)) != 0) {
			if (isEnabled() && freqDivider == 2) {
				int pos = i / 2;
				if (pos < 4) {
					waveRAM[0] = waveRAM[pos];
				} else {
					pos = pos & ~3;
					for (int j = 0; j < 4; j++)
						waveRAM[j] = waveRAM[((pos + j) % 0x10)];
				}
			}
		}
		super.set4(value);
	}
	
	public int readWave(int addr) {
		if(!isEnabled())
			return waveRAM[addr-WAVE_RAM];
		else if(withinWave(lastReadAddr) && (globals.gbcMode || ticksSinceRead < 2))
			return waveRAM[lastReadAddr-WAVE_RAM];
		return 0xff;
	}
	
	public void writeWave(int addr, int value) {
		if (!isEnabled()) {
			waveRAM[addr-WAVE_RAM] = value;
        } else if (withinWave(lastReadAddr) && (globals.gbcMode || ticksSinceRead < 2)) {
        	waveRAM[lastReadAddr-WAVE_RAM] = value;
        }
	}
	
	private boolean withinWave(int addr) {
		return addr-WAVE_RAM>=0 && addr-WAVE_RAM <= getWave().length;
	}

	@Override
	public int getOffset() {
		return 0xFF1A;
	}

	@Override
	public void start() {
		i = 0;
		buffer = 0;
		if (globals.gbcMode)
			length.reset();
		length.start();
	}

	@Override
	public void trigger() {
		i = 0;
		freqDivider = 6;
		triggered = !globals.gbcMode;
		if (globals.gbcMode)
			getWaveEntry();
	}

	@Override
	public int getOutput() {
		ticksSinceRead++;
		if (!updateLength())
			return 0;
		
		if (!DACEnabled)
			return 0;

		if ((get0() & (1 << 7)) == 0)
			return 0;

		if (--freqDivider == 0) {
			resetFreqDivider();
			if (triggered) {
				lastOutput = (buffer >> 4) & 0x0f;
				triggered = false;
			} 
			else
				lastOutput = getWaveEntry();
			i = (i + 1) % 32;
		}
		return lastOutput;
	}

	private int getVolume() {
		return (get2() >> 5) & 0b11;
	}

	private int getWaveEntry() {
		ticksSinceRead = 0;
		lastReadAddr = 0xff30 + i / 2;
		buffer = waveRAM[lastReadAddr - SOUND_ADDR];
		
		int b = buffer;
		
		if (i % 2 == 0)
			b = (b >> 4) & 0x0f;
		else
			b = b & 0x0f;
		
		switch (getVolume()) {
			case 0:
				return 0;
			case 1:
				return b;
			case 2:
				return b >> 1;
			case 3:
				return b >> 2;
			default:
				throw new IllegalStateException();
		}
	}

	private void resetFreqDivider() {
		freqDivider = getFrequency() * 2;
	}
}
