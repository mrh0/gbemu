package com.mrh0.gbemu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Debugger {
	private List<String> opList;
	private Map<String, String> opMap;
	
	public Debugger() {
		opList = new ArrayList<>();
		opMap = new HashMap<>();
	}
	
	public void op(String  opcode) {
		if(opMap.put(opcode, opcode) == null)
			opList.add(opcode);
	}
	
	public boolean has(String opcode) {
		return opMap.containsKey(opcode);
	}
	
	public void printListed(Debugger filter) {
		for(int i = 0; i < opList.size(); i++) {
			if(!filter.has(opList.get(i)))
				System.out.println("Debugger: " + opList.get(i));
		}
	}
}
