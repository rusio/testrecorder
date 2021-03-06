package net.amygdalum.testrecorder.ioscenarios;

import static net.amygdalum.testrecorder.testing.assertj.TestsRun.testsRun;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.amygdalum.testrecorder.TestGenerator;
import net.amygdalum.testrecorder.util.Instrumented;
import net.amygdalum.testrecorder.util.TestRecorderAgentExtension;

@ExtendWith(TestRecorderAgentExtension.class)
@Instrumented(classes = { "net.amygdalum.testrecorder.ioscenarios.NestedOutput" })
public class NestedOutputTest {

	@Test
	public void testCompilable() throws Exception {
		NestedOutput input = new NestedOutput();
		int time = input.getTime();
		time = input.getTime();

		assertThat(time).isEqualTo(2);

		TestGenerator testGenerator = TestGenerator.fromRecorded();
		assertThat(testGenerator.renderTest(NestedOutput.class)).satisfies(testsRun());
	}

}
