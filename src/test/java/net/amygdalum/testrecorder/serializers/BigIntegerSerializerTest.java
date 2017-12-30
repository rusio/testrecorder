package net.amygdalum.testrecorder.serializers;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.amygdalum.testrecorder.types.Serializer;
import net.amygdalum.testrecorder.values.SerializedImmutable;


public class BigIntegerSerializerTest {

	private SerializerFacade facade;
	private Serializer<SerializedImmutable<BigInteger>> serializer;

	@BeforeEach
	public void before() throws Exception {
		facade = mock(SerializerFacade.class);
		serializer = new BigIntegerSerializer.Factory().newSerializer(facade);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetMatchingClasses() throws Exception {
		assertThat(serializer.getMatchingClasses(), contains(BigInteger.class));
	}

	@Test
	public void testGenerate() throws Exception {
		SerializedImmutable<BigInteger> value = serializer.generate(BigInteger.class, BigInteger.class);

		assertThat(value.getResultType(), equalTo(BigInteger.class));
		assertThat(value.getType(), equalTo(BigInteger.class));
	}

	@Test
	public void testPopulate() throws Exception {
		SerializedImmutable<BigInteger> value = serializer.generate(BigInteger.class, BigInteger.class);

		serializer.populate(value, BigInteger.valueOf(22));

		assertThat(value.getValue(), equalTo(BigInteger.valueOf(22)));
	}

}
