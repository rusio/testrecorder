package net.amygdalum.testrecorder.scenarios;

import net.amygdalum.testrecorder.TestRecorderAgentInitializer;

public class GlobalsInitializer implements TestRecorderAgentInitializer {

	@Override
	public void run() {
		Globals.initialized = 42;
	}

}
