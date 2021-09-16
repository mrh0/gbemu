package com.mrh0.gbemu.sound.channels;

import com.mrh0.gbemu.Globals;

public abstract class AbstractSoundChannel {
	public boolean initialized = false;
	protected SoundLength length;
	protected boolean enabled;
	protected boolean DACEnabled;
	protected Globals globals;
	
	public static final int SOUND_ADDR = 0xFF10;
	
	private int[] nr;
	
	public abstract int getOutput();
	
	protected abstract void trigger();
	
	protected int getFrequency() {
        return 2048 - (get3() | ((get4() & 0b111) << 8));
    }

    public abstract void start();

    public void stop() {
    	setEnabled(false);
    }
	
	public boolean isEnabled() {
		return enabled;
	}
	public void setEnabled(boolean state) {
		enabled = state;
	}
	
	public abstract int getOffset();
	
	public AbstractSoundChannel(Globals globals, int length) {
		this.globals = globals;
		nr = new int[5];
		this.length = new SoundLength(length);
	}
	
	public void set0(int value) {
		nr[0] = value;
	}
	
	public void set1(int value) {
		nr[1] = value;
	}
	
	public void set2(int value) {
		nr[2] = value;
	}
	
	public void set3(int value) {
		nr[3] = value;
	}
	
	public void set4(int value) {
		nr[4] = value;
	}
	
	public int get0() {
		return nr[0];
	}
	
	public int get1() {
		return nr[1];
	}
	
	public int get2() {
		return nr[2];
	}
	
	public int get3() {
		return nr[3];
	}
	
	public int get4() {
		return nr[4];
	}
	
	protected boolean updateLength() {
        length.tick();
        if (!length.isEnabled()) {
            return isEnabled();
        }
        if (isEnabled() && length.getValue() == 0) {
        	setEnabled(false);
        }
        return isEnabled();
    }
	
	public class SoundEnvelope {
		private int initialVolume;
	    private int envelopeDirection;
	    private int sweep;
	    private int volume;
	    private int i;
	    private boolean finished;

	    public void set2(int register) {
	        this.initialVolume = register >> 4;
	        this.envelopeDirection = (register & (1 << 3)) == 0 ? -1 : 1;
	        this.sweep = register & 0b111;
	    }

	    public boolean isEnabled() {
	        return sweep > 0;
	    }

	    public void start() {
	        finished = true;
	        i = 8192;
	    }

	    public void trigger() {
	        volume = initialVolume;
	        i = 0;
	        finished = false;
	    }

	    public void tick() {
	        if (finished) {
	            return;
	        }
	        if ((volume == 0 && envelopeDirection == -1) || (volume == 15 && envelopeDirection == 1)) {
	            finished = true;
	            return;
	        }
	        if (++i == sweep * Globals.soundTicksPerSec / 64) {
	            i = 0;
	            volume += envelopeDirection;
	        }
	    }

	    public int getVolume() {
	        if (isEnabled()) {
	            return volume;
	        } else {
	            return initialVolume;
	        }
	    }
	}
	
	public class SoundSweep {
		private static final int DIVIDER = Globals.soundTicksPerSec / 128;

	    // sweep parameters
	    private int period;
	    private boolean negate;
	    private int shift;

	    // current process variables
	    private int timer;
	    private int shadowFreq;
	    private int nr13, nr14;
	    private int i;
	    private boolean overflow;
	    private boolean counterEnabled;
	    private boolean negging;

	    public void start() {
	        counterEnabled = false;
	        i = 8192;
	    }

	    public void trigger() {
	        this.negging = false;
	        this.overflow = false;

	        this.shadowFreq = nr13 | ((nr14 & 0b111) << 8);
	        this.timer = period == 0 ? 8 : period;
	        this.counterEnabled = period != 0 || shift != 0;

	        if (shift > 0) {
	            calculate();
	        }
	    }

	    public void set10(int value) {
	        this.period = (value >> 4) & 0b111;
	        this.negate = (value & (1 << 3)) != 0;
	        this.shift = value & 0b111;
	        if (negging && !negate) {
	            overflow = true;
	        }
	    }

	    public void set13(int value) {
	        this.nr13 = value;
	    }

	    public void set14(int value) {
	        this.nr14 = value;
	        if ((value & (1 << 7)) != 0) {
	            trigger();
	        }
	    }

	    public int get13() {
	        return nr13;
	    }

	    public int get14() {
	        return nr14;
	    }

	    public void tick() {
	        if (++i == DIVIDER) {
	            i = 0;
	            if (!counterEnabled) {
	                return;
	            }
	            if (--timer == 0) {
	                timer = period == 0 ? 8 : period;
	                if (period != 0) {
	                    int newFreq = calculate();
	                    if (!overflow && shift != 0) {
	                        shadowFreq = newFreq;
	                        nr13 = shadowFreq & 0xff;
	                        nr14 = (shadowFreq & 0x700) >> 8;
	                        calculate();
	                    }
	                }
	            }
	        }
	    }

	    private int calculate() {
	        int freq = shadowFreq >> shift;
	        if (negate) {
	            freq = shadowFreq - freq;
	            negging = true;
	        } else {
	            freq = shadowFreq + freq;
	        }
	        if (freq > 2047) {
	            overflow = true;
	        }
	        return freq;
	    }

	    public boolean isEnabled() {
	        return !overflow;
	    }
	}
	
	public class SoundLength {
		
		private final int DIVIDER = Globals.soundTicksPerSec / 256;
	    private final int fullLength;
	    private int length;
	    private long i;
	    private boolean enabled;

	    public SoundLength(int fullLength) {
	        this.fullLength = fullLength;
	    }

	    public void start() {
	        i = 8192;
	    }

	    public void tick() {
	        if (++i == DIVIDER) {
	            i = 0;
	            if (enabled && length > 0) {
	                length--;
	            }
	        }
	    }

	    public void setLength(int length) {
	        if (length == 0) {
	            this.length = fullLength;
	        } else {
	            this.length = length;
	        }
	    }

	    public void set4(int value) {
	        boolean enable = (value & (1 << 6)) != 0;
	        boolean trigger = (value & (1 << 7)) != 0;

	        if (enabled) {
	            if (length == 0 && trigger) {
	                if (enable && i < DIVIDER / 2) {
	                    setLength(fullLength - 1);
	                } else {
	                    setLength(fullLength);
	                }
	            }
	        } else if (enable) {
	            if (length > 0 && i < DIVIDER / 2) {
	                length--;
	            }
	            if (length == 0 && trigger&& i < DIVIDER / 2) {
	                setLength(fullLength - 1);
	            }
	        } else {
	            if (length == 0 && trigger) {
	                setLength(fullLength);
	            }
	        }
	        this.enabled = enable;
	    }

	    public int getValue() {
	        return length;
	    }

	    public boolean isEnabled() {
	        return enabled;
	    }

	    void reset() {
	        this.enabled = true;
	        this.i = 0;
	        this.length = 0;
	    }
	}
	
	public class Lfsr {

	    private int lfsr;

	    public Lfsr() {
	        reset();
	    }

	    public void start() {
	        reset();
	    }

	    public void reset() {
	        lfsr = 0x7fff;
	    }

	    public int nextBit(boolean widthMode7) {
	        boolean x = ((lfsr & 1) ^ ((lfsr & 2) >> 1)) != 0;
	        lfsr = lfsr >> 1;
	        lfsr = lfsr | (x ? (1 << 14) : 0);
	        if (widthMode7) {
	            lfsr = lfsr | (x ? (1 << 6) : 0);
	        }
	        return 1 & ~lfsr;
	    }

	    int getValue() {
	        return lfsr;
	    }
	}
	
	public class PolynomialCounter {

	    private int shiftedDivisor;

	    private int i;

	    public void set43(int value) {
	        int clockShift = value >> 4;
	        int divisor;
	        switch (value & 0b111) {
	            case 0:
	                divisor = 8;
	                break;

	            case 1:
	                divisor = 16;
	                break;

	            case 2:
	                divisor = 32;
	                break;

	            case 3:
	                divisor = 48;
	                break;

	            case 4:
	                divisor = 64;
	                break;

	            case 5:
	                divisor = 80;
	                break;

	            case 6:
	                divisor = 96;
	                break;

	            case 7:
	                divisor = 112;
	                break;

	            default:
	                throw new IllegalStateException();
	        }
	        shiftedDivisor = divisor << clockShift;
	        i = 1;
	    }

	    public boolean tick() {
	        if (--i == 0) {
	            i = shiftedDivisor;
	            return true;
	        } else {
	            return false;
	        }
	    }
	}
}
