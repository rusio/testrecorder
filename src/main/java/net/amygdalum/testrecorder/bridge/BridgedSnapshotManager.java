package net.amygdalum.testrecorder.bridge;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Type;

/**
 * This class is attached to the bootstrap classpath by SnapshotManager, to enable jre classes to load fakes outside of jre.
 * 
 * Loading it with another class loader will break all features provided by this class, so never do it
 */
public class BridgedSnapshotManager {

	public static Object MANAGER;

	public static MethodHandle inputVariables;
	public static MethodHandle inputArguments;
	public static MethodHandle inputResult;

	public static MethodHandle outputVariables;
	public static MethodHandle outputArguments;
	public static MethodHandle outputResult;

	public static int inputVariables(StackTraceElement[] stackTrace, Object object, String method, Type resultType, Type[] paramTypes) throws Throwable {
		return (Integer) inputVariables.invoke(MANAGER, stackTrace, object, method, resultType, paramTypes);
	}

	public static void inputArguments(int id, Object... args) throws Throwable {
		inputArguments.invoke(MANAGER, id, args);
	}

	public static void inputResult(int id, Object result) throws Throwable {
		inputResult.invoke(MANAGER, id, result);
	}

	public static int outputVariables(StackTraceElement[] stackTrace, Object object, String method, Type resultType, Type[] paramTypes) throws Throwable {
		return (Integer) outputVariables.invoke(MANAGER, stackTrace, object, method, resultType, paramTypes);
	}

	public static void outputArguments(int id, Object... args) throws Throwable {
		outputArguments.invoke(MANAGER, id, args);
	}

	public static void outputResult(int id, Object result) throws Throwable {
		outputResult.invoke(MANAGER, id, result);
	}

}
