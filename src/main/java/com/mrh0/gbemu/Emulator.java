package com.mrh0.gbemu;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JOptionPane;

import com.mrh0.gbemu.cpu.CPU;
import com.mrh0.gbemu.events.EmulationEventManager;
import com.mrh0.gbemu.events.EmulationEventType;
import com.mrh0.gbemu.events.IEmulationEvent;
import com.mrh0.gbemu.io.IO;
import com.mrh0.gbemu.io.Input;
import com.mrh0.gbemu.lcd.LCD;
import com.mrh0.gbemu.lcd.color.CLCD;
import com.mrh0.gbemu.memory.Memory;
import com.mrh0.gbemu.sound.SoundManager;
import com.mrh0.gbemu.ui.Window;
import com.mrh0.gbemu.ui.game.Renderer;

public class Emulator implements Runnable {
	private Globals globals;
	private CPU cpu;
	private Memory memory;
	private Renderer renderer;
	private Window window;
	private Input input;
	private SoundManager sound;
	
	private final EmulationEventManager eventManager;
	
	public Emulator() {
		eventManager = new EmulationEventManager();
		
		globals = new Globals();
		renderer = new Renderer(this, null);
		
		// Setup Memory:
		memory = new Memory(this);
		memory.raw()[0xFF41] = 0x01;
		memory.raw()[0xFF43] = 0x00;
		// Unknown function:
		memory.raw()[0xFFF4] = (byte) 0x31;
		memory.raw()[0xFFF7] = (byte) 0xFF;
		
		sound = new SoundManager(this);
		
		cpu = new CPU(this);
		
		input = new Input(this);
		
		window = createWindow();
		window.init(this);
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				memory.RAMSave();
				System.out.println("Quit");
			}
		}));
	}
	
	public void triggerEvent(EmulationEventType type) {
		eventManager.trigger(type, this);
	}
	
	public void addEvent(EmulationEventType type, IEmulationEvent event) {
		eventManager.addListener(type, event);
	}
	
	public boolean setROM(byte[] rom) {
		if(rom == null)
			return false;
		memory.setROM(rom);
		return true;
	}
	
	public boolean setROM(File rom) {
		try {
			return setROM(readROM(rom));
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		System.err.println("Failed to load ROM file.");
		return false;
	}
	
	public Globals getGlobals() {
		return globals;
	}
	
	public CPU getCPU() {
		return cpu;
	}
	
	public Renderer getRenderer() {
		return renderer;
	}
	
	public Window getWindow() {
		return window;
	}
	
	public Input getInput() {
		return input;
	}
	
	public Memory getMemory() {
		return memory;
	}
	
	public SoundManager getSound() {
		return sound;
	}
	
	private static Window createWindow() {
		Window win = new Window();
		win.setVisible(true);
		return win;
	}
	
	@Override
	public void run() {
		globals.gameHasLoaded = true;
		cpu.reset();
		int relativePercent = 1;
		long sec = System.nanoTime();
		long time = System.nanoTime();
		long ntime = time + globals.frameTime;
		int cycles = globals.frameCycles;
		long cycleSum = 0;
		
		while (true) {
			long nano = System.nanoTime();
			if(sec < nano) {
				sec = nano + 1000000000;
				relativePercent = (int) (100f * ((float)cycleSum)/Globals.cyclesPerSec);
				cycleSum = 0;
			}
			windowTick(window, globals, relativePercent);
			if (!globals.cpuEnabled)
				continue;
			//cpu.debug();

			int c = cpu.advance();
			
			for(int i = 0; i < c; i++)
				sound.tickOutput();
			
			if(globals.doubleSpeed)
				c+=cpu.advance();
			
			cycleSum += c;
			cycles -= c;
			while (cycles < 0) {
				if ((time = System.nanoTime()) > ntime) {
					ntime = time + globals.frameTime / (globals.doubleSpeed?2:1);
					cycles = globals.frameCycles * (globals.doubleSpeed?2:1);
				}
			}
		}
	}
	
	private String getWindowTitle(int precent) {
		return "gbemu" + (globals.cpuEnabled ? " - " + precent + "%" : " - PAUSED");
	}

	private static String lastTitle = "";

	private void windowTick(Window window, Globals globals, int precent) {
		if (!lastTitle.equals(lastTitle = getWindowTitle(precent)))
			window.setTitle(lastTitle);
	}
	
	private byte[] readBootcode(String name) throws IOException, URISyntaxException {
		Path p = Paths.get(this.getClass().getClassLoader().getResource(name+".bin").toURI());
		return IO.readBin(p.toAbsolutePath().toFile());
	}

	private byte[] readROM(File file) throws IOException, URISyntaxException {
		if(file == null)
			return null;
		byte[] rom = IO.readBin(file);
		
		//byte[] bootcode = parseBootcode();
		globals.CGBMode = (rom[0x0143]&0xFF) == 0xC0 || (rom[0x0143]&0xFF) == 0x80;
		globals.universalMode = (rom[0x0143]&0xFF) == 0x80;
		
		if(globals.universalMode) {
			String[] options = {"CGB Color", "DMG Monochrome"};
			int input = JOptionPane.showOptionDialog(getRenderer(),
				"This cartridge ROM is Universal, what mode would you like to launch it in?",
				"Select Launch Mode",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[0]);
			if(input == JOptionPane.NO_OPTION)
				globals.CGBMode = false;
		}
		
		String bootname = globals.CGBMode?"cgb_boot":"dmg_boot";
		
		byte[] bootcode;
		try {
			bootcode = readBootcode(bootname);
		}
		catch(Exception e) {
			System.err.println("Failed to load src/main/resources/"+bootname+".bin");
			throw e;
		}
		
		/*globals.FirstROMPage = new byte[getBootromLength()];
		for (int i = 0; i < getBootromLength(); i++) {
			globals.FirstROMPage[i] = rom[i];
			rom[i] = bootcode[i];
		}*/
		globals.bootROM = bootcode;
		
		if(isCGB())
			memory.setCGB();
		renderer.setLCD(isCGB()?new CLCD():new LCD());
		
		String title = getTitle(rom);
		String mode = (globals.universalMode?"Universal":(isCGB()?"CGB":"DMG"));
		
		globals.currentROMFile = file;
		globals.currentROMName = title;
		System.out.println("Loaded "+mode+" ROM '"+title+"' (" + rom.length + "bytes)." );
		memory.RAMLoad();
		return rom;
	}
	
	 private static String getTitle(byte[] rom) {
	        StringBuilder t = new StringBuilder();
	        for (int i = 0x0134; i < 0x0143; i++) {
	            char c = (char) rom[i];
	            if (c == 0)
	                break;
	            t.append(c);
	        }
	        return t.toString();
	    }

	/*private byte[] parseBootcode() {
		String[] splt = globals.bootcodeDMG.split(" ");
		byte[] bytes = new byte[splt.length];
		for (int i = 0; i < splt.length; i++)
			bytes[i] = (byte) Integer.parseInt(splt[i], 16);
		return bytes;
	}*/
	
	public void togglePause() {
		globals.cpuEnabled = !globals.cpuEnabled;
		triggerEvent(EmulationEventType.EmulationPauseResume);
	}
	
	public boolean isPaused() {
		return !globals.cpuEnabled;
	}
	
	public void setLCDScale(int scale) {
		renderer.setScale(scale);
		if(!window.isFullscreen())
			window.pack();
		else
			window.validate();
		triggerEvent(EmulationEventType.LCDScaleSet);
	}
	
	public void setLCDColorMode(int mode) {
		renderer.setColorMode(mode);
		triggerEvent(EmulationEventType.LCDColorModeSet);
	}
	
	public void toggleMuteAudio() {
		globals.uiMuteAll = !globals.uiMuteAll;
	}
	
	public void setMasterVolume(int vol) {
		globals.uiVolume = vol;
	}
	
	public boolean isCGB() {
		return globals.CGBMode;
	}
}
