package net.amygdalum.testrecorder.ioscenarios;

import static net.amygdalum.extensions.assertj.Assertions.assertThat;
import static net.amygdalum.testrecorder.testing.assertj.TestsRun.testsRun;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.amygdalum.testrecorder.TestGenerator;
import net.amygdalum.testrecorder.util.Instrumented;
import net.amygdalum.testrecorder.util.TestRecorderAgentExtension;

@ExtendWith(TestRecorderAgentExtension.class)
@Instrumented(classes = { "net.amygdalum.testrecorder.ioscenarios.HiddenOutput", "net.amygdalum.testrecorder.ioscenarios.Outputs" })
public class HiddenOutputTest {

	@Test
	public void testOutputImmediate() throws Exception {
		HiddenOutput output = new HiddenOutput();

		output.outputImmediate("Hello");

		TestGenerator testGenerator = TestGenerator.fromRecorded();
		assertThat(testGenerator.renderTest(HiddenOutput.class)).satisfies(testsRun());
		assertThat(testGenerator.renderTest(HiddenOutput.class).getTestCode())
			.containsWildcardPattern(".add(HiddenOutput.class, \"outputImmediate\", *, null, equalTo(\"Hello\")")
			.contains("verify()");
	}

	@Test
	public void testOutputWithUnexposedDependency() throws Exception {
		HiddenOutput output = new HiddenOutput();

		output.outputToField("Hello");

		TestGenerator testGenerator = TestGenerator.fromRecorded();
		assertThat(testGenerator.renderTest(HiddenOutput.class)).satisfies(testsRun());
		assertThat(testGenerator.renderTest(HiddenOutput.class).getTestCode())
			.containsWildcardPattern(".add(HiddenOutput.class, \"outputToField\", *, null, equalTo(\"Hello\")")
			.contains("verify()");
	}

}