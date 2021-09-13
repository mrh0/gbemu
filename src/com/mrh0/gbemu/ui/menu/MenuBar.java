package com.mrh0.gbemu.ui.menu;

import java.awt.event.ActionListener;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import com.mrh0.gbemu.Emulator;
import com.mrh0.gbemu.events.EmulationEventType;
import com.mrh0.gbemu.ui.events.ComponentEmulationEvent;

public class MenuBar extends JMenuBar {
	private static final long serialVersionUID = 1L;

	private Emulator emulator;
	private JMenu file;
	private JMenu emulation;
	private JMenu screen;
	private JMenu sound;
	private JMenu options;
	
	public MenuBar(Emulator emulator) {
		this.emulator = emulator;
		file = new JMenu("File");
		file.add(new JMenuItem("Open"));
		file.addSeparator();
		file.add(new JMenuItem("Save State"));
		file.add(new JMenuItem("Load State"));
		file.add(new JMenuItem("Quicksave"));
		file.add(new JMenuItem("Quickload"));
		file.addSeparator();
		file.add(new JMenuItem("About"));
		file.addSeparator();
		file.add(action(new JMenuItem("Exit"), (e) -> System.exit(0)));
		this.add(file);
		
		emulation = new JMenu("Emulation");
		emulation.add(action(event(new JCheckBoxMenuItem("Pause"), EmulationEventType.EmulationPauseResume, (c, e) -> c.setSelected(e.isPaused())), (e) -> emulator.togglePause()));
		emulation.add(new JMenuItem("Stop"));
		emulation.addSeparator();
		ButtonGroup speedGroup = new ButtonGroup();
		emulation.add(child(new JMenu("Speed"),
			group(new JRadioButtonMenuItem("60fps"), speedGroup),
			group(new JRadioButtonMenuItem("No Limit"), speedGroup),
			new JPopupMenu.Separator(),
			group(new JRadioButtonMenuItem("0.5x"), speedGroup),
			group(new JRadioButtonMenuItem("0.75x"), speedGroup),
			group(select(new JRadioButtonMenuItem("1x (59.7fps)")), speedGroup),
			group(new JRadioButtonMenuItem("1.25x"), speedGroup),
			group(new JRadioButtonMenuItem("1.5x"), speedGroup),
			group(new JRadioButtonMenuItem("2x"), speedGroup),
			group(new JRadioButtonMenuItem("4x"), speedGroup)
		));
		this.add(emulation);
		
		screen = new JMenu("Screen");
		
		screen.add(action(event(new JCheckBoxMenuItem("Fullscreen"), EmulationEventType.WindowFullscreenSet, (c, e) -> c.setSelected(e.getWindow().isFullscreen())), (e) -> emulator.getWindow().setFullscreen(!emulator.getWindow().isFullscreen())));
		ButtonGroup scaleGroup = new ButtonGroup();
		screen.add(child(new JMenu("Scale"),
			group(action(new JRadioButtonMenuItem("1x"), (e) -> emulator.setLCDScale(1)), scaleGroup),
			group(action(new JRadioButtonMenuItem("2x"), (e) -> emulator.setLCDScale(2)), scaleGroup),
			group(action(select(new JRadioButtonMenuItem("3x")), (e) -> emulator.setLCDScale(3)), scaleGroup),
			group(action(new JRadioButtonMenuItem("4x"), (e) -> emulator.setLCDScale(4)), scaleGroup),
			group(action(new JRadioButtonMenuItem("5x"), (e) -> emulator.setLCDScale(5)), scaleGroup),
			group(action(new JRadioButtonMenuItem("8x"), (e) -> emulator.setLCDScale(8)), scaleGroup),
			group(action(new JRadioButtonMenuItem("16x"), (e) -> emulator.setLCDScale(16)), scaleGroup)
		));
		screen.addSeparator();
		ButtonGroup colorGroup = new ButtonGroup();
		screen.add(child(new JMenu("Color"),
			group(action(select(new JRadioButtonMenuItem("Greenscale")), (e) -> emulator.setLCDColorMode(0)), colorGroup),
			group(action(new JRadioButtonMenuItem("Grayscale"), (e) -> emulator.setLCDColorMode(1)), colorGroup),
			group(action(new JRadioButtonMenuItem("Color"), (e) -> emulator.setLCDColorMode(0)), colorGroup)
		));
		screen.addSeparator();
		screen.add(new JCheckBoxMenuItem("Show fps"));
		this.add(screen);
		
		sound = new JMenu("Sound");
		this.add(sound);
		
		options = new JMenu("Options");
		this.add(options);
	}
	
	private static AbstractButton action(AbstractButton comp, ActionListener action) {
		comp.addActionListener(action);
		return comp;
	}
	
	private static JComponent child(JComponent parent, JComponent...children) {
		for(JComponent child : children)
			parent.add(child);
		return parent;
	}
	
	private static JComponent group(AbstractButton comp, ButtonGroup group) {
		group.add(comp);
		return comp;
	}
	
	private static <T extends AbstractButton> T select(T button) {
		button.setSelected(true);
		return button;
	}
	
	private <T extends JComponent> T event(T comp, EmulationEventType type, ComponentEmulationEvent.IComponentEvent<T> event) {
		emulator.addEvent(type, new ComponentEmulationEvent(comp, event));
		return comp;
	}
}
