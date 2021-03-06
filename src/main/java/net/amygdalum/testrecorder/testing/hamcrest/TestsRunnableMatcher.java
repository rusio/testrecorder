package net.amygdalum.testrecorder.testing.hamcrest;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import net.amygdalum.testrecorder.dynamiccompile.DynamicClassCompiler;
import net.amygdalum.testrecorder.dynamiccompile.DynamicClassCompilerException;
import net.amygdalum.testrecorder.dynamiccompile.RenderedTest;
import net.amygdalum.testrecorder.util.Instantiations;

public class TestsRunnableMatcher extends TypeSafeDiagnosingMatcher<RenderedTest> {

    private DynamicClassCompiler compiler;

    public TestsRunnableMatcher() {
        compiler = new DynamicClassCompiler();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("should compile and run with success");
    }

    @Override
    protected boolean matchesSafely(RenderedTest test, Description mismatchDescription) {
        try {
            Class<?> clazz = compiler.compile(test.getTestCode(), test.getTestClassLoader());
            JUnitCore junit = new JUnitCore();
            Instantiations.resetInstatiations();
            Result result = junit.run(clazz);
            if (result.wasSuccessful()) {
                return true;
            }
            mismatchDescription.appendText("compiled successfully but got test failures : " + result.getFailureCount());
            for (Failure failure : result.getFailures()) {
                StringBuilder message = new StringBuilder("\n-\t").append(failure.getMessage());
                Throwable cause = failure.getException();
                while (cause != null) {
                	message.append("\n\t").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
                	cause = cause.getCause() == cause ? null : cause.getCause();
                }
                mismatchDescription.appendText(message.toString());
            }
            return false;
        } catch (DynamicClassCompilerException e) {
            mismatchDescription.appendText(e.getMessage());
            for (String msg : e.getDetailMessages()) {
                mismatchDescription.appendText("\n\t" + msg);
            }
            return false;
        }
    }

    public static TestsRunnableMatcher testsRun() {
        return new TestsRunnableMatcher();
    }

}
