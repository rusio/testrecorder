package com.almondtools.invivoderived.scenarios;

import com.almondtools.invivoderived.Snapshot;

public class Results {

	public Results() {
	}

	@Snapshot
	public double pow(int i) {
		return Math.pow(i, i);
	}
}