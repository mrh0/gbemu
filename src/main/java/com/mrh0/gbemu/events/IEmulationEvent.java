package com.mrh0.gbemu.events;

import javax.swing.JComponent;

import com.mrh0.gbemu.Emulator;

public interface IEmulationEvent {
	public void trigger(Emulator emulator);
}
