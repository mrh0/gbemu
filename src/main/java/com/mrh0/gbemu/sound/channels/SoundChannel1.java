package com.mrh0.gbemu.sound.channels;

import com.mrh0.gbemu.Globals;

public class SoundChannel1 extends AbstractSoundChannel {

	public SoundChannel1(Globals globals) {
		super(globals, 64);
		this.frequencySweep = new SoundSweep();
        this.volumeEnvelope = new SoundEnvelope();
	}

	private int freqDivider;

    private int lastOutput;

    private int i;

    private SoundSweep frequencySweep;

    private SoundEnvelope volumeEnvelope;

    @Override
    public void start() {
        i = 0;
        if (globals.CGBMode) {
            length.reset();
        }
        length.start();
        frequencySweep.start();
        volumeEnvelope.start();
    }

    @Override
    public void trigger() {
        i = 0;
        freqDivider = 1;
        volumeEnvelope.trigger();
    }

    @Override
    public int getOutput() {
        volumeEnvelope.tick();

        boolean e = true;
        e = updateLength() && e;
        e = updateSweep() && e;
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
        frequencySweep.set10(value);
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

    @Override
    public void set3(int value) {
        super.set3(value);
        frequencySweep.set13(value);
    }

    @Override
    public void set4(int value) {
        super.set4(value);
        frequencySweep.set14(value);
    }

    @Override
    public int get3() {
        return frequencySweep.get13();
    }

    @Override
    public int get4() {
        return (super.get4() & 0b11111000) | (frequencySweep.get14() & 0b00000111);
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

    protected boolean updateSweep() {
        frequencySweep.tick();
        if (enabled && !frequencySweep.isEnabled()) {
            enabled = false;
        }
        return enabled;
    }

	@Override
	public int getOffset() {
		return 0xFF10;
	}

}
