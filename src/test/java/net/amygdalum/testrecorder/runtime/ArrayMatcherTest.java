package net.amygdalum.testrecorder.runtime;

import static com.almondtools.conmatch.strings.WildcardStringMatcher.containsPattern;
import static net.amygdalum.testrecorder.runtime.ArrayMatcher.arrayContaining;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

import org.hamcrest.StringDescription;
import org.junit.Test;

public class ArrayMatcherTest {

    @Test
    public void testDescribeTo() throws Exception {
        StringDescription description = new StringDescription();

        arrayContaining(String.class, "A", "b").describeTo(description);

        assertThat(description.toString(), equalTo("containing [<\"A\">, <\"b\">]"));
    }

    @Test
    public void testMatchesSafelyEmpty() throws Exception {
        assertThat(arrayContaining(String.class).matchesSafely(new String[] { "A", "b" }), is(false));
        assertThat(arrayContaining(String.class).matchesSafely(new String[0]), is(true));
    }

    @Test
    public void testMatchesSafelyMatchers() throws Exception {
        assertThat(arrayContaining(String.class, equalTo("A")).matchesSafely(new String[] { "A" }), is(true));
        assertThat(arrayContaining(String.class, equalTo("A")).matchesSafely(new String[] { "b" }), is(false));
        assertThat(arrayContaining(String.class, equalTo("A")).matchesSafely(new String[0]), is(false));
    }

    @Test
    public void testMatchesSafelyWithSuccess() throws Exception {
        assertThat(arrayContaining(String.class, "A", "b").matchesSafely(new String[] { "A", "b" }), is(true));
        assertThat(arrayContaining(String.class, "A", null).matchesSafely(new String[] { "A", null }), is(true));
    }

    @Test
    public void testMatchesSafelyWithFailure() throws Exception {
        assertThat(arrayContaining(String.class, "A", "b").matchesSafely(new String[] { "a", "b" }), is(false));
    }

    @Test
    public void testDescribeMismatchSafelyNotEqual() throws Exception {
        StringDescription description = new StringDescription();

        arrayContaining(String.class, "A", "b").describeMismatchSafely(new String[] { "a", "b" }, description);

        assertThat(description.toString(), containsPattern(""
            + "mismatching elements <[*"
            + "was \"a\"*"
            + "]>"));
    }

    @Test
    public void testDescribeMismatchSafelyEqual() throws Exception {
        StringDescription description = new StringDescription();

        arrayContaining(String.class, "A", "b").describeMismatchSafely(new String[] { "A", "b" }, description);

        assertThat(description.toString(), equalTo(""));
    }

    @Test
    public void testDescribeMismatchSafelyNull() throws Exception {
        StringDescription description = new StringDescription();

        arrayContaining(String.class, "A", null).describeMismatchSafely(new String[] { "A", "b" }, description);

        assertThat(description.toString(), containsPattern(""
            + "mismatching elements <[., was \"b\"]>"));
    }

    @Test
    public void testDescribeMismatchSafelyToMany() throws Exception {
        StringDescription description = new StringDescription();

        arrayContaining(String.class, "A", "b").describeMismatchSafely(new String[] { "A", "b", "c" }, description);

        assertThat(description.toString(), containsPattern(""
            + "mismatching elements <[*"
            + ".,*"
            + "found 1 elements surplus [was \"c\"]*"
            + "]>"));
    }

    @Test
    public void testDescribeMismatchSafelyToFew() throws Exception {
        StringDescription description = new StringDescription();

        arrayContaining(String.class, "A", "b").describeMismatchSafely(new String[] { "A" }, description);

        assertThat(description.toString(), containsPattern(""
            + "mismatching elements <[*"
            + ".,*"
            + "missing 1 elements*"
            + "]>"));
    }

    @Test
    public void testDescribeMismatchSafelyNullOnly() throws Exception {
        StringDescription description = new StringDescription();

        arrayContaining(String.class, nullValue()).describeMismatchSafely(new String[] { "A" }, description);

        assertThat(description.toString(), containsPattern(""
            + "mismatching elements <[was \"A\"]>"));
    }

    @Test
    public void testDescribeMismatchSafelyNullMore() throws Exception {
        StringDescription description = new StringDescription();

        arrayContaining(String.class, nullValue()).describeMismatchSafely(new String[] { null, "A" }, description);

        assertThat(description.toString(), containsPattern(""
            + "mismatching elements <[., found 1 elements surplus [was \"A\"]]>"));
    }

}