package net.amygdalum.testrecorder.scenarios;

import static net.amygdalum.testrecorder.testing.assertj.TestsRun.testsRun;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.amygdalum.testrecorder.TestGenerator;
import net.amygdalum.testrecorder.util.Instrumented;
import net.amygdalum.testrecorder.util.TestRecorderAgentExtension;

@ExtendWith(TestRecorderAgentExtension.class)
@Instrumented(classes = {
	"net.amygdalum.testrecorder.scenarios.DifferentPublicDeclarationTypes",
	"net.amygdalum.testrecorder.scenarios.DifferentPublicDeclarationTypes$MyEnum",
	"net.amygdalum.testrecorder.scenarios.DifferentPublicDeclarationTypes$MyAnnotation",
	"net.amygdalum.testrecorder.scenarios.DifferentPublicDeclarationTypes$MyInterface",
	"net.amygdalum.testrecorder.scenarios.DifferentPublicDeclarationTypes$MyClass" })
public class DifferentPublicDeclarationTypesTest {

	@Test
	public void testCompilable() throws Exception {
		DifferentPublicDeclarationTypes types = new DifferentPublicDeclarationTypes();

		types.test();

		TestGenerator testGenerator = TestGenerator.fromRecorded();
		assertThat(testGenerator.renderTest(DifferentPublicDeclarationTypes.class)).satisfies(testsRun());
	}

}