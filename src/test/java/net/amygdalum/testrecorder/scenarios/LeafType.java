package net.amygdalum.testrecorder.scenarios;

import net.amygdalum.testrecorder.Snapshot;

public class LeafType implements InnerType {

	private LeafMethods leafMethods;

	public LeafType(LeafMethods leafMethods) {
		this.leafMethods = leafMethods;
	}
	
	@Snapshot
	public String quote(String string) {
		return "'" + string + "'";
	}

	@Override
	public String method() {
		return quote(leafMethods.toString());
	}

}
