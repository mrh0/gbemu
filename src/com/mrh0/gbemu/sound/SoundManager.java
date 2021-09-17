package com.mrh0.gbemu.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioSystem;

import com.mrh0.gbemu.Emulator;
import com.mrh0.gbemu.Globals;
import com.mrh0.gbemu.sound.channels.AbstractSoundChannel;
import com.mrh0.gbemu.sound.channels.SoundChannel1;
import com.mrh0.gbemu.sound.channels.SoundChannel2;
//import com.mrh0.gbemu.sound.channels.SoundChannel1;
import com.mrh0.gbemu.sound.channels.SoundChannel3;
import com.mrh0.gbemu.sound.channels.SoundChannel4;

public class SoundManager {
	private Emulator emulator;

	private static final int SAMPLE_RATE = 22050;
	private static final int BUFFER_SIZE = 1024;
	private static final AudioFormat FORMAT = new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, SAMPLE_RATE, 8, 2, 2, SAMPLE_RATE, false);

	//Sound
	private SourceDataLine line;
	private byte[] buffer;

	//Output
	private int index;
	private int tick;
	private int divider;
	
	private boolean masterEnable;

	private static final int[] MASKS = new int[] { 0x80, 0x3f, 0x00, 0xff, 0xbf, 0xff, 0x3f, 0x00, 0xff, 0xbf, 0x7f,
			0xff, 0x9f, 0xff, 0xbf, 0xff, 0xff, 0x00, 0x00, 0xbf, 0x00, 0x00, 0x70, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
			0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00 };

	private int NR50;
	private int NR51;

	private AbstractSoundChannel[] allChannels;
	private int[] channels;

	public SoundManager(Emulator emulator) {
		this.emulator = emulator;
		allChannels = new AbstractSoundChannel[4];
		channels = new int[4];

		allChannels[0] = new SoundChannel1(emulator.getGlobals());
		allChannels[1] = new SoundChannel2(emulator.getGlobals());
		allChannels[2] = new SoundChannel3(emulator.getGlobals());
		allChannels[3] = new SoundChannel4(emulator.getGlobals());
	}

	public int readWaveRAM(int addr) {
		return ((SoundChannel3) allChannels[2]).readWave(addr);
	}

	public void writeWaveRAM(int addr, int value) {
		((SoundChannel3) allChannels[2]).writeWave(addr, value);
	}

	public void startOutput() {
		if (line != null) {
			System.out.println("Sound already started");
			return;
		}
		System.out.println("Start-SMOut");
		try {
			line = AudioSystem.getSourceDataLine(FORMAT);
			line.open(FORMAT, BUFFER_SIZE);
		} catch (LineUnavailableException e) {
			throw new RuntimeException(e);
		}
		line.start();
		buffer = new byte[line.getBufferSize()];
		divider = (int) (Globals.soundTicksPerSec / FORMAT.getSampleRate());
	}

	public void stopOutput() {
		if (line == null)
			System.out.println("Can't stop - sound wasn't started");
		line.drain();
		line.stop();
		line = null;
	}

	public void playOutput(byte left, byte right) {
		if (tick++ != 0) {
			tick %= divider;
			return;
		}

		if (!(left >= 0 && left < 256 && right >= 0 && right < 256)) {
			System.err.println("Sound argument error");
			return;
		}

		buffer[index++] = (byte) (left);
		buffer[index++] = (byte) (right);

		if (index > BUFFER_SIZE/2) {
			line.write(buffer, 0, index);
			index = 0;
		}
	}

	private void start() {
		System.out.println("Start-SM");
		for (int i = 0xff10; i <= 0xff25; i++) {
			int v = 0;
			// lengths should be preserved
			if (i == 0xff11 || i == 0xff16 || i == 0xff20) { // channel 1, 2, 4 lengths
				v = getUnmasked(i) & 0b00111111;
			} else if (i == 0xff1b) { // channel 3 length
				v = getUnmasked(i);
			}
			write(i, v);
		}
		for (AbstractSoundChannel s : allChannels) {
			s.start();
		}
		startOutput();
	}

	private void stop() {
		System.out.println("Stop-SM");
		stopOutput();
		for (AbstractSoundChannel s : allChannels) {
			s.stop();
		}
	}

	public void tickOutput() {
		if (!masterEnable)
			return;

		for (int i = 0; i < 4; i++) {
			AbstractSoundChannel s = allChannels[i];
			channels[i] = s.getOutput();
		}

		int selection = read(0xFF25);
		int left = 0;
		int right = 0;
		for (int i = 0; i < 4; i++) {
			// if (!overridenEnabled[i])
			// continue;
			if ((selection & (1 << i + 4)) != 0) {
				left += channels[i];
			}
			if ((selection & (1 << i)) != 0) {
				right += channels[i];
			}
		}
		left /= 4;
		right /= 4;

		int volumes = read(0xFF24);
		left *= ((volumes >> 4) & 0b111);
		right *= (volumes & 0b111);

		playOutput((byte) left, (byte) right);
	}

	public int getUnmasked(int addr) {
		switch (addr) {
		case 0xFF10:
			return allChannels[0].get0();
		case 0xFF11:
			return allChannels[0].get1();
		case 0xFF12:
			return allChannels[0].get2();
		case 0xFF13:
			return 0x00;// allChannels[0].get3();
		case 0xFF14:
			return allChannels[0].get4();

		case 0xFF16:
			return allChannels[1].get1();
		case 0xFF17:
			return allChannels[1].get2();
		case 0xFF18:
			return 0x00;// allChannels[1].get2();
		case 0xFF19:
			return allChannels[1].get4();

		case 0xFF20:
			return allChannels[3].get1();
		case 0xFF21:
			return allChannels[3].get2();
		case 0xFF22:
			return allChannels[3].get3();
		case 0xFF23:
			return allChannels[3].get4();

		case 0xFF24:
			return NR50;
		case 0xFF25:
			return NR51;

		case 0xFF1A:
			return allChannels[2].get0();
		case 0xFF1B:
			return allChannels[2].get1();
		case 0xFF1C:
			return allChannels[2].get2();
		case 0xFF1D:
			return 0x00;// allChannels[2].get3(); // (Write only)
		case 0xFF1E:
			return allChannels[2].get4();
		}
		return 0x00;
	}

	public int read(int addr) {
		int result = 0;
		if (addr == 0xFF26) {
			result = 0;
			for (int i = 0; i < allChannels.length; i++) {
				result |= (allChannels[i].isEnabled() ? (1 << i) : 0);
			}
			result |= (masterEnable ? (1 << 7) : 0);
			
		} else
			result = getUnmasked(addr);
		return result | MASKS[addr - 0xFF10];
	}

	public void write(int addr, int val) {
		if (addr == 0xFF26) {
			if ((val & (1 << 7)) == 0) {
				if (masterEnable) {
					masterEnable = false;
					stop();
				}
			} else {
				if (!masterEnable) {
					masterEnable = true;
					start();
				}
			}
			return;
		}
		switch (addr) {
		case 0xFF10:
			allChannels[0].set0(val);
			return;
		case 0xFF11:
			allChannels[0].set1(val);
			return;
		case 0xFF12:
			allChannels[0].set2(val);
			return;
		case 0xFF13:
			allChannels[0].set3(val);
			return;
		case 0xFF14:
			allChannels[0].set4(val);
			return;

		case 0xFF16:
			allChannels[1].set1(val);
			return;
		case 0xFF17:
			allChannels[1].set2(val);
			return;
		case 0xFF18:
			allChannels[1].set3(val);
			return;
		case 0xFF19:
			allChannels[1].set4(val);
			return;

		case 0xFF20:
			allChannels[3].set1(val);
			return;
		case 0xFF21:
			allChannels[3].set2(val);
			return;
		case 0xFF22:
			allChannels[3].set3(val);
			return;
		case 0xFF23:
			allChannels[3].set4(val);
			return;

		case 0xFF24:
			NR50 = val;
			return;
		case 0xFF25:
			NR51 = val;
			return;

		case 0xFF1A:
			allChannels[2].set0(val);
			return;
		case 0xFF1B:
			allChannels[2].set1(val);
			return;
		case 0xFF1C:
			allChannels[2].set2(val);
			return;
		case 0xFF1D:
			allChannels[2].set3(val);
			return;
		case 0xFF1E:
			allChannels[2].set4(val);
			return;
		}
	}
}
