package net.amygdalum.testrecorder.values;

import static net.amygdalum.testrecorder.values.ParameterizedTypeMatcher.parameterized;
import static net.amygdalum.testrecorder.values.SerializedLiteral.literal;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.arrayContaining;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.junit.Test;

import net.amygdalum.testrecorder.deserializers.TestValueVisitor;

public class SerializedArrayTest {

	@SuppressWarnings("unused")
	private List<String>[] genericArray = null;
	
	@Test
		public void testGetResultType() throws Exception {
			SerializedArray array = new SerializedArray(String[].class).with(literal(String.class, "s1"), literal(String.class, "s2"));
			
			assertThat(array.getResultType(), equalTo(String[].class));
		}

	@Test
	public void testGetComponentType() throws Exception {
		SerializedArray array = new SerializedArray(String[].class).with(literal(String.class, "s1"), literal(String.class, "s2"));
		assertThat(array.getComponentType(), equalTo(String.class));
	}

	@Test
	public void testGetComponentTypeOnGenericArray() throws Exception {
		SerializedArray array = new SerializedArray(SerializedArrayTest.class.getDeclaredField("genericArray").getGenericType());
		assertThat(array.getComponentType(), parameterized(List.class, String.class));
	}

	@Test
	public void testGetComponentTypeOnRuntimeGenericArray() throws Exception {
		SerializedArray array = new SerializedArray(Void.class);
		
		assertThat(array.getComponentType(), equalTo(Object.class));
	}

	@Test
	public void testGetArray() throws Exception {
		SerializedArray array = new SerializedArray(String[].class).with(literal(String.class, "s1"), literal(String.class, "s2"));

		assertThat(array.getArray(), arrayContaining(literal(String.class, "s1"), literal(String.class, "s2")));
	}

	@Test
	public void testAccept() throws Exception {
		SerializedArray array = new SerializedArray(String[].class);
		
		assertThat(array.accept(new TestValueVisitor()), equalTo("SerializedArray"));
	}

	@Test
	public void testAdd() throws Exception {
		SerializedArray array = new SerializedArray(String[].class);
		array.add(literal(String.class, "s"));
		
		assertThat(array.getArray(), arrayContaining(literal(String.class, "s")));
	}

	@Test
	public void testToString() throws Exception {
		SerializedArray array = new SerializedArray(String[].class).with(literal(String.class, "s1"), literal(String.class, "s2"));

		assertThat(array.toString(), equalTo("<s1, s2>"));
	}

}