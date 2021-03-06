package net.amygdalum.testrecorder.deserializers.builder;

import static net.amygdalum.testrecorder.deserializers.Computation.expression;
import static net.amygdalum.testrecorder.deserializers.Templates.callLocalMethod;
import static net.amygdalum.testrecorder.util.Literals.asLiteral;
import static net.amygdalum.testrecorder.util.Types.baseType;
import static net.amygdalum.testrecorder.util.Types.isLiteral;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.Optional;

import net.amygdalum.testrecorder.deserializers.Computation;
import net.amygdalum.testrecorder.deserializers.TypeManager;
import net.amygdalum.testrecorder.hints.LoadFromFile;
import net.amygdalum.testrecorder.types.DeserializationException;
import net.amygdalum.testrecorder.types.DeserializerContext;
import net.amygdalum.testrecorder.types.SerializedValue;
import net.amygdalum.testrecorder.util.FileSerializer;
import net.amygdalum.testrecorder.values.SerializedArray;
import net.amygdalum.testrecorder.values.SerializedLiteral;

public class LargePrimitiveArrayAdaptor implements SetupGenerator<SerializedArray> {

	@Override
	public Class<SerializedArray> getAdaptedClass() {
		return SerializedArray.class;
	}

	@Override
	public Class<? extends SetupGenerator<SerializedArray>> parent() {
		return DefaultArrayAdaptor.class;
	}

	@Override
	public boolean matches(Type type) {
		return true;
	}

	@Override
	public Computation tryDeserialize(SerializedArray value, SetupGenerators generator, DeserializerContext context) throws DeserializationException {
		TypeManager types = generator.getTypes();
		Class<?> componentType = baseType(value.getComponentType());
		while (componentType.isArray()) {
			componentType = componentType.getComponentType();
		}
		if (isLiteral(componentType)) {
			Optional<LoadFromFile> hint = context.getHint(LoadFromFile.class);
			if (hint.isPresent()) {
				try {
					LoadFromFile loadFromFile = hint.get();
					types.staticImport(FileSerializer.class, "load");
					Object object = unwrap(value);
					String fileName = FileSerializer.store(loadFromFile.writeTo(), object);
					String result = callLocalMethod("load", asLiteral(loadFromFile.readFrom()), asLiteral(fileName), types.getRawClass(value.getType()));
					return expression(result, value.getResultType());
				} catch (IOException e) {
					throw new DeserializationException("failed deserializing: " + value, e);
				}
			}
		}
		throw new DeserializationException("failed deserializing: " + value);
	}

	private Object unwrap(SerializedArray value) {
		SerializedValue[] serializedArray = value.getArray();
		Class<?> componentType = baseType(value.getComponentType());
		Object array = Array.newInstance(componentType, serializedArray.length);
		for (int i = 0; i < serializedArray.length; i++) {
			Array.set(array, i, unwrap(serializedArray[i]));
		}
		return array;
	}

	private Object unwrap(SerializedValue value) {
		if (value instanceof SerializedLiteral) {
			return ((SerializedLiteral) value).getValue();
		} else if (value instanceof SerializedArray) {
			return unwrap((SerializedArray) value);
		} else {
			throw new DeserializationException("failed deserializing: " + value);
		}
	}

}
