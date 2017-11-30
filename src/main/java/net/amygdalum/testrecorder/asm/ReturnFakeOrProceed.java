package net.amygdalum.testrecorder.asm;

import static net.amygdalum.testrecorder.util.ByteCode.isPrimitive;
import static net.amygdalum.testrecorder.util.ByteCode.unboxPrimitives;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class ReturnFakeOrProceed implements SequenceInstruction {

	private MethodNode methodNode;
	private Class<?> clazz;
	private String field;

	public ReturnFakeOrProceed(MethodNode methodNode, Class<?> clazz, String field) {
		this.methodNode = methodNode;
		this.clazz = clazz;
		this.field = field;
	}

	@Override
	public InsnList build(Sequence sequence) {
		Type returnType = Type.getReturnType(methodNode.desc);
		InsnList insnList = new InsnList();
		LabelNode continueLabel = new LabelNode();
		insnList.add(new InsnNode(DUP));
		insnList.add(new FieldInsnNode(GETSTATIC, Type.getInternalName(clazz), field, Type.getDescriptor(Object.class)));
		insnList.add(new JumpInsnNode(IF_ACMPEQ, continueLabel));
		if (returnType.getSize() == 0) {
			insnList.add(new InsnNode(POP));
			insnList.add(new InsnNode(RETURN));
		} else if (isPrimitive(returnType)) {
			insnList.add(unboxPrimitives(returnType));
			insnList.add(new InsnNode(returnType.getOpcode(IRETURN)));
		} else {
			insnList.add(new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
			insnList.add(new InsnNode(ARETURN));
		}
		insnList.add(continueLabel);
		insnList.add(new InsnNode(POP));
		return insnList;
	}

}
