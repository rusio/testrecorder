package com.almondtools.testrecorder.scenarios;

import static com.almondtools.conmatch.strings.WildcardStringMatcher.containsPattern;
import static com.almondtools.testrecorder.dynamiccompile.CompilableMatcher.compiles;
import static com.almondtools.testrecorder.dynamiccompile.TestsRunnableMatcher.testsRuns;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.almondtools.testrecorder.ConfigRegistry;
import com.almondtools.testrecorder.DefaultConfig;
import com.almondtools.testrecorder.TestGenerator;
import com.almondtools.testrecorder.util.Instrumented;
import com.almondtools.testrecorder.util.InstrumentedClassLoaderRunner;

@RunWith(InstrumentedClassLoaderRunner.class)
@Instrumented(classes = { "com.almondtools.testrecorder.scenarios.HiddenInnerClass", "com.almondtools.testrecorder.scenarios.HiddenInnerClass$Hidden" })
public class HiddenInnerClassTest {

	@Before
	public void before() throws Exception {
		((TestGenerator) ConfigRegistry.loadConfig(DefaultConfig.class).getSnapshotConsumer()).clearResults();
	}

	@Test
	public void testCompilable() throws Exception {
		HiddenInnerClass object = new HiddenInnerClass("hidden name");

		assertThat(object.toString(), equalTo("hidden name"));

		TestGenerator testGenerator = TestGenerator.fromRecorded(object);
		assertThat(testGenerator.renderTest(HiddenInnerClass.class), compiles());
		assertThat(testGenerator.renderTest(HiddenInnerClass.class), testsRuns());
	}

	@Test
	public void testCode() throws Exception {
		HiddenInnerClass object = new HiddenInnerClass("hidden name");

		assertThat(object.toString(), equalTo("hidden name"));

		TestGenerator testGenerator = TestGenerator.fromRecorded(object);
		assertThat(testGenerator.testsFor(HiddenInnerClass.class), hasSize(1));
		assertThat(testGenerator.testsFor(HiddenInnerClass.class), contains(allOf(
			containsPattern("Wrapped hidden? = new GenericObject() {*String name = \"hidden name\";*}.as(clazz(\"com.almondtools.testrecorder.scenarios.HiddenInnerClass$Hidden\"));"),
			containsPattern("HiddenInnerClass hiddenInnerClass? = new GenericObject() {*Wrapped o = hidden2;*}.as(HiddenInnerClass.class)"),
			containsPattern("new GenericMatcher() {*"
				+ "Matcher<Wrapped> o = new GenericMatcher() {*"
				+ "String name = \"hidden name\";*"
				+ "}.matching(clazz(\"com.almondtools.testrecorder.scenarios.HiddenInnerClass$Hidden\"));*"
				+ "}"
				+ ".matching(HiddenInnerClass.class));"))));
	}
}