package net.amygdalum.testrecorder.deserializers.builder;

import static net.amygdalum.testrecorder.deserializers.DefaultDeserializerContext.NULL;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.amygdalum.testrecorder.deserializers.Computation;
import net.amygdalum.testrecorder.values.SerializedImmutable;

public class DefaultBigDecimalAdaptorTest {

	private DefaultBigDecimalAdaptor adaptor;

	@BeforeEach
	public void before() throws Exception {
		adaptor = new DefaultBigDecimalAdaptor();
	}

	@Test
	public void testParentNull() throws Exception {
		assertThat(adaptor.parent()).isNull();
	}

	@Test
	public void testMatchesOnlyBigDecimal() throws Exception {
		assertThat(adaptor.matches(BigDecimal.class)).isTrue();
		assertThat(adaptor.matches(Object.class)).isFalse();
	}

	@Test
	public void testTryDeserialize() throws Exception {
		SerializedImmutable<BigDecimal> value = new SerializedImmutable<>(BigDecimal.class);
		value.setValue(new BigDecimal("0.815"));
		SetupGenerators generator = new SetupGenerators(getClass());

		Computation result = adaptor.tryDeserialize(value, generator, NULL);

		assertThat(result.getStatements()).isEmpty();
		assertThat(result.getValue()).isEqualTo("new BigDecimal(\"0.815\")");
	}

}
