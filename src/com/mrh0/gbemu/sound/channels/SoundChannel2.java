package com.mrh0.gbemu.sound.channels;

import com.mrh0.gbemu.Globals;

public class SoundChannel2 extends AbstractSoundChannel {

	private int freqDivider;

    private int lastOutput;

    private int i;

    private SoundEnvelope volumeEnvelope;
	
	public SoundChannel2(Globals globals) {
		super(globals, 64);
		this.volumeEnvelope = new SoundEnvelope();
	}

	@Override
    public void start() {
        i = 0;
        if (globals.gbcMode) {
            length.reset();
        }
        length.start();
        volumeEnvelope.start();
    }

    @Override
    public void trigger() {
        this.i = 0;
        freqDivider = 1;
        volumeEnvelope.trigger();
    }

    @Override
    public int getOutput() {
        volumeEnvelope.tick();

        boolean e = true;
        e = updateLength() && e;
        e = DACEnabled && e;
        if (!e) {
            return 0;
        }

        if (--freqDivider == 0) {
            resetFreqDivider();
            lastOutput = ((getDuty() & (1 << i)) >> i);
            i = (i + 1) % 8;
        }
        return lastOutput * volumeEnvelope.getVolume();
    }

    @Override
    public void set0(int value) {
        super.set0(value);
    }

    @Override
    public void set1(int value) {
        super.set1(value);
        length.setLength(64 - (value & 0b00111111));
    }

    @Override
    public void set2(int value) {
        super.set2(value);
        volumeEnvelope.set2(value);
        DACEnabled = (value & 0b11111000) != 0;
        enabled &= DACEnabled;
    }

    private int getDuty() {
        switch (get1() >> 6) {
            case 0:
                return 0b00000001;
            case 1:
                return 0b10000001;
            case 2:
                return 0b10000111;
            case 3:
                return 0b01111110;
            default:
                throw new IllegalStateException();
        }
    }

    private void resetFreqDivider() {
        freqDivider = getFrequency() * 4;
    }

	@Override
	public int getOffset() {
		return 0xFF16;
	}
}
