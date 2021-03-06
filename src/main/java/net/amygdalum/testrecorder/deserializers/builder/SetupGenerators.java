package net.amygdalum.testrecorder.deserializers.builder;

import static net.amygdalum.testrecorder.deserializers.Computation.expression;
import static net.amygdalum.testrecorder.deserializers.Computation.variable;
import static net.amygdalum.testrecorder.deserializers.Templates.assignLocalVariableStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.callMethod;
import static net.amygdalum.testrecorder.deserializers.Templates.cast;
import static net.amygdalum.testrecorder.util.Types.assignableTypes;
import static net.amygdalum.testrecorder.util.Types.baseType;
import static net.amygdalum.testrecorder.util.Types.boxingEquivalentTypes;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.amygdalum.testrecorder.deserializers.Adaptors;
import net.amygdalum.testrecorder.deserializers.Computation;
import net.amygdalum.testrecorder.deserializers.DeserializerFactory;
import net.amygdalum.testrecorder.deserializers.LocalVariable;
import net.amygdalum.testrecorder.deserializers.LocalVariableDefinition;
import net.amygdalum.testrecorder.deserializers.LocalVariableNameGenerator;
import net.amygdalum.testrecorder.deserializers.TypeManager;
import net.amygdalum.testrecorder.runtime.GenericObject;
import net.amygdalum.testrecorder.runtime.Wrapped;
import net.amygdalum.testrecorder.types.DeserializationException;
import net.amygdalum.testrecorder.types.Deserializer;
import net.amygdalum.testrecorder.types.DeserializerContext;
import net.amygdalum.testrecorder.types.SerializedFieldType;
import net.amygdalum.testrecorder.types.SerializedImmutableType;
import net.amygdalum.testrecorder.types.SerializedReferenceType;
import net.amygdalum.testrecorder.types.SerializedValue;
import net.amygdalum.testrecorder.types.SerializedValueType;

public class SetupGenerators implements Deserializer<Computation> {

	public static final Adaptors<SetupGenerators> DEFAULT = new Adaptors<SetupGenerators>()
		.load(SetupGenerator.class);

	private LocalVariableNameGenerator locals;
	private TypeManager types;
	private Adaptors<SetupGenerators> adaptors;

	private Map<SerializedValue, LocalVariable> defined;

	public SetupGenerators(Class<?> clazz) {
		this(new LocalVariableNameGenerator(), new TypeManager(clazz.getPackage().getName()));
	}

	public SetupGenerators(LocalVariableNameGenerator locals, TypeManager types) {
		this(locals, types, DEFAULT);
	}

	public SetupGenerators(LocalVariableNameGenerator locals, TypeManager types, Adaptors<SetupGenerators> adaptors) {
		this.types = types;
		this.locals = locals;
		this.adaptors = adaptors;
		this.defined = new IdentityHashMap<>();
	}

	public String adapt(String expression, Type resultType, Type type) {
		if (baseType(resultType) != Wrapped.class && type == Wrapped.class) {
			expression = callMethod(expression, "value");
			type = Object.class;
		} else if (baseType(resultType) != Wrapped.class && types.isHidden(type)) {
			expression = callMethod(expression, "value");
			type = Object.class;
		}
		if ((!assignableTypes(resultType, type) || types.isHidden(type))
			&& !boxingEquivalentTypes(resultType, type)
			&& baseType(resultType) != Wrapped.class) {
			expression = cast(types.getVariableTypeName(resultType), expression);
		}
		return expression;
	}

	public boolean needsAdaptation(Type resultType, Type type) {
		if (baseType(resultType) != Wrapped.class && type == Wrapped.class) {
			return true;
		} else if (baseType(resultType) != Wrapped.class && types.isHidden(type)) {
			return true;
		}
		if ((!assignableTypes(resultType, type) || types.isHidden(type))
			&& !boxingEquivalentTypes(resultType, type)
			&& baseType(resultType) != Wrapped.class) {
			return true;
		}
		return false;
	}

	public TypeManager getTypes() {
		return types;
	}

	public Computation forVariable(SerializedValue value, Type type, LocalVariableDefinition computation) {
		LocalVariable local = localVariable(value, type, value.getResultType());
		try {
			Computation definition = computation.define(local);
			finishVariable(value);
			return definition;
		} catch (DeserializationException e) {
			resetVariable(value);
			throw e;
		}
	}

	public String temporaryLocal() {
		return locals.fetchName("temp");
	}

	public String newLocal(String name) {
		return locals.fetchName(name);
	}

	private LocalVariable localVariable(SerializedValue value, Type type, Type resultType) {
		String name = locals.fetchName(type);
		LocalVariable definition = new LocalVariable(name, resultType);
		defined.put(value, definition);
		return definition;
	}

	private void finishVariable(SerializedValue value) {
		defined.computeIfPresent(value, (val, def) -> def.finish());
	}

	private void resetVariable(SerializedValue value) {
		defined.remove(value);
	}

	@Override
	public Computation visitField(SerializedFieldType field, DeserializerContext context) {
		Type fieldType = field.getType();
		Type resultType = field.getValue().getResultType();
		Type fieldResultType = types.bestType(fieldType, Object.class);
		types.registerTypes(fieldType, resultType, fieldResultType);

		SerializedValue value = field.getValue();
		if (value instanceof SerializedReferenceType) {
			((SerializedReferenceType) value).setResultType(fieldResultType);
		}
		Computation valueTemplate = value.accept(this, context.newWithHints(field.getAnnotations()));

		List<String> statements = valueTemplate.getStatements();

		String expression = valueTemplate.getValue();

		expression = adapt(expression, fieldResultType, valueTemplate.getType());

		String assignField = assignLocalVariableStatement(types.getVariableTypeName(fieldResultType), field.getName(), expression);
		return expression(assignField, null, statements);
	}

	@Override
	public Computation visitReferenceType(SerializedReferenceType value, DeserializerContext context) {
		if (defined.containsKey(value)) {
			LocalVariable definition = defined.get(value);
			String name = definition.getName();
			if (definition.isDefined()) {
				return variable(name, definition.getType());
			} else {
				List<String> statements = new ArrayList<>();
				String forwardExpression = callMethod(types.getVariableTypeName(GenericObject.class), "forward", types.getRawClass(value.getType()));
				Type resultType = types.wrapHidden(value.getType());
				statements.add(assignLocalVariableStatement(types.getRawTypeName(resultType), name, forwardExpression));
				definition.define(resultType);
				return variable(name, resultType, statements);
			}
		}
		return adaptors.tryDeserialize(value, types, this, context);
	}

	@Override
	public Computation visitValueType(SerializedValueType value, DeserializerContext context) {
		return adaptors.tryDeserialize(value, types, this, context);
	}

	@Override
	public Computation visitImmutableType(SerializedImmutableType value, DeserializerContext context) {
		return adaptors.tryDeserialize(value, types, this, context);
	}

	public static class Factory implements DeserializerFactory {

		@Override
		public SetupGenerators create(LocalVariableNameGenerator locals, TypeManager types) {
			return new SetupGenerators(locals, types);
		}

		@Override
		public Type resultType(Type type) {
			return type;
		}
	}

}
