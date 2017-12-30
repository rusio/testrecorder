package net.amygdalum.testrecorder.serializers;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.amygdalum.testrecorder.types.Serializer;
import net.amygdalum.testrecorder.values.SerializedEnum;


public class EnumSerializerTest {

	private SerializerFacade facade;
	private Serializer<SerializedEnum> serializer;

	@BeforeEach
	public void before() throws Exception {
		facade = mock(SerializerFacade.class);
		serializer = new EnumSerializer(facade);
	}

	@Test
	public void testGetMatchingClasses() throws Exception {
		assertThat(serializer.getMatchingClasses(), empty());
	}

	@Test
	public void testGenerate() throws Exception {
		SerializedEnum value = serializer.generate(MyInterface.class, MyEnum.class);

		assertThat(value.getResultType(), equalTo(MyInterface.class));
		assertThat(value.getType(), equalTo(MyEnum.class));
	}

	@Test
	public void testGenerateWithExtendedEnum() throws Exception {
		SerializedEnum value = serializer.generate(MyInterface.class, ExtendedEnum.VALUE1.getClass());

		assertThat(value.getResultType(), equalTo(MyInterface.class));
		assertThat(value.getType(), equalTo(ExtendedEnum.class));
	}

	@Test
	public void testPopulate() throws Exception {
		SerializedEnum value = serializer.generate(MyInterface.class, MyEnum.class);

		serializer.populate(value, MyEnum.VALUE1);

		assertThat(value.getName(), equalTo("VALUE1"));
	}

	interface MyInterface {
		
	}
	
	private static enum MyEnum implements MyInterface {
		VALUE1, VALUE2;
	}

	private static enum ExtendedEnum {
		VALUE1 {};
	}
}
