package net.amygdalum.testrecorder.deserializers.matcher;

import static java.util.Collections.emptyList;
import static net.amygdalum.testrecorder.deserializers.Computation.expression;
import static net.amygdalum.testrecorder.deserializers.Templates.enumMatcher;
import static net.amygdalum.testrecorder.deserializers.Templates.fieldAccess;
import static net.amygdalum.testrecorder.deserializers.Templates.sameInstanceMatcher;
import static net.amygdalum.testrecorder.deserializers.Templates.widening;
import static net.amygdalum.testrecorder.util.Literals.asLiteral;
import static net.amygdalum.testrecorder.util.Types.baseType;
import static net.amygdalum.testrecorder.util.Types.parameterized;
import static net.amygdalum.testrecorder.util.Types.wildcardExtends;

import java.lang.reflect.Type;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import net.amygdalum.testrecorder.deserializers.Computation;
import net.amygdalum.testrecorder.deserializers.TypeManager;
import net.amygdalum.testrecorder.testing.hamcrest.EnumMatcher;
import net.amygdalum.testrecorder.testing.hamcrest.WideningMatcher;
import net.amygdalum.testrecorder.types.DeserializerContext;
import net.amygdalum.testrecorder.values.SerializedEnum;

public class DefaultEnumAdaptor extends DefaultMatcherGenerator<SerializedEnum> implements MatcherGenerator<SerializedEnum> {
	
	@Override
	public Class<SerializedEnum> getAdaptedClass() {
		return SerializedEnum.class;
	}

	@Override
	public boolean matches(Type type) {
		return baseType(type).isEnum();
	}

	@Override
	public Computation tryDeserialize(SerializedEnum value, MatcherGenerators generator, DeserializerContext context) {
		TypeManager types = generator.getTypes();
		Type type = value.getType();
        types.registerType(type);

		if (types.isHidden(type)) {
			if (!Enum.class.isAssignableFrom(baseType(value.getResultType()))) {
				types.staticImport(WideningMatcher.class, "widening");
			}
			types.staticImport(EnumMatcher.class, "matchingEnum");
		} else {
			types.staticImport(Matchers.class, "sameInstance");
		}

		if (types.isHidden(type)) {
			String enumMatcher = enumMatcher(asLiteral(value.getName()));
			if (!Enum.class.isAssignableFrom(baseType(value.getResultType()))) {
				enumMatcher = widening(enumMatcher); 
			}
			return expression(enumMatcher, parameterized(Matcher.class, null, wildcardExtends(Enum.class)), emptyList());
		} else {
			String typeName = types.getVariableTypeName(type);
			String name = value.getName();

			String matchingValue = fieldAccess(typeName, name);
			String enumMatcher = sameInstanceMatcher(matchingValue);
			return expression(enumMatcher, parameterized(Matcher.class, null, type), emptyList());
		}
	}

}
