package net.amygdalum.testrecorder.deserializers.builder;

import static java.util.stream.Collectors.toList;
import static net.amygdalum.testrecorder.deserializers.Computation.variable;
import static net.amygdalum.testrecorder.deserializers.Templates.assignLocalVariableStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.callMethodStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.newObject;
import static net.amygdalum.testrecorder.util.Types.baseType;
import static net.amygdalum.testrecorder.util.Types.equalTypes;
import static net.amygdalum.testrecorder.util.Types.typeArgument;
import static net.amygdalum.testrecorder.util.Types.typeArguments;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import net.amygdalum.testrecorder.deserializers.Computation;
import net.amygdalum.testrecorder.deserializers.TypeManager;
import net.amygdalum.testrecorder.types.DeserializerContext;
import net.amygdalum.testrecorder.types.SerializedReferenceType;
import net.amygdalum.testrecorder.types.SerializedValue;
import net.amygdalum.testrecorder.util.Types;

public abstract class DefaultGenericCollectionAdaptor<T extends SerializedReferenceType> extends DefaultSetupGenerator<T> implements SetupGenerator<T> {

    public abstract Class<?>[] matchingTypes();

    public abstract Type componentType(T value);

    public abstract Stream<SerializedValue> elements(T value);

    @Override
    public boolean matches(Type type) {
        return matchType(type).isPresent();
    }

    public Optional<Class<?>> matchType(Type type) {
        return Stream.of(matchingTypes())
            .filter(clazz -> clazz.isAssignableFrom(baseType(type)))
            .findFirst();
    }

    @Override
    public Computation tryDeserialize(T value, SetupGenerators generator, DeserializerContext context) {

        Type type = value.getType();
        Type resultType = value.getResultType();
        Type componentType = componentType(value);

        Class<?> matchingType = matchType(type).get();

        TypeManager types = generator.getTypes();

        Type effectiveResultType = types.bestType(resultType, matchingType);
        Type temporaryType = types.bestType(type, effectiveResultType, matchingType);
        Type componentResultType = types.isHidden(componentType) ? typeArgument(temporaryType, 0).orElse(Object.class) : componentType;

        types.registerTypes(effectiveResultType, type, componentResultType);

        return generator.forVariable(value, matchingType, definition -> {

            List<Computation> elementTemplates = elements(value)
                .map(element -> withResultType(element, componentResultType).accept(generator, context))
                .filter(element -> element != null)
                .collect(toList());

            List<String> elements = elementTemplates.stream()
                .map(template -> generator.adapt(template.getValue(), componentResultType, template.getType()))
                .collect(toList());

            List<String> statements = elementTemplates.stream()
                .flatMap(template -> template.getStatements().stream())
                .collect(toList());

            String tempVar = definition.getName();
            if (!equalTypes(effectiveResultType, temporaryType)) {
                tempVar = generator.temporaryLocal();
            }

            String set = types.isHidden(type)
                ? generator.adapt(types.getWrappedName(type), temporaryType, types.wrapHidden(type))
                : newObject(types.getConstructorTypeName(type));
            String temporaryTypeName = Optional.of(temporaryType)
                .filter(t -> typeArguments(t).count() > 0)
                .filter(t -> typeArguments(t).allMatch(Types::isBound))
                .map(t -> types.getVariableTypeName(t))
                .orElse(types.getRawTypeName(temporaryType));
            String setInit = assignLocalVariableStatement(temporaryTypeName, tempVar, set);
            statements.add(setInit);

            for (String element : elements) {
                String addElement = callMethodStatement(tempVar, "add", element);
                statements.add(addElement);
            }

            if (definition.isDefined() && !definition.isReady()) {
                statements.add(callMethodStatement(definition.getName(), "addAll", tempVar));
                return variable(definition.getName(), definition.getType(), statements);
            } else if (generator.needsAdaptation(effectiveResultType, temporaryType)) {
                tempVar = generator.adapt(tempVar, effectiveResultType, temporaryType);
                statements.add(assignLocalVariableStatement(types.getVariableTypeName(effectiveResultType), definition.getName(), tempVar));
            } else if (!equalTypes(effectiveResultType, temporaryType)) {
                statements.add(assignLocalVariableStatement(types.getVariableTypeName(effectiveResultType), definition.getName(), tempVar));
            }

            return variable(definition.getName(), effectiveResultType, statements);
        });
    }

    private SerializedValue withResultType(SerializedValue value, Type type) {
        if (value instanceof SerializedReferenceType) {
            ((SerializedReferenceType) value).setResultType(type);
        }
        return value;
    }

}
