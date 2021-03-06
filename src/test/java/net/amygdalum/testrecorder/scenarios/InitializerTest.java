package net.amygdalum.testrecorder.scenarios;

import static net.amygdalum.testrecorder.testing.assertj.TestsFail.testsFail;
import static net.amygdalum.testrecorder.testing.assertj.TestsRun.testsRun;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.amygdalum.testrecorder.TestGenerator;
import net.amygdalum.testrecorder.util.ExtensibleClassLoader;
import net.amygdalum.testrecorder.util.Instrumented;
import net.amygdalum.testrecorder.util.ServiceLoaderExtension;
import net.amygdalum.testrecorder.util.TestRecorderAgentExtension;

@ExtendWith(TestRecorderAgentExtension.class)
@Instrumented(classes = { "net.amygdalum.testrecorder.scenarios.Globals" })
public class InitializerTest {

	@Test
	@ExtendWith(ServiceLoaderExtension.class)
	public void testWithInitializer(ExtensibleClassLoader loader) throws Exception {
		loader.addPackage("net.amygdalum.testrecorder.scenarios");
		loader.defineResource("META-INF/services/net.amygdalum.testrecorder.TestRecorderAgentInitializer", "net.amygdalum.testrecorder.scenarios.GlobalsInitializer".getBytes());
		TestGenerator.fromRecorded().execute(() -> {
			Thread.currentThread().setContextClassLoader(loader);	
		}); 

		Globals.global = 0;
		new GlobalsInitializer().run();

		Globals.incGlobal();

		assertThat(Globals.getSum()).isEqualTo(43);

		TestGenerator testGenerator = TestGenerator.fromRecorded();
		assertThat(testGenerator.testsFor(Globals.class)).hasSize(2);
		assertThat(testGenerator.renderTest(Globals.class)).satisfies(testsRun());
	}

	@Test
	public void testWithoutInitializer() throws Exception {
		Globals.global = 0;
		new GlobalsInitializer().run();

		Globals.incGlobal();

		assertThat(Globals.getSum()).isEqualTo(43);

		TestGenerator testGenerator = TestGenerator.fromRecorded();
		assertThat(testGenerator.testsFor(Globals.class)).hasSize(2);
		assertThat(testGenerator.renderTest(Globals.class)).satisfies(testsFail());
	}

}
