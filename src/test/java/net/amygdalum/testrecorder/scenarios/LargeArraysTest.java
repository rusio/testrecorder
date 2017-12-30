package net.amygdalum.testrecorder.scenarios;

import static net.amygdalum.testrecorder.dynamiccompile.CompilableMatcher.compiles;
import static net.amygdalum.testrecorder.dynamiccompile.TestsRunnableMatcher.testsRun;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.Timeout;

import net.amygdalum.testrecorder.TestGenerator;
import net.amygdalum.testrecorder.util.Instrumented;
import net.amygdalum.testrecorder.util.TestRecorderAgentExtension;

@ExtendWith(TestRecorderAgentExtension.class)
@Instrumented(classes = { "net.amygdalum.testrecorder.scenarios.LargeIntArrays" })
public class LargeArraysTest {

    @Rule
    public Timeout timeout = new Timeout(10, TimeUnit.SECONDS);

    

    @Test
    public void testLargeIntArraysResultAndArgumentCompilable() throws Exception {
        LargeIntArrays arrays = new LargeIntArrays();

        int[][] result = arrays.initInts(400);
        arrays.doubleInts(result);

        TestGenerator testGenerator = TestGenerator.fromRecorded();
        assertThat(testGenerator.testsFor(LargeIntArrays.class), hasSize(2));
        assertThat(testGenerator.renderTest(LargeIntArrays.class), compiles(LargeIntArrays.class));
        assertThat(testGenerator.renderTest(LargeIntArrays.class), testsRun(LargeIntArrays.class));
    }

    @Test
    public void testLargeIntArraysClassCompilable() throws Exception {
        LargeIntArrays arrays = new LargeIntArrays(100);

        arrays.sum();

        TestGenerator testGenerator = TestGenerator.fromRecorded();
        assertThat(testGenerator.testsFor(LargeIntArrays.class), hasSize(2));
        assertThat(testGenerator.renderTest(LargeIntArrays.class), compiles(LargeIntArrays.class));
        assertThat(testGenerator.renderTest(LargeIntArrays.class), testsRun(LargeIntArrays.class));
    }

}