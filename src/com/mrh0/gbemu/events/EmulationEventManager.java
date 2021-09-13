package com.mrh0.gbemu.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mrh0.gbemu.Emulator;

public class EmulationEventManager {
	private Map<EmulationEventType, List<IEmulationEvent>> listeners;

	public EmulationEventManager() {
		listeners = new HashMap<>();
	}
	
	public void addListener(EmulationEventType type, IEmulationEvent event) {
		List<IEmulationEvent> list = listeners.getOrDefault(type, new ArrayList<>());
		if(list.isEmpty())
			listeners.put(type, list);
		list.add(event);
	}
	
	public void trigger(EmulationEventType type, Emulator emulator) {
		for(IEmulationEvent e : listeners.getOrDefault(type, new ArrayList<>())) {
			e.trigger(emulator);
		}
	}
	
	public void clear(EmulationEventType type) {
		listeners.remove(type);
	}
}
