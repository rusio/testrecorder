package net.amygdalum.testrecorder.scenarios;

import static net.amygdalum.testrecorder.testing.assertj.TestsRun.testsRun;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.amygdalum.testrecorder.TestGenerator;
import net.amygdalum.testrecorder.util.Instrumented;
import net.amygdalum.testrecorder.util.TestRecorderAgentExtension;

@ExtendWith(TestRecorderAgentExtension.class)
@Instrumented(classes = { "net.amygdalum.testrecorder.scenarios.Exceptions" })
public class ExceptionsTest {

	@Test
	public void testCompilesAndRuns() throws Exception {
		Exceptions out = new Exceptions();
		try {
			out.throwingException();
		} catch (IllegalArgumentException e) {
		}

		TestGenerator testGenerator = TestGenerator.fromRecorded();
		assertThat(testGenerator.renderTest(Exceptions.class)).satisfies(testsRun());
	}

}