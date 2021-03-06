package net.amygdalum.testrecorder;

import java.util.ArrayList;
import java.util.List;

import net.amygdalum.testrecorder.profile.Classes;
import net.amygdalum.testrecorder.profile.Fields;
import net.amygdalum.testrecorder.profile.Methods;

/**
 * Most TestRecorderAgentConfig are Factories, i.e. they produce all configurations when called (and therefore almost never return
 * the same value at different call locations). This is ok at configuration time, but after this we want to rely on concrete values.
 * 
 * FixedTestRecorderAgentConfig materializes any given TestRecorderAgentConfig, ensuring that any call to any configuration method
 * returns the same value. 
 */
public class FixedTestRecorderAgentConfig implements TestRecorderAgentConfig {

	private List<Fields> fieldExclusions;
	private List<Classes> classExclusions;
	private List<Fields> globalFields;
	private List<Methods> inputs;
    private List<Methods> outputs;

	private SnapshotConsumer snapshotConsumer;
	private long timeoutInMillis;
	private List<Classes> classes;

	public FixedTestRecorderAgentConfig(TestRecorderAgentConfig config) {
		this.fieldExclusions = new ArrayList<>(config.getFieldExclusions());
		this.classExclusions = new ArrayList<>(config.getClassExclusions());
		this.globalFields = new ArrayList<>(config.getGlobalFields());
		this.inputs = new ArrayList<>(config.getInputs());
		this.outputs = new ArrayList<>(config.getOutputs());
		this.snapshotConsumer = config.getSnapshotConsumer();
		this.timeoutInMillis = config.getTimeoutInMillis();
		this.classes = config.getClasses();
	}

	@Override
	public List<Fields> getFieldExclusions() {
		return fieldExclusions;
	}

	@Override
	public List<Classes> getClassExclusions() {
		return classExclusions;
	}

	@Override
	public List<Fields> getGlobalFields() {
		return globalFields;
	}
	
	@Override
	public List<Methods> getInputs() {
        return inputs;
    }
	
    @Override
	public List<Methods> getOutputs() {
        return outputs;
    }

	@Override
	public SnapshotConsumer getSnapshotConsumer() {
		return snapshotConsumer;
	}

	@Override
	public long getTimeoutInMillis() {
		return timeoutInMillis;
	}

	@Override
	public List<Classes> getClasses() {
		return classes;
	}

}