package com.mrh0.gbemu.lcd.color;

public class IntQueue {
	private int[] q;
	private int i;
	private int len;
	public IntQueue(int len) {
		this.len = len;
		q = new int[len];
	}
	
	public void enqueue(int e) {
		q[i++ % len] = e;
	}
	
	public int dequeue() {
		return q[--i % len];
	}
	
	public boolean isEmpty() {
		return i == 0;
	}
}
