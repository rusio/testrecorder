package com.almondtools.testrecorder.scenarios;

import static com.almondtools.conmatch.strings.WildcardStringMatcher.containsPattern;
import static com.almondtools.testrecorder.dynamiccompile.CompilableMatcher.compiles;
import static com.almondtools.testrecorder.dynamiccompile.TestsRunnableMatcher.testsRuns;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.almondtools.testrecorder.ConfigRegistry;
import com.almondtools.testrecorder.DefaultConfig;
import com.almondtools.testrecorder.SnapshotInstrumentor;
import com.almondtools.testrecorder.TestGenerator;

public class ClassicBeanTest {

	private static SnapshotInstrumentor instrumentor;

	@BeforeClass
	public static void beforeClass() throws Exception {
		instrumentor = new SnapshotInstrumentor(new DefaultConfig());
		instrumentor.register("com.almondtools.testrecorder.scenarios.ClassicBean");
	}
	
	@Before
	public void before() throws Exception {
		((TestGenerator) ConfigRegistry.loadConfig(DefaultConfig.class).getMethodConsumer()).clearResults();
	}
	
	@Test
	public void testCompilable() throws Exception {
		ClassicBean bean = new ClassicBean();
		bean.setI(22);
		bean.setO(new ClassicBean());
		
		assertThat(bean.hashCode(), equalTo(191));

		TestGenerator testGenerator = TestGenerator.fromRecorded(bean);
		assertThat(testGenerator.renderTest(ClassicBean.class), compiles());
		assertThat(testGenerator.renderTest(ClassicBean.class), testsRuns());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCode() throws Exception {
		ClassicBean bean = new ClassicBean();
		bean.setI(22);
		bean.setO(new ClassicBean());
		
		assertThat(bean.hashCode(), equalTo(191));

		TestGenerator testGenerator = TestGenerator.fromRecorded(bean);
		assertThat(testGenerator.testsFor(ClassicBean.class), hasSize(2));
		assertThat(testGenerator.testsFor(ClassicBean.class), containsInAnyOrder(
			allOf(containsString("new ClassicBean()"), not(containsPattern("classicBean?.set")), containsString("equalTo(13)")), 
			allOf(containsPattern("classicBean?.setI(22)"), containsPattern("classicBean?.setO(classicBean?)"), containsString("equalTo(191)"))));
	}
}