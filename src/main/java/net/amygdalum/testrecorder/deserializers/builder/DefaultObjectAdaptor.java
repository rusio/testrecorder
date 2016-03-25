package net.amygdalum.testrecorder.deserializers.builder;

import static java.util.stream.Collectors.toList;
import static net.amygdalum.testrecorder.deserializers.Templates.assignLocalVariableStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.genericObjectConverter;

import java.util.List;

import net.amygdalum.testrecorder.deserializers.Adaptor;
import net.amygdalum.testrecorder.deserializers.Computation;
import net.amygdalum.testrecorder.deserializers.DefaultAdaptor;
import net.amygdalum.testrecorder.deserializers.TypeManager;
import net.amygdalum.testrecorder.util.GenericObject;
import net.amygdalum.testrecorder.values.SerializedObject;

public class DefaultObjectAdaptor extends DefaultAdaptor<SerializedObject, ObjectToSetupCode> implements Adaptor<SerializedObject, ObjectToSetupCode> {

	@Override
	public Computation tryDeserialize(SerializedObject value, TypeManager types, ObjectToSetupCode generator) {
		types.registerTypes(value.getType(), GenericObject.class);

		List<Computation> elementTemplates = value.getFields().stream()
			.sorted()
			.map(element -> element.accept(generator))
			.collect(toList());

		List<String> elements = elementTemplates.stream()
			.map(template -> template.getValue())
			.collect(toList());

		List<String> statements = elementTemplates.stream()
			.flatMap(template -> template.getStatements().stream())
			.collect(toList());

		String genericObject = genericObjectConverter(types.getRawTypeName(value.getValueType()), elements);

		String name = generator.localVariable(value, value.getValueType());
		statements.add(assignLocalVariableStatement(types.getRawName(value.getValueType()), name, genericObject));

		return new Computation(name, statements);
	}

}