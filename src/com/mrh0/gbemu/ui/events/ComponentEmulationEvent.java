package com.mrh0.gbemu.ui.events;

import javax.swing.JComponent;

import com.mrh0.gbemu.Emulator;
import com.mrh0.gbemu.events.IEmulationEvent;

public class ComponentEmulationEvent implements IEmulationEvent {

	private JComponent comp;
	private IComponentEvent event;
	public ComponentEmulationEvent(JComponent comp, IComponentEvent event) {
		this.comp = comp;
		this.event = event;
	}
	
	@Override
	public void trigger(Emulator emulator) {
		event.trigger(comp, emulator);
	}

	public interface IComponentEvent<T> {
		public void trigger(T comp, Emulator emulator);
	}
}
