package net.amygdalum.testrecorder.scenarios;

import static com.almondtools.conmatch.strings.WildcardStringMatcher.containsPattern;
import static net.amygdalum.testrecorder.dynamiccompile.CompilableMatcher.compiles;
import static net.amygdalum.testrecorder.dynamiccompile.TestsRunnableMatcher.testsRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.amygdalum.testrecorder.TestGenerator;
import net.amygdalum.testrecorder.util.Instrumented;
import net.amygdalum.testrecorder.util.TestRecorderAgentExtension;

@ExtendWith(TestRecorderAgentExtension.class)
@Instrumented(classes = { "net.amygdalum.testrecorder.scenarios.Imports", "net.amygdalum.testrecorder.scenarios.Imports$List" })
public class ImportsTest {

	@Test
	public void testCompilable() throws Exception {
		Imports object = new Imports("name");

		assertThat(object.toString()).isEqualTo("[name]:name");

		TestGenerator testGenerator = TestGenerator.fromRecorded();
		assertThat(testGenerator.renderTest(Imports.class), compiles(Imports.class));
		assertThat(testGenerator.renderTest(Imports.class), testsRun(Imports.class));
	}

	@Test
	public void testCode() throws Exception {
		Imports object = new Imports("name");

		assertThat(object.toString()).isEqualTo("[name]:name");

		TestGenerator testGenerator = TestGenerator.fromRecorded();
		assertThat(testGenerator.testsFor(Imports.class), hasSize(1));
		assertThat(testGenerator.testsFor(Imports.class), contains(allOf(
			containsPattern("Imports imports? = new GenericObject() {*"
				+ "List<String> list = *"
				+ "net.amygdalum.testrecorder.scenarios.Imports.List otherList *"
				+ "}.as(Imports.class);"),
			containsPattern("new GenericMatcher() {*"
				+ "Matcher<?> list = containsInOrder(String.class, \"name\");*"
				+ "Matcher<?> otherList = new GenericMatcher() {*"
				+ "String name = \"name\";*"
				+ "}.matching(net.amygdalum.testrecorder.scenarios.Imports.List.class);*"
				+ "}.matching(Imports.class)"))));
		assertThat(testGenerator.renderTest(Imports.class), allOf(
			containsString("import java.util.List;"),
			not(containsPattern("import net.amygdalum.testrecorder.scenarios.Imports.List;"))));
	}
}