package net.amygdalum.testrecorder.deserializers.builder;

import static net.amygdalum.extensions.assertj.Assertions.assertThat;
import static net.amygdalum.extensions.assertj.iterables.IterableConditions.containingExactly;
import static net.amygdalum.extensions.assertj.strings.StringConditions.containingWildcardPattern;
import static net.amygdalum.testrecorder.deserializers.DefaultDeserializerContext.NULL;
import static net.amygdalum.testrecorder.util.Types.parameterized;
import static net.amygdalum.testrecorder.util.testobjects.Collections.arrayList;
import static net.amygdalum.testrecorder.util.testobjects.Hidden.classOfHiddenList;
import static net.amygdalum.testrecorder.util.testobjects.Hidden.hiddenList;
import static net.amygdalum.testrecorder.values.SerializedLiteral.literal;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.amygdalum.testrecorder.deserializers.Computation;
import net.amygdalum.testrecorder.util.Types;
import net.amygdalum.testrecorder.util.testobjects.Complex;
import net.amygdalum.testrecorder.util.testobjects.ContainingList;
import net.amygdalum.testrecorder.util.testobjects.Cycle;
import net.amygdalum.testrecorder.util.testobjects.Dubble;
import net.amygdalum.testrecorder.util.testobjects.GenericCycle;
import net.amygdalum.testrecorder.util.testobjects.SerializedValues;
import net.amygdalum.testrecorder.util.testobjects.Simple;
import net.amygdalum.testrecorder.values.SerializedField;
import net.amygdalum.testrecorder.values.SerializedImmutable;
import net.amygdalum.testrecorder.values.SerializedList;
import net.amygdalum.testrecorder.values.SerializedLiteral;
import net.amygdalum.testrecorder.values.SerializedObject;

public class SetupGeneratorsTest {

	private SerializedValues values;
	private SetupGenerators setupCode;

	@BeforeEach
	public void before() throws Exception {
		values = new SerializedValues();
		setupCode = new SetupGenerators(getClass());
	}

	@Test
	public void testVisitField() throws Exception {
		Type type = parameterized(ArrayList.class, null, String.class);
		SerializedList value = values.list(type, arrayList("Foo", "Bar"));

		Computation result = setupCode.visitField(new SerializedField(ContainingList.class, "list", type, value), NULL);

		assertThat(result.getStatements().toString()).containsWildcardPattern("ArrayList<String> list1 = new ArrayList*");
		assertThat(result.getValue()).isEqualTo("ArrayList<String> list = list1;");
	}

	@Test
	public void testVisitFieldWithCastNeeded() throws Exception {
		Computation result = setupCode.visitField(new SerializedField(Complex.class, "simple", Simple.class, values.object(Object.class, new Complex())), NULL);

		assertThat(result.getStatements().toString()).containsWildcardPattern("Complex complex1 = new Complex*");
		assertThat(result.getValue()).isEqualTo("Simple simple = (Simple) complex1;");
	}

	@Test
	public void testVisitFieldWithHiddenTypeAndVisibleResult() throws Exception {
		SerializedObject value = values.object(parameterized(classOfHiddenList(), null, String.class), hiddenList("Foo", "Bar"));

		Computation result = setupCode.visitField(new SerializedField(ContainingList.class, "list", parameterized(List.class, null, String.class), value), NULL);

		assertThat(result.getStatements().toString()).containsWildcardPattern("List hiddenList2 = (List<String>) new GenericObject*.as(clazz(*HiddenList*)*.value()");
		assertThat(result.getValue()).isEqualTo("List<String> list = hiddenList2;");
	}

	@Test
	public void testVisitFieldWithHiddenTypeAndHiddenResult() throws Exception {
		SerializedObject value = values.object(parameterized(classOfHiddenList(), null, String.class), hiddenList("Foo", "Bar"));

		Computation result = setupCode.visitField(new SerializedField(ContainingList.class, "list", parameterized(classOfHiddenList(), null, String.class), value), NULL);

		assertThat(result.getStatements().toString()).containsWildcardPattern("ArrayList hiddenList2 = *(ArrayList<?>) new GenericObject*value()");
		assertThat(result.getValue()).isEqualTo("ArrayList<?> list = hiddenList2;");
	}

	@Test
	public void testVisitReferenceType() throws Exception {
		SerializedObject value = values.object(Dubble.class, new Dubble("Foo", "Bar"));

		Computation result = setupCode.visitReferenceType(value, NULL);

		assertThat(result.getStatements().toString()).contains("Dubble dubble1 = new Dubble(\"Foo\", \"Bar\");");
		assertThat(result.getValue()).isEqualTo("dubble1");
	}

	@Test
	public void testVisitReferenceTypeRevisited() throws Exception {
		SerializedObject value = values.object(Dubble.class, new Dubble("Foo", "Bar"));
		setupCode.visitReferenceType(value, NULL);

		Computation result = setupCode.visitReferenceType(value, NULL);

		assertThat(result.getStatements()).isEmpty();
		assertThat(result.getValue()).isEqualTo("dubble1");
	}

	@Test
	public void testVisitReferenceTypeForwarding() throws Exception {
		SerializedObject value = values.object(Cycle.class, Cycle.recursive("Foo"));

		Computation result = setupCode.visitReferenceType(value, NULL);

		assertThat(result.getStatements()).is(containingExactly(
			containingWildcardPattern("Cycle cycle2 = GenericObject.forward(Cycle.class)*"),
			containingWildcardPattern("GenericObject.define*")));
		assertThat(result.getValue()).isEqualTo("cycle2");
	}

	@Test
	public void testVisitReferenceTypeGenericsForwarding() throws Exception {
		SerializedObject value = values.object(Types.parameterized(GenericCycle.class, null, String.class), GenericCycle.recursive("Foo"));

		Computation result = setupCode.visitReferenceType(value, NULL);

		assertThat(result.getStatements()).is(containingExactly(
			containingWildcardPattern("GenericCycle genericCycle2 = GenericObject.forward(GenericCycle.class)*"),
			containingWildcardPattern("GenericObject.define*")));
		assertThat(result.getValue()).isEqualTo("genericCycle2");
	}

	@Test
	public void testVisitImmutableType() throws Exception {
		SerializedImmutable<BigInteger> value = values.bigInteger(BigInteger.valueOf(42));

		Computation result = setupCode.visitImmutableType(value, NULL);

		assertThat(result.getStatements()).isEmpty();
		assertThat(result.getValue()).isEqualTo("new BigInteger(\"42\")");
	}

	@Test
	public void testVisitValueType() throws Exception {
		SerializedLiteral value = literal(int.class, 42);

		Computation result = setupCode.visitValueType(value, NULL);

		assertThat(result.getStatements()).isEmpty();
		assertThat(result.getValue()).isEqualTo("42");
	}

	@Test
	public void testTemporaryLocal() throws Exception {
		assertThat(setupCode.temporaryLocal()).isEqualTo("temp1");
		assertThat(setupCode.temporaryLocal()).isEqualTo("temp2");
	}

	@Test
	public void testNewLocal() throws Exception {
		assertThat(setupCode.newLocal("var")).isEqualTo("var1");
		assertThat(setupCode.newLocal("var")).isEqualTo("var2");
	}

}
