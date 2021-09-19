package com.mrh0.gbemu.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class IO {
	
	public static final String logPath = "C:\\Games\\GB\\log.txt";
	public static final String otherLogPath = "C:\\Games\\GB\\other.txt";
	
	public static byte[] readROM(File file) throws IOException {
		return Files.readAllBytes(file.toPath());
	}
	
	public static FileWriter logWriter() {
		File file = new File(logPath);
		if(file.exists() && file.isFile())
			file.delete();
		try {
			return new FileWriter(logPath);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public static void log(FileWriter writer, String str) {
		try {
			writer.write(str);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void compareLogs() {
		int n = 0;
		try {
			File file1 = new File(logPath);
			FileReader fr1 = new FileReader(file1);
			BufferedReader br1 = new BufferedReader(fr1);
			
			File file2 = new File(otherLogPath);
			FileReader fr2 = new FileReader(file2);
			BufferedReader br2 = new BufferedReader(fr2);
			
			String line1;
			String line2;
			while((line1=br1.readLine())!=null && (line2=br2.readLine())!=null) {
				n++;
				if(line1.equals(line2))
					continue;
				if(n < 3)
					continue;
				System.err.println("DIF ON LINE " + n);
				System.out.println("log: "+line1);
				System.out.println("other: "+line2);
				System.exit(0);
			}
			br1.close();
			br2.close();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		System.out.println("DONE");
		System.exit(0);
	}
	
	public static void sleep(int t) {
		try {
			//System.out.println("Sleep " + t + "ms");
			Thread.sleep(t);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
