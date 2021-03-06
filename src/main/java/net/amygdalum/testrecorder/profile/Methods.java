package net.amygdalum.testrecorder.profile;

import java.lang.reflect.Method;

/**
 * used to specify a method or multiple methods. Provides two predicate methods for matching at compile time and at run time.
 */
public interface Methods {

	/**
	 * defines matching with runtime (reflection) methods.
	 * 
	 * @param method a method specified via reflection
	 * @return true if method is covered by this predicate, false otherwise
	 */
	boolean matches(Method method);

	/**
	 * defines matching with compile time method specifications.
	 * 
     * @param className the internal name of the class (e.g. java/lang/String for java.lang.String)
	 * @param methodName the name of the method (e.g getBytes)
	 * @param methodDescriptor the method descriptor of the method (e.g. (Ljava/nio/Charset;)[B; for byte[] getBytes(Charset charset))
	 * @return true if the compile time description of the method is covered by this predicate, false otherwise
	 */
	boolean matches(String className, String methodName, String methodDescriptor);

	/**
	 * specifies a set of methods by name (note that class and descriptor of the method are not taken into account)
	 * 
	 * @param name the name of the method
	 * @return a predicate return true for every method of the given name
	 */
	static Methods byName(String name) {
		return new MethodsByName(name);
	}

	/**
	 * specifies a method by description
	 * 
     * @param className the internal name of the class (e.g. java/lang/String for java.lang.String)
	 * @param methodName the name of the method (e.g getBytes)
	 * @param methodDescriptor the method descriptor of the method (e.g. (Ljava/nio/Charset;)[B; for byte[] getBytes(Charset charset))
	 * @return a predicate return true for the specified method
	 */
	static Methods byDescription(String className, String methodName, String methodDescriptor) {
		return new MethodDescription(className, methodName, methodDescriptor);
	}

}
