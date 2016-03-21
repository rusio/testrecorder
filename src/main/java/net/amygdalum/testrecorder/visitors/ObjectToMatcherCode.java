package net.amygdalum.testrecorder.visitors;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static net.amygdalum.testrecorder.visitors.Templates.arrayContainingMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.arrayEmptyMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.asLiteral;
import static net.amygdalum.testrecorder.visitors.Templates.assignLocalVariableStatement;
import static net.amygdalum.testrecorder.visitors.Templates.containsEntriesMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.containsInAnyOrderMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.containsMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.emptyMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.equalToMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.genericObjectMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.newObject;
import static net.amygdalum.testrecorder.visitors.Templates.noEntriesMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.nullMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.primitiveArrayContainingMatcher;
import static net.amygdalum.testrecorder.visitors.Templates.recursiveMatcher;
import static net.amygdalum.testrecorder.visitors.TypeManager.getArgument;
import static net.amygdalum.testrecorder.visitors.TypeManager.getBase;
import static net.amygdalum.testrecorder.visitors.TypeManager.isPrimitive;
import static net.amygdalum.testrecorder.visitors.TypeManager.parameterized;
import static net.amygdalum.testrecorder.visitors.TypeManager.wildcard;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.collection.IsArrayContainingInOrder;
import org.hamcrest.collection.IsArrayWithSize;

import com.almondtools.conmatch.datatypes.MapMatcher;
import com.almondtools.conmatch.datatypes.PrimitiveArrayMatcher;

import net.amygdalum.testrecorder.SerializedCollectionVisitor;
import net.amygdalum.testrecorder.SerializedImmutableVisitor;
import net.amygdalum.testrecorder.SerializedValue;
import net.amygdalum.testrecorder.SerializedValueVisitor;
import net.amygdalum.testrecorder.util.GenericMatcher;
import net.amygdalum.testrecorder.values.SerializedArray;
import net.amygdalum.testrecorder.values.SerializedBigDecimal;
import net.amygdalum.testrecorder.values.SerializedBigInteger;
import net.amygdalum.testrecorder.values.SerializedField;
import net.amygdalum.testrecorder.values.SerializedList;
import net.amygdalum.testrecorder.values.SerializedLiteral;
import net.amygdalum.testrecorder.values.SerializedMap;
import net.amygdalum.testrecorder.values.SerializedNull;
import net.amygdalum.testrecorder.values.SerializedObject;
import net.amygdalum.testrecorder.values.SerializedSet;

public class ObjectToMatcherCode implements SerializedValueVisitor<Computation>, SerializedCollectionVisitor<Computation>, SerializedImmutableVisitor<Computation> {

	private Set<SerializedValue> computed;

	private TypeManager types;

	public ObjectToMatcherCode() {
		this(new LocalVariableNameGenerator(), new TypeManager());
	}

	public ObjectToMatcherCode(LocalVariableNameGenerator locals, TypeManager types) {
		this.types = types;
		this.computed = new HashSet<>();
	}

	public TypeManager getTypes() {
		return types;
	}

	@Override
	public Computation visitField(SerializedField field) {
		SerializedValue fieldValue = field.getValue();
		if (isSimpleValue(fieldValue)) {
			types.registerImport(getBase(field.getType()));
			Computation value = getSimpleValue(fieldValue);

			String assignField = assignLocalVariableStatement(types.getRawName(field.getType()), field.getName(), value.getValue());
			return new Computation(assignField, value.getStatements());
		} else {
			types.registerImport(Matcher.class);
			Computation value = fieldValue.accept(this);

			String genericType = types.getSimpleName(value.getType());

			String assignField = assignLocalVariableStatement(genericType, field.getName(), value.getValue());
			return new Computation(assignField, value.getStatements());
		}
	}

	@Override
	public Computation visitObject(SerializedObject value) {
		if (!computed.add(value)) {
			types.staticImport(GenericMatcher.class, "recursive");
			Type resultType = value.getType().equals(value.getValueType()) ? parameterized(Matcher.class, null, value.getType()) : parameterized(Matcher.class, null, wildcard());
			return new Computation(recursiveMatcher(types.getRawTypeName(value.getValueType())), resultType);
		}
		types.registerTypes(value.getType(), value.getValueType(), GenericMatcher.class);

		List<Computation> fields = value.getFields().stream()
			.sorted()
			.map(field -> field.accept(this))
			.collect(toList());

		List<String> fieldComputations = fields.stream()
			.flatMap(field -> field.getStatements().stream())
			.collect(toList());

		List<String> fieldAssignments = fields.stream()
			.map(field -> field.getValue())
			.collect(toList());

		Type resultType = parameterized(Matcher.class, null, value.getType());

		String matcherExpression = createMatcherExpression(value, fieldAssignments);

		return new Computation(matcherExpression, resultType, fieldComputations);
	}

	public String createMatcherExpression(SerializedObject value, List<String> fieldAssignments) {
		Type type = value.getType();
		if (getBase(type) == Matcher.class) {
			type = getArgument(type, 0);
		}
		Class<?> valueType = value.getValueType();
		if (type.equals(valueType)) {
			String matcherRawType = types.getRawTypeName(valueType);
			return genericObjectMatcher(matcherRawType, fieldAssignments);
		} else {
			String matcherRawType = types.getRawTypeName(valueType);
			String matcherToType = types.getRawTypeName(type);
			return genericObjectMatcher(matcherRawType, matcherToType, fieldAssignments);
		}
	}

	@Override
	public Computation visitList(SerializedList value) {
		if (!computed.add(value)) {
			types.staticImport(GenericMatcher.class, "recursive");
			types.registerImport(value.getValueType());
			return new Computation(recursiveMatcher(types.getRawTypeName(value.getValueType())), parameterized(Matcher.class, null, wildcard()));
		}
		if (value.isEmpty()) {
			types.staticImport(Matchers.class, "empty");

			return new Computation(emptyMatcher(), parameterized(Matcher.class, null, wildcard()), emptyList());
		} else {
			types.staticImport(Matchers.class, "contains");

			List<Computation> elements = value.stream()
				.map(element -> getSimpleValue(element))
				.collect(toList());

			List<String> elementComputations = elements.stream()
				.flatMap(element -> element.getStatements().stream())
				.collect(toList());

			String[] elementValues = elements.stream()
				.map(element -> element.getValue())
				.toArray(String[]::new);

			String containsMatcher = containsMatcher(elementValues);

			return new Computation(containsMatcher, parameterized(Matcher.class, null, wildcard()), elementComputations);
		}
	}

	@Override
	public Computation visitSet(SerializedSet value) {
		if (!computed.add(value)) {
			types.staticImport(GenericMatcher.class, "recursive");
			types.registerImport(value.getValueType());
			return new Computation(recursiveMatcher(types.getRawTypeName(value.getValueType())), parameterized(Matcher.class, null, wildcard()));
		}
		if (value.isEmpty()) {
			types.staticImport(Matchers.class, "empty");

			String emptyMatcher = emptyMatcher();
			return new Computation(emptyMatcher, parameterized(Matcher.class, null, wildcard()), emptyList());
		} else {
			types.staticImport(Matchers.class, "containsInAnyOrder");

			List<Computation> elements = value.stream()
				.map(element -> getSimpleValue(element))
				.collect(toList());

			List<String> elementComputations = elements.stream()
				.flatMap(element -> element.getStatements().stream())
				.collect(toList());

			String[] elementValues = elements.stream()
				.map(element -> element.getValue())
				.toArray(String[]::new);

			String containsInAnyOrderMatcher = containsInAnyOrderMatcher(elementValues);
			return new Computation(containsInAnyOrderMatcher, parameterized(Matcher.class, null, wildcard()), elementComputations);
		}
	}

	@Override
	public Computation visitMap(SerializedMap value) {
		if (!computed.add(value)) {
			types.staticImport(GenericMatcher.class, "recursive");
			types.registerImport(value.getValueType());
			return new Computation(recursiveMatcher(types.getRawTypeName(value.getValueType())), parameterized(Matcher.class, null, wildcard()));
		}
		String keyType = types.getSimpleName(value.getMapKeyType());
		String valueType = types.getSimpleName(value.getMapValueType());
		if (value.isEmpty()) {
			types.staticImport(MapMatcher.class, "noEntries");

			String noEntriesMatcher = noEntriesMatcher(keyType, valueType);

			return new Computation(noEntriesMatcher, parameterized(Matcher.class, null, wildcard()), emptyList());
		} else {
			types.staticImport(MapMatcher.class, "containsEntries");

			Map<Computation, Computation> elements = value.entrySet().stream()
				.collect(toMap(entry -> getSimpleValue(entry.getKey()), entry -> getSimpleValue(entry.getValue())));

			List<String> entryComputations = elements.entrySet().stream()
				.flatMap(entry -> Stream.concat(entry.getKey().getStatements().stream(), entry.getValue().getStatements().stream()))
				.collect(toList());

			Set<Entry<String, String>> entryValues = elements.entrySet().stream()
				.collect(toMap(entry -> entry.getKey().getValue(), entry -> entry.getValue().getValue()))
				.entrySet();

			String containsEntriesMatcher = containsEntriesMatcher(keyType, valueType, entryValues);
			return new Computation(containsEntriesMatcher, parameterized(Matcher.class, null, wildcard()), entryComputations);
		}
	}

	@Override
	public Computation visitArray(SerializedArray value) {
		if (!computed.add(value)) {
			types.staticImport(GenericMatcher.class, "recursive");
			types.registerImport(value.getValueType());
			return new Computation(recursiveMatcher(types.getRawName(value.getValueType())), parameterized(Matcher.class, null, wildcard()));
		}
		if (isPrimitive(value.getComponentType())) {
			String name = value.getComponentType().getTypeName();
			types.staticImport(PrimitiveArrayMatcher.class, name + "ArrayContaining");

			List<Computation> elements = Stream.of(value.getArray())
				.map(element -> getSimpleValue(element))
				.collect(toList());

			List<String> elementComputations = elements.stream()
				.flatMap(element -> element.getStatements().stream())
				.collect(toList());

			String[] elementValues = elements.stream()
				.map(element -> element.getValue())
				.toArray(String[]::new);

			String primitiveArrayContainingMatcher = primitiveArrayContainingMatcher(name, elementValues);
			return new Computation(primitiveArrayContainingMatcher, parameterized(Matcher.class, null, wildcard()), elementComputations);
		} else {
			if (value.getArrayAsList().isEmpty()) {
				types.staticImport(IsArrayWithSize.class, "emptyArray");

				String arrayEmptyMatcher = arrayEmptyMatcher();
				return new Computation(arrayEmptyMatcher, parameterized(Matcher.class, null, wildcard()));
			} else {
				types.staticImport(IsArrayContainingInOrder.class, "arrayContaining");

				List<Computation> elements = Stream.of(value.getArray())
					.map(element -> getSimpleValue(element))
					.collect(toList());

				List<String> elementComputations = elements.stream()
					.flatMap(element -> element.getStatements().stream())
					.collect(toList());

				String[] elementValues = elements.stream()
					.map(element -> element.getValue())
					.toArray(String[]::new);

				String arrayContainingMatcher = arrayContainingMatcher(elementValues);
				return new Computation(arrayContainingMatcher, parameterized(Matcher.class, null, wildcard()), elementComputations);
			}
		}

	}

	@Override
	public Computation visitLiteral(SerializedLiteral value) {
		types.staticImport(Matchers.class, "equalTo");

		String valueExpression = asLiteral(value.getValue());

		String equalToMatcher = equalToMatcher(valueExpression);
		return new Computation(equalToMatcher, parameterized(Matcher.class, null, value.getValueType()), emptyList());
	}

	@Override
	public Computation visitNull(SerializedNull value) {
		types.registerImport(value.getValueType());
		types.staticImport(Matchers.class, "nullValue");

		String nullMatcher = nullMatcher(types.getRawName(value.getValueType()));
		return new Computation(nullMatcher, parameterized(Matcher.class, null, value.getValueType()), emptyList());
	}

	@Override
	public Computation visitBigDecimal(SerializedBigDecimal value) {
		types.registerImport(BigDecimal.class);
		types.staticImport(Matchers.class, "equalTo");

		String literal = asLiteral(value.getValue().toPlainString());

		String bigDecimalLiteral = newObject("BigDecimal", literal);

		String equalToMatcher = equalToMatcher(bigDecimalLiteral);
		return new Computation(equalToMatcher, parameterized(Matcher.class, null, value.getValueType()), emptyList());
	}

	@Override
	public Computation visitBigInteger(SerializedBigInteger value) {
		types.registerImport(BigInteger.class);
		types.staticImport(Matchers.class, "equalTo");

		String literal = asLiteral(value.getValue().toString());

		String bigIntegerLiteral = newObject("BigInteger", literal);

		String equalToMatcher = equalToMatcher(bigIntegerLiteral);
		return new Computation(equalToMatcher, parameterized(Matcher.class, null, value.getValueType()), emptyList());
	}

	@Override
	public Computation visitUnknown(SerializedValue value) {
		return Computation.NULL;
	}

	private boolean isSimpleValue(SerializedValue element) {
		return element instanceof SerializedNull
			|| element instanceof SerializedLiteral;
	}

	private Computation getSimpleValue(SerializedValue element) {
		if (element instanceof SerializedNull) {
			return new Computation("null");
		} else if (element instanceof SerializedLiteral) {
			return new Computation(asLiteral(((SerializedLiteral) element).getValue()));
		} else {
			return element.accept(this);
		}
	}

	public static class Factory implements SerializedValueVisitorFactory {

		@Override
		public SerializedValueVisitor<Computation> create(LocalVariableNameGenerator locals, TypeManager types) {
			return new ObjectToMatcherCode(locals, types);
		}

		@Override
		public Type resultType(Type type) {
			return parameterized(Matcher.class, null, type);
		}
	}

}