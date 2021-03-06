package net.amygdalum.testrecorder.deserializers.builder;

import static net.amygdalum.testrecorder.deserializers.Computation.expression;
import static net.amygdalum.testrecorder.deserializers.Templates.callMethod;
import static net.amygdalum.testrecorder.deserializers.Templates.fieldAccess;
import static net.amygdalum.testrecorder.util.Literals.asLiteral;
import static net.amygdalum.testrecorder.util.Types.baseType;

import java.lang.reflect.Type;

import net.amygdalum.testrecorder.deserializers.Computation;
import net.amygdalum.testrecorder.deserializers.TypeManager;
import net.amygdalum.testrecorder.runtime.Wrapped;
import net.amygdalum.testrecorder.types.DeserializerContext;
import net.amygdalum.testrecorder.values.SerializedEnum;

public class DefaultEnumAdaptor extends DefaultSetupGenerator<SerializedEnum> implements SetupGenerator<SerializedEnum> {

	@Override
	public Class<SerializedEnum> getAdaptedClass() {
		return SerializedEnum.class;
	}

	@Override
	public boolean matches(Type type) {
		return baseType(type).isEnum();
	}

	@Override
	public Computation tryDeserialize(SerializedEnum value, SetupGenerators generator, DeserializerContext context) {
		TypeManager types = generator.getTypes();
		types.registerType(value.getType());

		if (types.isHidden(value.getType())) {
			String typeName = baseType(value.getType()).getName();
			String typeArgument = asLiteral(typeName);
			String expression = callMethod(types.getRawTypeName(Wrapped.class), "enumType", typeArgument, asLiteral(value.getName()));
			
			expression = generator.adapt(expression, value.getResultType(), value.getType());
			return expression(expression, value.getResultType());
		} else {
			String typeName = types.getVariableTypeName(value.getType());
			String name = value.getName();

			String enumConstant = fieldAccess(typeName, name);
			return expression(enumConstant, value.getResultType());
		}
	}

}
