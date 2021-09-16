package com.mrh0.gbemu.sound.channels;

import com.mrh0.gbemu.Globals;

public class SoundChannel4 extends AbstractSoundChannel {
	private SoundEnvelope volumeEnvelope;
    private PolynomialCounter polynomialCounter;
    private int lastResult;
    private Lfsr lfsr = new Lfsr();

    public SoundChannel4(Globals globals) {
        super(globals, 64);
        this.volumeEnvelope = new SoundEnvelope();
        this.polynomialCounter = new PolynomialCounter();
    }

    @Override
    public void start() {
        if (globals.gbcMode) {
            length.reset();
        }
        length.start();
        lfsr.start();
        volumeEnvelope.start();
    }

    @Override
    public void trigger() {
        lfsr.reset();
        volumeEnvelope.trigger();
    }

    @Override
    public int getOutput() {
        volumeEnvelope.tick();

        if (!updateLength()) {
            return 0;
        }
        if (!DACEnabled) {
            return 0;
        }

        if (polynomialCounter.tick()) {
            lastResult = lfsr.nextBit((get3() & (1 << 3)) != 0);
        }
        return lastResult * volumeEnvelope.getVolume();
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
        polynomialCounter.set43(value);
    }

	@Override
	public int getOffset() {
		return 0xFF20;
	}
}
