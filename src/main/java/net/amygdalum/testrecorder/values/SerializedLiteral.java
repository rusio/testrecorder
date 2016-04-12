package net.amygdalum.testrecorder.values;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.amygdalum.testrecorder.Deserializer;
import net.amygdalum.testrecorder.SerializedValueType;
import net.amygdalum.testrecorder.deserializers.ValuePrinter;

/**
 * Serializing to SerializedLiteral is only valid for primitive types and non-null Strings. For this use the factory method 
 * {@link #literal(Type, Object)}
 */
public class SerializedLiteral implements SerializedValueType {

	public static Set<Class<?>> LITERAL_TYPES = new HashSet<>(Arrays.asList(
		boolean.class, char.class, byte.class, short.class, int.class, float.class, long.class, double.class,
		Boolean.class, Character.class, Byte.class, Short.class, Integer.class, Float.class, Long.class, Double.class,
		String.class));

	private static final Map<Object, SerializedLiteral> KNOWN_LITERALS = new HashMap<>();

	private Type type;
	private Object value;

	private SerializedLiteral(Type type, Object value) {
		this.type = type;
		this.value = value;
	}

	public static boolean isLiteral(Type type) {
		return LITERAL_TYPES.contains(type);
	}

	public static SerializedLiteral literal(Type type, Object value) {
		return KNOWN_LITERALS.computeIfAbsent(value, val -> new SerializedLiteral(type, val));
	}

	@Override
	public Type getResultType() {
		return type;
	}
	
	@Override
	public void setType(Type type) {
		this.type = type;
	}
	
	@Override
	public Class<?> getType() {
		return value.getClass();
	}

	@Override
	public Object getValue() {
		return value;
	}

	@Override
	public <T> T accept(Deserializer<T> visitor) {
		return visitor.visitValueType(this);
	}

	@Override
	public String toString() {
		return accept(new ValuePrinter());
	}

}