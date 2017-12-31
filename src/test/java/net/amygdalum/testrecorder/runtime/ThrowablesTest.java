package net.amygdalum.testrecorder.runtime;

import static com.almondtools.conmatch.conventions.UtilityClassMatcher.isUtilityClass;
import static com.almondtools.conmatch.exceptions.ExceptionMatcher.matchesException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;

import org.junit.jupiter.api.Test;

import com.almondtools.conmatch.exceptions.ExceptionMatcher;

public class ThrowablesTest {

    @Test
    public void testThrowables() throws Exception {
        assertThat(Throwables.class, isUtilityClass());
    }

    @Test
    public void testCaptureWithoutResult() throws Exception {
        Throwable capture = Throwables.capture(() -> {
            try {
                throw new IllegalArgumentException("captured");
            } catch (NullPointerException e) {
                return;
            }
        });
        assertThat(capture, matchesException(IllegalArgumentException.class).withMessage("captured"));
    }

    @Test
    public void testCaptureWithoutResultUnexpected() throws Exception {
        Throwable capture = Throwables.capture(() -> {
        });
        assertThat(capture).isNull();
    }

    @Test
    public void testSpecificCaptureWithoutResult() throws Exception {
        Throwable capture = Throwables.capture(() -> {
            try {
                throw new IllegalArgumentException("captured");
            } catch (NullPointerException e) {
                return;
            }
        }, IllegalArgumentException.class);
        assertThat(capture, ExceptionMatcher.matchesException(IllegalArgumentException.class).withMessage("captured"));
    }
    
    @Test
    public void testUnexpectedCaptureWithoutResult() throws Exception {
        Throwable capture = Throwables.capture(() -> {
            try {
                throw new ArrayIndexOutOfBoundsException("captured");
            } catch (NullPointerException e) {
                return;
            }
        }, IllegalArgumentException.class);
        assertThat(capture).isNull();
    }

    @Test
    public void testCaptureWithResult() throws Exception {
        Throwable capture = Throwables.capture(() -> {
            try {
                throw new IllegalArgumentException("captured");
            } catch (NullPointerException e) {
                return "success";
            }
        });
        assertThat(capture, matchesException(IllegalArgumentException.class).withMessage("captured"));
    }

    @Test
    public void testCaptureWithResultUnexpected() throws Exception {
        Throwable capture = Throwables.capture(() -> {
            return "success";
        });
        assertThat(capture).isNull();
    }

    @Test
    public void testSpecificCaptureWithResult() throws Exception {
        Throwable capture = Throwables.capture(() -> {
            try {
                throw new IllegalArgumentException("captured");
            } catch (NullPointerException e) {
                return "success";
            }
        }, IllegalArgumentException.class);
        assertThat(capture, ExceptionMatcher.matchesException(IllegalArgumentException.class).withMessage("captured"));
    }
    
    @Test
    public void testUnexpectedCaptureWithResult() throws Exception {
        Throwable capture = Throwables.capture(() -> {
            try {
                throw new ArrayIndexOutOfBoundsException("captured");
            } catch (NullPointerException e) {
                return "success";
            }
        }, IllegalArgumentException.class);
        assertThat(capture).isNull();
    }

}
