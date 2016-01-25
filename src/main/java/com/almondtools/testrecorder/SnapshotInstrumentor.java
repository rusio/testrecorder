package com.almondtools.testrecorder;

import static com.almondtools.testrecorder.ByteCode.memorizeLocal;
import static com.almondtools.testrecorder.ByteCode.pushAsArray;
import static com.almondtools.testrecorder.ByteCode.pushType;
import static com.almondtools.testrecorder.ByteCode.pushTypes;
import static com.almondtools.testrecorder.ByteCode.range;
import static com.almondtools.testrecorder.ByteCode.recallLocal;
import static java.util.stream.Collectors.toList;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SWAP;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.almondtools.testrecorder.visitors.TypeManager;

public class SnapshotInstrumentor implements ClassFileTransformer {

	private static final String Class_name = Type.getInternalName(Class.class);
	private static final String Object_name = Type.getInternalName(Object.class);
	private static final String TypeManager_name = Type.getInternalName(TypeManager.class);
	private static final String SnapShortGenerator_name = Type.getInternalName(SnapshotGenerator.class);

	private static final String SnapshotGenerator_descriptor = Type.getDescriptor(SnapshotGenerator.class);
	private static final String SnapshotExcluded_descriptor = Type.getDescriptor(SnapshotExcluded.class);
	private static final String Snapshot_descriptor = Type.getDescriptor(Snapshot.class);
	private static final String SnapshotInput_descriptor = Type.getDescriptor(SnapshotInput.class);
	private static final String SnapshotOutput_descriptor = Type.getDescriptor(SnapshotOutput.class);

	private static final String Object_getClass_descriptor = ByteCode.methodDescriptor(Object.class, "getClass");

	private static final String SnapshotGenerator_init_descriptor = ByteCode.constructorDescriptor(SnapshotGenerator.class, Object.class, Class.class);
	private static final String SnapshotGenerator_registerMethod_descriptor = ByteCode.methodDescriptor(SnapshotGenerator.class, "register", String.class, Method.class);
	private static final String SnapshotGenerator_getCurrentGenerator_descriptor = ByteCode.methodDescriptor(SnapshotGenerator.class, "getCurrentGenerator");
	private static final String SnapshotGenerator_setupVariables_descriptor = ByteCode.methodDescriptor(SnapshotGenerator.class, "setupVariables", String.class, Object[].class);
	private static final String SnapshotGenerator_expectVariablesResult_descriptor = ByteCode.methodDescriptor(SnapshotGenerator.class, "expectVariables", Object.class, Object[].class);
	private static final String SnapshotGenerator_expectVariablesNoResult_descriptor = ByteCode.methodDescriptor(SnapshotGenerator.class, "expectVariables", Object[].class);
	private static final String SnapshotGenerator_throwVariables_descriptor = ByteCode.methodDescriptor(SnapshotGenerator.class, "throwVariables", Throwable.class, Object[].class);
	private static final String SnapshotGenerator_outputVariables_descriptor = ByteCode.methodDescriptor(SnapshotGenerator.class, "outputVariables", Class.class, String.class,
		java.lang.reflect.Type[].class, Object[].class);
	private static final String SnapshotGenerator_inputVariablesResult_descriptor = ByteCode.methodDescriptor(SnapshotGenerator.class, "inputVariables", Class.class, String.class,
		java.lang.reflect.Type.class, Object.class, java.lang.reflect.Type[].class, Object[].class);
	private static final String SnapshotGenerator_inputVariablesNoResult_descriptor = ByteCode.methodDescriptor(SnapshotGenerator.class, "inputVariables", Class.class, String.class,
		java.lang.reflect.Type[].class, Object[].class);

	private static final String TypeManager_getDeclaredMethod_descriptor = ByteCode.methodDescriptor(TypeManager.class, "getDeclaredMethod", Class.class, String.class, Class[].class);

	private static final String CONSTRUCTOR_NAME = "<init>";

	public static final String SNAPSHOT_GENERATOR_FIELD_NAME = "generator";

	private SnapshotConfig config;

	public SnapshotInstrumentor(SnapshotConfig config) {
		this.config = config;
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		for (String pkg : config.getPackages()) {
			pkg = pkg.replace('.', '/');
			if (className != null && className.startsWith(pkg)) {
				System.out.println("recording snapshots of " + className);
				return instrument(new ClassReader(classfileBuffer));
			}
		}
		return null;
	}

	public byte[] instrument(String className) throws IOException {
		return instrument(new ClassReader(className));
	}

	public byte[] instrument(ClassReader cr) {
		ClassNode classNode = new ClassNode();

		cr.accept(classNode, 0);

		instrumentFields(classNode);

		instrumentConstructors(classNode);

		instrumentSnapshotMethods(classNode);

		instrumentInputMethods(classNode);
		instrumentOutputMethods(classNode);

		ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		classNode.accept(out);
		return out.toByteArray();
	}

	private void instrumentFields(ClassNode classNode) {
		classNode.fields.add(createTestAspectField());
	}

	private void instrumentConstructors(ClassNode classNode) {
		for (MethodNode method : getRootConstructors(classNode)) {
			List<InsnNode> rets = findReturn(method.instructions);
			for (InsnNode ret : rets) {
				method.instructions.insertBefore(ret, createTestAspectInitializer(classNode));
			}
		}
	}

	private void instrumentSnapshotMethods(ClassNode classNode) {
		for (MethodNode method : getSnapshotMethods(classNode)) {
			LabelNode tryLabel = new LabelNode();
			LabelNode catchLabel = new LabelNode();
			LabelNode finallyLabel = new LabelNode();
			method.tryCatchBlocks.add(createTryCatchBlock(tryLabel, catchLabel));
			method.instructions.insert(createTry(tryLabel, setupVariables(classNode, method)));
			List<InsnNode> rets = findReturn(method.instructions);
			for (InsnNode ret : rets) {
				method.instructions.insert(ret, new JumpInsnNode(GOTO, finallyLabel));
				method.instructions.remove(ret);
			}
			int returnOpcode = rets.stream()
				.map(ret -> ret.getOpcode())
				.distinct()
				.findFirst()
				.orElse(RETURN);
			method.instructions.add(createCatchFinally(catchLabel, throwVariables(classNode, method), finallyLabel, expectVariables(classNode, method), new InsnNode(returnOpcode)));
		}
	}

	private void instrumentInputMethods(ClassNode classNode) {
		for (MethodNode method : getInputMethods(classNode)) {
			List<InsnNode> rets = findReturn(method.instructions);
			InsnList notifyInput = notifyInput(classNode, method);
			for (InsnNode ret : rets) {
				method.instructions.insertBefore(ret, notifyInput);
			}
		}
	}

	private void instrumentOutputMethods(ClassNode classNode) {
		for (MethodNode method : getOutputMethods(classNode)) {
			method.instructions.insert(notifyOutput(classNode, method));
		}
	}

	private FieldNode createTestAspectField() {
		FieldNode fieldNode = new FieldNode(ACC_PRIVATE | ACC_SYNTHETIC, SNAPSHOT_GENERATOR_FIELD_NAME, SnapshotGenerator_descriptor, SnapshotGenerator_descriptor, null);
		fieldNode.visibleAnnotations = new ArrayList<>();
		fieldNode.visibleAnnotations.add(new AnnotationNode(SnapshotExcluded_descriptor));
		return fieldNode;
	}

	private List<MethodNode> getRootConstructors(ClassNode classNode) {
		return (classNode.methods).stream()
			.filter(method -> isConstructor(method))
			.filter(method -> isRoot(method, classNode.name))
			.collect(toList());
	}

	private boolean isConstructor(MethodNode method) {
		return method.name.equals(CONSTRUCTOR_NAME);
	}

	private boolean isRoot(MethodNode method, String name) {
		return stream(method.instructions.iterator())
			.filter(insn -> insn.getOpcode() == INVOKESPECIAL && insn instanceof MethodInsnNode)
			.map(insn -> (MethodInsnNode) insn)
			.filter(insn -> insn.name.equals(CONSTRUCTOR_NAME))
			.noneMatch(insn -> insn.owner != null && insn.owner.equals(name));
	}

	private <T> Stream<T> stream(Iterator<T> iterator) {
		Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
		return StreamSupport.stream(spliterator, false);
	}

	private List<InsnNode> findReturn(InsnList instructions) {
		return stream(instructions.iterator())
			.filter(insn -> insn instanceof InsnNode)
			.map(insn -> (InsnNode) insn)
			.filter(insn -> IRETURN <= insn.getOpcode() && insn.getOpcode() <= RETURN)
			.collect(toList());
	}

	private InsnList createTestAspectInitializer(ClassNode classNode) {
		InsnList insnList = new InsnList();

		insnList.add(new VarInsnNode(ALOAD, 0));
		insnList.add(new TypeInsnNode(NEW, SnapShortGenerator_name));
		insnList.add(new InsnNode(DUP));
		insnList.add(new VarInsnNode(ALOAD, 0));
		insnList.add(new LdcInsnNode(Type.getType(config.getClass())));
		insnList.add(new MethodInsnNode(INVOKESPECIAL, SnapShortGenerator_name, CONSTRUCTOR_NAME, SnapshotGenerator_init_descriptor, false));
		insnList.add(new FieldInsnNode(PUTFIELD, classNode.name, SNAPSHOT_GENERATOR_FIELD_NAME, SnapshotGenerator_descriptor));

		insnList.add(new VarInsnNode(ALOAD, 0));
		insnList.add(new FieldInsnNode(GETFIELD, classNode.name, SNAPSHOT_GENERATOR_FIELD_NAME, SnapshotGenerator_descriptor));

		for (MethodNode methodNode : getSnapshotMethods(classNode)) {
			insnList.add(new InsnNode(DUP));
			insnList.add(new LdcInsnNode(keySignature(classNode, methodNode)));

			insnList.add(pushMethod(classNode, methodNode));

			insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapShortGenerator_name, "register", SnapshotGenerator_registerMethod_descriptor, false));
		}

		insnList.add(new InsnNode(POP));

		return insnList;
	}

	private InsnList pushMethod(ClassNode clazz, MethodNode method) {
		Type[] argumentTypes = Type.getArgumentTypes(method.desc);
		int argCount = argumentTypes.length;

		InsnList insnList = new InsnList();

		insnList.add(new VarInsnNode(ALOAD, 0));
		insnList.add(new MethodInsnNode(INVOKEVIRTUAL, Object_name, "getClass", Object_getClass_descriptor, false));

		insnList.add(new LdcInsnNode(method.name));

		insnList.add(new LdcInsnNode(argCount));
		insnList.add(new TypeInsnNode(Opcodes.ANEWARRAY, Class_name));
		for (int i = 0; i < argCount; i++) {
			insnList.add(new InsnNode(DUP));
			insnList.add(new LdcInsnNode(i));
			insnList.add(pushType(argumentTypes[i]));
			insnList.add(new InsnNode(AASTORE));
		}

		insnList.add(new MethodInsnNode(INVOKESTATIC, TypeManager_name, "getDeclaredMethod", TypeManager_getDeclaredMethod_descriptor, false));
		return insnList;
	}

	private List<MethodNode> getSnapshotMethods(ClassNode classNode) {
		return classNode.methods.stream()
			.filter(method -> isSnapshotMethod(method))
			.collect(toList());
	}

	private boolean isSnapshotMethod(MethodNode method) {
		if (method.visibleAnnotations == null) {
			return false;
		}
		return method.visibleAnnotations.stream()
			.anyMatch(annotation -> annotation.desc.equals(Snapshot_descriptor));
	}

	private List<MethodNode> getInputMethods(ClassNode classNode) {
		return classNode.methods.stream()
			.filter(method -> isInputMethod(method))
			.collect(toList());
	}

	private boolean isInputMethod(MethodNode method) {
		if (method.visibleAnnotations == null) {
			return false;
		}
		return method.visibleAnnotations.stream()
			.anyMatch(annotation -> annotation.desc.equals(SnapshotInput_descriptor));
	}

	private List<MethodNode> getOutputMethods(ClassNode classNode) {
		return classNode.methods.stream()
			.filter(method -> isOutputMethod(method))
			.collect(toList());
	}

	private boolean isOutputMethod(MethodNode method) {
		if (method.visibleAnnotations == null) {
			return false;
		}
		return method.visibleAnnotations.stream()
			.anyMatch(annotation -> annotation.desc.equals(SnapshotOutput_descriptor));
	}

	private TryCatchBlockNode createTryCatchBlock(LabelNode tryLabel, LabelNode catchLabel) {
		return new TryCatchBlockNode(tryLabel, catchLabel, catchLabel, null);
	}

	private InsnList createTry(LabelNode tryLabel, InsnList onBegin) {
		InsnList insnList = new InsnList();
		insnList.add(tryLabel);
		insnList.add(onBegin);
		return insnList;
	}

	private InsnList createCatchFinally(LabelNode catchLabel, InsnList onError, LabelNode finallyLabel, InsnList onSuccess, InsnNode ret) {
		InsnList insnList = new InsnList();
		insnList.add(new JumpInsnNode(GOTO, finallyLabel));
		insnList.add(catchLabel);
		insnList.add(onError);
		insnList.add(new InsnNode(ATHROW));
		insnList.add(finallyLabel);
		insnList.add(onSuccess);
		insnList.add(ret);
		return insnList;
	}

	private InsnList setupVariables(ClassNode classNode, MethodNode methodNode) {
		Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
		List<LocalVariableNode> arguments = range(methodNode.localVariables, 1, argumentTypes.length);

		InsnList insnList = new InsnList();

		insnList.add(new VarInsnNode(ALOAD, 0));
		insnList.add(new FieldInsnNode(GETFIELD, classNode.name, SNAPSHOT_GENERATOR_FIELD_NAME, SnapshotGenerator_descriptor));

		insnList.add(new LdcInsnNode(keySignature(classNode, methodNode)));

		insnList.add(pushAsArray(arguments, argumentTypes));

		insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapShortGenerator_name, "setupVariables", SnapshotGenerator_setupVariables_descriptor, false));

		return insnList;
	}

	private InsnList expectVariables(ClassNode classNode, MethodNode methodNode) {
		Type returnType = Type.getReturnType(methodNode.desc);
		Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
		List<LocalVariableNode> arguments = range(methodNode.localVariables, 1, argumentTypes.length);

		InsnList insnList = new InsnList();
		int newLocal = methodNode.maxLocals;

		if (returnType.getSize() > 0) {
			insnList.add(memorizeLocal(returnType, newLocal));
		}

		insnList.add(new VarInsnNode(ALOAD, 0));
		insnList.add(new FieldInsnNode(GETFIELD, classNode.name, SNAPSHOT_GENERATOR_FIELD_NAME, SnapshotGenerator_descriptor));

		if (returnType.getSize() > 0) {
			insnList.add(recallLocal(newLocal));
		}
		insnList.add(pushAsArray(arguments, argumentTypes));

		if (returnType.getSize() > 0) {
			insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapShortGenerator_name, "expectVariables", SnapshotGenerator_expectVariablesResult_descriptor, false));
		} else {
			insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapShortGenerator_name, "expectVariables", SnapshotGenerator_expectVariablesNoResult_descriptor, false));
		}

		return insnList;
	}

	private InsnList throwVariables(ClassNode classNode, MethodNode methodNode) {
		Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
		List<LocalVariableNode> arguments = range(methodNode.localVariables, 1, argumentTypes.length);

		InsnList insnList = new InsnList();

		insnList.add(new InsnNode(DUP));

		insnList.add(new VarInsnNode(ALOAD, 0));
		insnList.add(new FieldInsnNode(GETFIELD, classNode.name, SNAPSHOT_GENERATOR_FIELD_NAME, SnapshotGenerator_descriptor));

		insnList.add(new InsnNode(SWAP));

		insnList.add(pushAsArray(arguments, argumentTypes));

		insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapShortGenerator_name, "throwVariables", SnapshotGenerator_throwVariables_descriptor, false));

		return insnList;
	}

	private InsnList notifyInput(ClassNode classNode, MethodNode methodNode) {
		Type returnType = Type.getReturnType(methodNode.desc);
		Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
		List<LocalVariableNode> arguments = range(methodNode.localVariables, 1, argumentTypes.length);

		InsnList insnList = new InsnList();
		int newLocal = methodNode.maxLocals;

		if (returnType.getSize() > 0) {
			insnList.add(memorizeLocal(returnType, newLocal));
		}

		LabelNode skip = new LabelNode();
		LabelNode done = new LabelNode();

		insnList.add(new MethodInsnNode(INVOKESTATIC, SnapShortGenerator_name, "getCurrentGenerator", SnapshotGenerator_getCurrentGenerator_descriptor, false));
		insnList.add(new InsnNode(DUP));
		insnList.add(new JumpInsnNode(IFNULL, skip));

		insnList.add(new LdcInsnNode(Type.getObjectType(classNode.name)));
		insnList.add(new LdcInsnNode(methodNode.name));
		if (returnType.getSize() > 0) {
			insnList.add(pushType(returnType));
			insnList.add(recallLocal(newLocal));
		}
		insnList.add(pushTypes(argumentTypes));
		insnList.add(pushAsArray(arguments, argumentTypes));
		if (returnType.getSize() > 0) {
			insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapShortGenerator_name, "inputVariables", SnapshotGenerator_inputVariablesResult_descriptor, false));
		} else {
			insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapShortGenerator_name, "inputVariables", SnapshotGenerator_inputVariablesNoResult_descriptor, false));
		}
		insnList.add(new JumpInsnNode(Opcodes.GOTO, done));
		insnList.add(skip);
		insnList.add(new InsnNode(POP));
		insnList.add(done);
		return insnList;
	}

	private InsnList notifyOutput(ClassNode classNode, MethodNode methodNode) {
		Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
		List<LocalVariableNode> arguments = range(methodNode.localVariables, 1, argumentTypes.length);

		InsnList insnList = new InsnList();

		LabelNode skip = new LabelNode();
		LabelNode done = new LabelNode();

		insnList.add(new MethodInsnNode(INVOKESTATIC, SnapShortGenerator_name, "getCurrentGenerator", SnapshotGenerator_getCurrentGenerator_descriptor, false));
		insnList.add(new InsnNode(DUP));
		insnList.add(new JumpInsnNode(IFNULL, skip));

		insnList.add(new LdcInsnNode(Type.getObjectType(classNode.name)));
		insnList.add(new LdcInsnNode(methodNode.name));
		insnList.add(pushTypes(argumentTypes));
		insnList.add(pushAsArray(arguments, argumentTypes));
		insnList.add(new MethodInsnNode(INVOKEVIRTUAL, SnapShortGenerator_name, "outputVariables", SnapshotGenerator_outputVariables_descriptor, false));
		insnList.add(new JumpInsnNode(Opcodes.GOTO, done));
		insnList.add(skip);
		insnList.add(new InsnNode(POP));
		insnList.add(done);
		return insnList;
	}

	private String keySignature(ClassNode classNode, MethodNode methodNode) {
		return classNode.name + ":" + methodNode.name + methodNode.desc;
	}

}
