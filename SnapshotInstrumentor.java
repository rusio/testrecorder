package net.amygdalum.testrecorder;

import static java.util.stream.Collectors.toList;
import static net.amygdalum.testrecorder.asm.ByteCode.memorizeLocal;
import static net.amygdalum.testrecorder.asm.ByteCode.pushAsArray;
import static net.amygdalum.testrecorder.asm.ByteCode.pushType;
import static net.amygdalum.testrecorder.asm.ByteCode.pushTypes;
import static net.amygdalum.testrecorder.asm.ByteCode.recallLocal;
import static org.objectweb.asm.Opcodes.ACC_ANNOTATION;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.amygdalum.testrecorder.asm.ByteCode;
import net.amygdalum.testrecorder.asm.GetStatic;
import net.amygdalum.testrecorder.asm.GetThisOrNull;
import net.amygdalum.testrecorder.asm.InvokeVirtual;
import net.amygdalum.testrecorder.asm.Ldc;
import net.amygdalum.testrecorder.asm.Locals;
import net.amygdalum.testrecorder.asm.Memoize;
import net.amygdalum.testrecorder.asm.Recall;
import net.amygdalum.testrecorder.asm.Sequence;
import net.amygdalum.testrecorder.asm.SequenceInstruction;
import net.amygdalum.testrecorder.asm.TryCatch;
import net.amygdalum.testrecorder.asm.WrapArguments;
import net.amygdalum.testrecorder.profile.Classes;
import net.amygdalum.testrecorder.profile.Fields;
import net.amygdalum.testrecorder.profile.Methods;
import net.amygdalum.testrecorder.profile.SerializationProfile.Global;
import net.amygdalum.testrecorder.profile.SerializationProfile.Input;
import net.amygdalum.testrecorder.profile.SerializationProfile.Output;
import net.amygdalum.testrecorder.util.AttachableClassFileTransformer;

public class SnapshotInstrumentor extends AttachableClassFileTransformer implements ClassFileTransformer {

	public static final String SNAPSHOT_MANAGER_FIELD_NAME = "MANAGER";
	private static final String INPUT_VARIABLES = "inputVariables";
	private static final String OUTPUT_VARIABLES = "outputVariables";

	private static final String SnapshotManager_name = Type.getInternalName(SnapshotManager.class);

	private static final String SnaphotManager_descriptor = Type.getDescriptor(SnapshotManager.class);
	private static final String Recorded_descriptor = Type.getDescriptor(Recorded.class);
	private static final String Input_descriptor = Type.getDescriptor(Input.class);
	private static final String Output_descriptor = Type.getDescriptor(Output.class);
	private static final String Global_descriptor = Type.getDescriptor(Global.class);

	private static final String SnaphotManager_outputVariablesResult_descriptor = ByteCode.methodDescriptor(SnapshotManager.class, OUTPUT_VARIABLES, Object.class, String.class,
		java.lang.reflect.Type.class, Object.class, java.lang.reflect.Type[].class, Object[].class);
	private static final String SnaphotManager_outputVariablesNoResult_descriptor = ByteCode.methodDescriptor(SnapshotManager.class, OUTPUT_VARIABLES, Object.class, String.class,
		java.lang.reflect.Type[].class, Object[].class);
	private static final String SnaphotManager_inputVariablesResult_descriptor = ByteCode.methodDescriptor(SnapshotManager.class, INPUT_VARIABLES, Object.class, String.class,
		java.lang.reflect.Type.class, Object.class, java.lang.reflect.Type[].class, Object[].class);
	private static final String SnaphotManager_inputVariablesNoResult_descriptor = ByteCode.methodDescriptor(SnapshotManager.class, INPUT_VARIABLES, Object.class, String.class,
		java.lang.reflect.Type[].class, Object[].class);

	private TestRecorderAgentConfig config;
	private Map<String, ClassNode> classCache;
	private Set<String> instrumentedClassNames;
	private Set<Class<?>> instrumentedClasses;

	public SnapshotInstrumentor(TestRecorderAgentConfig config) {
		this.config = config;
		this.classCache = new HashMap<>();
		this.instrumentedClassNames = new LinkedHashSet<>();
		this.instrumentedClasses = new LinkedHashSet<>();
		SnapshotManager.init(config);
	}

	@Override
	public Class<?>[] classesToRetransform() {
		Set<Class<?>> classesToRetransform = new LinkedHashSet<>();
		classesToRetransform.addAll(instrumentedClasses);

		for (String className : instrumentedClassNames) {
			classesToRetransform.add(ByteCode.classFrom(className));
		}

		return classesToRetransform.toArray(new Class[0]);
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		try {
			if (className == null) {
				return null;
			}
			for (Classes clazz : config.getClasses()) {
				if (clazz.matches(className)) {
					System.out.println("recording snapshots of " + className);
					byte[] instrument = instrument(classfileBuffer);
					if (classBeingRedefined != null) {
						instrumentedClasses.add(classBeingRedefined);
					} else {
						instrumentedClassNames.add(className);
					}

					return instrument;
				}
			}
			return null;
		} catch (Throwable e) {
			System.err.println("exception occured while preparing recording of snapshots: " + e.getMessage());
			e.printStackTrace(System.err);
			return null;
		}

	}

	private ClassNode fetchClassNode(String className) throws IOException {
		ClassNode classNode = classCache.get(className);
		if (classNode == null) {
			ClassReader cr = new ClassReader(className);
			classNode = new ClassNode();

			cr.accept(classNode, 0);
			classCache.put(className, classNode);
		}
		return classNode;
	}

	private ClassNode fetchClassNode(byte[] buffer) {
		ClassReader cr = new ClassReader(buffer);
		ClassNode classNode = new ClassNode();

		cr.accept(classNode, 0);
		classCache.put(classNode.name, classNode);
		return classNode;
	}

	private MethodNode fetchMethodNode(String className, String methodName, String methodDesc) throws IOException, NoSuchMethodException {
		ClassNode classNode = fetchClassNode(className);
		return classNode.methods.stream()
			.filter(method -> method.name.equals(methodName) && method.desc.equals(methodDesc))
			.findFirst()
			.orElseThrow(() -> new NoSuchMethodException(methodName + methodDesc));
	}

	public byte[] instrument(String className) throws IOException {
		return instrument(fetchClassNode(className));
	}

	public byte[] instrument(byte[] buffer) {
		return instrument(fetchClassNode(buffer));
	}

	public byte[] instrument(ClassNode classNode) {
		if (isClass(classNode)) {

			logSkippedSnapshotMethods(classNode);

			instrumentStaticInitializer(classNode);

			instrumentSnapshotMethods(classNode);

			instrumentInputMethods(classNode);

			instrumentOutputMethods(classNode);

			instrumentInputCalls(classNode);

			instrumentOutputCalls(classNode);
		}

		ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		classNode.accept(out);
		return out.toByteArray();
	}

	private boolean isClass(ClassNode classNode) {
		return (classNode.access & (ACC_INTERFACE | ACC_ANNOTATION)) == 0;
	}

	private void logSkippedSnapshotMethods(ClassNode classNode) {
		for (MethodNode methodNode : getSkippedSnapshotMethods(classNode)) {
			System.err.println("method " + Type.getMethodType(methodNode.desc).getDescriptor() + " in " + Type.getType(classNode.name) + " is not accessible, skipping");
		}
	}

	private void instrumentStaticInitializer(ClassNode classNode) {
		for (MethodNode methodNode : getSnapshotMethods(classNode)) {
			SnapshotManager.MANAGER.registerRecordedMethod(keySignature(classNode, methodNode), classNode.name, methodNode.name, methodNode.desc);
		}
		for (FieldNode fieldNode : getGlobalFields(classNode)) {
			SnapshotManager.MANAGER.registerGlobal(classNode.name, fieldNode.name);
		}
	}

	private void instrumentSnapshotMethods(ClassNode classNode) {
		for (MethodNode method : getSnapshotMethods(classNode)) {
			instrumentSnapshotMethod(classNode, method);
		}
	}

	protected void instrumentSnapshotMethod(ClassNode classNode, MethodNode methodNode) {
		methodNode.instructions = new TryCatch(methodNode)
			.withTry(setupVariables(classNode, methodNode))
			.withCatch(throwVariables(classNode, methodNode))
			.withReturn(expectVariables(classNode, methodNode))
			.build(Sequence.sequence(new Locals(methodNode)));
	}

	private void instrumentInputMethods(ClassNode classNode) {
		for (MethodNode method : getInputMethods(classNode)) {
			instrumentInputMethod(classNode, method);
		}
	}

	protected void instrumentInputMethod(ClassNode classNode, MethodNode methodNode) {

	}

	private void instrumentOutputMethods(ClassNode classNode) {
		for (MethodNode method : getOutputMethods(classNode)) {
			instrumentOutputMethod(classNode, method);
		}
	}

	protected void instrumentOutputMethod(ClassNode classNode, MethodNode methodNode) {

	}

	private void instrumentInputCalls(ClassNode classNode) {
		for (MethodNode method : classNode.methods) {
			if (!isInputMethod(classNode.name, method)) {
				List<MethodInsnNode> inputCalls = stream(method.instructions.iterator())
					.filter(node -> node instanceof MethodInsnNode)
					.map(node -> (MethodInsnNode) node)
					.filter(node -> isInputCall(node))
					.collect(toList());
				for (MethodInsnNode inputCall : inputCalls) {
					AbstractInsnNode location = inputCall.getPrevious();
					method.instructions.remove(inputCall);
					InsnList inputCallWrapped = wrapInputCall(method, inputCall);
					if (location == null) {
						method.instructions.insert(inputCallWrapped);
					} else {
						method.instructions.insert(location, inputCallWrapped);
					}
				}
			}
		}
	}

	private InsnList wrapInputCall(MethodNode method, MethodInsnNode inputCall) {
		Locals locals = new Locals(method);

		Type ownerType = Type.getObjectType(inputCall.owner);
		Type methodType = Type.getMethodType(inputCall.desc);
		Type[] argumentTypes = methodType.getArgumentTypes();
		Type[] returnType = methodType.getReturnType().getSize() == 0 ? new Type[0] : new Type[] { methodType.getReturnType() };

		InsnList insnList = new InsnList();

		int thisVar = locals.newLocalObject();
		if (inputCall.getOpcode() == INVOKESTATIC) {
			insnList.add(new LdcInsnNode(ownerType));
			insnList.add(new VarInsnNode(ASTORE, thisVar));
		} else {
			insnList.add(new InsnNode(DUP));
			insnList.add(new VarInsnNode(ASTORE, thisVar));
		}

		int[] argumentVars = new int[argumentTypes.length];
		int[] returnVars = new int[returnType.length];

		for (int i = 0; i < argumentVars.length; i++) {
			Type type = argumentTypes[i];
			int newLocal = locals.newLocal(type);
			argumentVars[i] = newLocal;
			int storecode = type.getOpcode(ISTORE);
			insnList.insert(new VarInsnNode(storecode, newLocal));
			int loadcode = type.getOpcode(ILOAD);
			insnList.add(new VarInsnNode(loadcode, newLocal));
		}
		insnList.add(inputCall);

		if (returnVars.length >= 1) {
			Type type = returnType[0];
			int newLocal = locals.newLocal(type);
			returnVars[0] = newLocal;
			insnList.add(memorizeLocal(type, newLocal));
		}

		insnList.add(new FieldInsnNode(GETSTATIC, SnapshotManager_name, SNAPSHOT_MANAGER_FIELD_NAME, SnaphotManager_descriptor));

		insnList.add(new VarInsnNode(ALOAD, thisVar));
		insnList.add(new LdcInsnNode(inputCall.name));
		for (int i = 0; i < returnType.length; i++) {
			Type type = returnType[i];
			int result = returnVars[i];
			insnList.add(pushType(type));
			insnList.add(recallLocal(result));
		}
		insnList.add(pushTypes(argumentTypes));
		insnList.add(pushAsArray(argumentVars, argumentTypes));
		if (returnType.length > 0) {
			insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapshotManager_name, INPUT_VARIABLES, SnaphotManager_inputVariablesResult_descriptor, false));
		} else {
			insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapshotManager_name, INPUT_VARIABLES, SnaphotManager_inputVariablesNoResult_descriptor, false));
		}

		return insnList;
	}

	private void instrumentOutputCalls(ClassNode classNode) {
		for (MethodNode method : classNode.methods) {
			if (!isOutputMethod(classNode.name, method)) {
				List<MethodInsnNode> outputCalls = stream(method.instructions.iterator())
					.filter(node -> node instanceof MethodInsnNode)
					.map(node -> (MethodInsnNode) node)
					.filter(node -> isOutputCall(node))
					.collect(toList());
				for (MethodInsnNode outputCall : outputCalls) {
					AbstractInsnNode location = outputCall.getPrevious();
					method.instructions.remove(outputCall);
					InsnList outputCallWrapped = wrapOutputCall(method, outputCall);
					if (location == null) {
						method.instructions.insert(outputCallWrapped);
					} else {
						method.instructions.insert(location, outputCallWrapped);
					}
				}
			}
		}
	}

	private InsnList wrapOutputCall(MethodNode method, MethodInsnNode inputCall) {
		Locals locals = new Locals(method);

		Type ownerType = Type.getObjectType(inputCall.owner);
		Type methodType = Type.getMethodType(inputCall.desc);
		Type[] argumentTypes = methodType.getArgumentTypes();
		Type[] returnType = methodType.getReturnType().getSize() == 0 ? new Type[0] : new Type[] { methodType.getReturnType() };

		InsnList insnList = new InsnList();

		int thisVar = locals.newLocalObject();
		if (inputCall.getOpcode() == INVOKESTATIC) {
			insnList.add(new LdcInsnNode(ownerType));
			insnList.add(new VarInsnNode(ASTORE, thisVar));
		} else {
			insnList.add(new InsnNode(DUP));
			insnList.add(new VarInsnNode(ASTORE, thisVar));
		}

		int[] argumentVars = new int[argumentTypes.length];
		int[] returnVars = new int[returnType.length];

		for (int i = 0; i < argumentVars.length; i++) {
			Type type = argumentTypes[i];
			int newLocal = locals.newLocal(type);
			argumentVars[i] = newLocal;
			int storecode = type.getOpcode(ISTORE);
			insnList.insert(new VarInsnNode(storecode, newLocal));
			int loadcode = type.getOpcode(ILOAD);
			insnList.add(new VarInsnNode(loadcode, newLocal));
		}
		insnList.add(inputCall);

		if (returnVars.length >= 1) {
			Type type = returnType[0];
			int newLocal = locals.newLocal(type);
			returnVars[0] = newLocal;
			insnList.add(memorizeLocal(type, newLocal));
		}

		insnList.add(new FieldInsnNode(GETSTATIC, SnapshotManager_name, SNAPSHOT_MANAGER_FIELD_NAME, SnaphotManager_descriptor));

		insnList.add(new VarInsnNode(ALOAD, thisVar));
		insnList.add(new LdcInsnNode(inputCall.name));
		for (int i = 0; i < returnType.length; i++) {
			Type type = returnType[i];
			int result = returnVars[i];
			insnList.add(pushType(type));
			insnList.add(recallLocal(result));
		}
		insnList.add(pushTypes(argumentTypes));
		insnList.add(pushAsArray(argumentVars, argumentTypes));
		if (returnType.length > 0) {
			insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapshotManager_name, OUTPUT_VARIABLES, SnaphotManager_outputVariablesResult_descriptor, false));
		} else {
			insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapshotManager_name, OUTPUT_VARIABLES, SnaphotManager_outputVariablesNoResult_descriptor, false));
		}

		return insnList;
	}

	private <T> Stream<T> stream(Iterator<T> iterator) {
		Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
		return StreamSupport.stream(spliterator, false);
	}

	private List<MethodNode> getSnapshotMethods(ClassNode classNode) {
		if (!isVisible(classNode)) {
			return Collections.emptyList();
		}
		return classNode.methods.stream()
			.filter(method -> isSnapshotMethod(method))
			.filter(method -> isVisible(method))
			.collect(toList());
	}

	private List<MethodNode> getInputMethods(ClassNode classNode) {
		if (!isVisible(classNode)) {
			return Collections.emptyList();
		}
		return classNode.methods.stream()
			.filter(method -> isInputMethod(classNode, method))
			.collect(toList());
	}

	private List<MethodNode> getOutputMethods(ClassNode classNode) {
		if (!isVisible(classNode)) {
			return Collections.emptyList();
		}
		return classNode.methods.stream()
			.filter(method -> isOutputMethod(classNode, method))
			.collect(toList());
	}

	private List<FieldNode> getGlobalFields(ClassNode classNode) {
		if (!isVisible(classNode)) {
			return Collections.emptyList();
		}
		return classNode.fields.stream()
			.filter(field -> isGlobalField(classNode.name, field))
			.filter(field -> isVisible(field))
			.collect(toList());
	}

	private List<MethodNode> getSkippedSnapshotMethods(ClassNode classNode) {
		return classNode.methods.stream()
			.filter(method -> isSnapshotMethod(method))
			.filter(method -> !isVisible(classNode) || !isVisible(method))
			.collect(toList());
	}

	private boolean isVisible(ClassNode classNode) {
		if ((classNode.access & ACC_PRIVATE) != 0) {
			return false;
		}
		return classNode.innerClasses.stream()
			.filter(innerClassNode -> innerClassNode.name.equals(classNode.name))
			.map(innerClassNode -> (innerClassNode.access & ACC_PRIVATE) == 0)
			.findFirst()
			.orElse(true);
	}

	private boolean isSnapshotMethod(MethodNode methodNode) {
		if (methodNode.visibleAnnotations == null) {
			return false;
		}
		return methodNode.visibleAnnotations.stream()
			.anyMatch(annotation -> annotation.desc.equals(Recorded_descriptor));
	}

	protected boolean isGlobalField(String className, FieldNode fieldNode) {
		return fieldNode.visibleAnnotations != null && fieldNode.visibleAnnotations.stream()
			.anyMatch(annotation -> annotation.desc.equals(Global_descriptor))
			|| config.getGlobalFields().stream()
				.anyMatch(field -> matches(field, className, fieldNode.name, fieldNode.desc));
	}

	private boolean isVisible(MethodNode methodNode) {
		return (methodNode.access & ACC_PRIVATE) == 0;
	}

	private boolean isVisible(FieldNode fieldNode) {
		return (fieldNode.access & ACC_PRIVATE) == 0;
	}

	protected boolean isInputCall(MethodInsnNode node) {
		for (Methods methods : config.getInputs()) {
			if (methods.matches(node.owner, node.name, node.desc)) {
				return true;
			}
		}
		try {
			MethodNode methodNode = fetchMethodNode(node.owner, node.name, node.desc);
			return isInputMethod(node.owner, methodNode);
		} catch (IOException | NoSuchMethodException e) {
			return false;
		}
	}

	protected boolean isInputMethod(ClassNode classNode, MethodNode methodNode) {
		return methodNode.visibleAnnotations != null && methodNode.visibleAnnotations.stream()
			.anyMatch(annotation -> annotation.desc.equals(Input_descriptor))
			|| config.getInputs().stream()
				.anyMatch(method -> matches(method, classNode.name, methodNode.name, methodNode.desc));
	}

	protected boolean isInputMethod(String className, MethodNode methodNode) {
		return methodNode.visibleAnnotations != null && methodNode.visibleAnnotations.stream()
			.anyMatch(annotation -> annotation.desc.equals(Input_descriptor))
			|| config.getInputs().stream()
				.anyMatch(method -> matches(method, className, methodNode.name, methodNode.desc));
	}

	protected boolean isOutputCall(MethodInsnNode node) {
		for (Methods methods : config.getOutputs()) {
			if (methods.matches(node.owner, node.name, node.desc)) {
				return true;
			}
		}
		try {
			MethodNode methodNode = fetchMethodNode(node.owner, node.name, node.desc);
			return isOutputMethod(node.owner, methodNode);
		} catch (IOException | NoSuchMethodException e) {
			return false;
		}
	}

	protected boolean isOutputMethod(ClassNode classNode, MethodNode methodNode) {
		return methodNode.visibleAnnotations != null && methodNode.visibleAnnotations.stream()
			.anyMatch(annotation -> annotation.desc.equals(Output_descriptor))
			|| config.getOutputs().stream()
				.anyMatch(method -> matches(method, classNode.name, methodNode.name, methodNode.desc));
	}

	protected boolean isOutputMethod(String className, MethodNode methodNode) {
		return methodNode.visibleAnnotations != null && methodNode.visibleAnnotations.stream()
			.anyMatch(annotation -> annotation.desc.equals(Output_descriptor))
			|| config.getOutputs().stream()
				.anyMatch(method -> matches(method, className, methodNode.name, methodNode.desc));
	}

	private boolean matches(Fields field, String className, String fieldName, String fieldDescriptor) {
		return field.matches(className, fieldName, fieldDescriptor);
	}

	private boolean matches(Methods method, String className, String methodName, String methodDescriptor) {
		return method.matches(className, methodName, methodDescriptor);
	}

	protected SequenceInstruction setupVariables(ClassNode classNode, MethodNode methodNode) {
		return new InvokeVirtual(SnapshotManager.class, "setupVariables", Object.class, String.class, Object[].class)
			.withBase(new GetStatic(SnapshotManager.class, "MANAGER"))
			.withArgument(0, new GetThisOrNull(methodNode))
			.withArgument(1, new Ldc(keySignature(classNode, methodNode)))
			.withArgument(2, new WrapArguments());
	}

	protected SequenceInstruction expectVariables(ClassNode classNode, MethodNode methodNode) {
		if (ByteCode.returnsResult(methodNode)) {
			return Sequence.sequence(new Locals(methodNode))
				.then(new Memoize("returnValue", Type.getReturnType(methodNode.desc)))
				.then(new InvokeVirtual(SnapshotManager.class, "expectVariables", Object.class, String.class, Object.class, Object[].class)
					.withBase(new GetStatic(SnapshotManager.class, "MANAGER"))
					.withArgument(0, new GetThisOrNull(methodNode))
					.withArgument(1, new Ldc(keySignature(classNode, methodNode)))
					.withArgument(2, new Recall("returnValue"))
					.withArgument(3, new WrapArguments()));
		} else {
			return Sequence.sequence(new Locals(methodNode))
				.then(new InvokeVirtual(SnapshotManager.class, "expectVariables", Object.class, String.class, Object[].class)
					.withBase(new GetStatic(SnapshotManager.class, "MANAGER"))
					.withArgument(0, new GetThisOrNull(methodNode))
					.withArgument(1, new Ldc(keySignature(classNode, methodNode)))
					.withArgument(2, new WrapArguments()));
		}
	}

	protected SequenceInstruction throwVariables(ClassNode classNode, MethodNode methodNode) {
		return Sequence.sequence(new Locals(methodNode))
			.then(new Memoize("throwable", Type.getType(Throwable.class)))
			.then(new InvokeVirtual(SnapshotManager.class, "throwVariables", Throwable.class, Object.class, String.class, Object[].class)
				.withBase(new GetStatic(SnapshotManager.class, "MANAGER"))
				.withArgument(0, new Recall("throwable"))
				.withArgument(1, new GetThisOrNull(methodNode))
				.withArgument(2, new Ldc(keySignature(classNode, methodNode)))
				.withArgument(3, new WrapArguments()));
	}

	private String keySignature(ClassNode classNode, MethodNode methodNode) {
		return classNode.name + ":" + methodNode.name + methodNode.desc;
	}

}