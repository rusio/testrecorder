package net.amygdalum.testrecorder;

import static java.lang.Character.toUpperCase;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.synchronizedMap;
import static java.util.stream.Collectors.toList;
import static net.amygdalum.testrecorder.deserializers.DeserializerContext.newContext;
import static net.amygdalum.testrecorder.deserializers.Templates.annotation;
import static net.amygdalum.testrecorder.deserializers.Templates.asLiteral;
import static net.amygdalum.testrecorder.deserializers.Templates.assignFieldStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.assignLocalVariableStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.callLocalMethod;
import static net.amygdalum.testrecorder.deserializers.Templates.callLocalMethodStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.callMethod;
import static net.amygdalum.testrecorder.deserializers.Templates.callMethodChainStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.callMethodStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.captureException;
import static net.amygdalum.testrecorder.deserializers.Templates.classOf;
import static net.amygdalum.testrecorder.deserializers.Templates.expressionStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.fieldAccess;
import static net.amygdalum.testrecorder.deserializers.Templates.fieldDeclaration;
import static net.amygdalum.testrecorder.deserializers.Templates.newObject;
import static net.amygdalum.testrecorder.deserializers.Templates.returnStatement;
import static net.amygdalum.testrecorder.deserializers.Templates.stringOf;
import static net.amygdalum.testrecorder.util.Types.baseType;
import static net.amygdalum.testrecorder.util.Types.isPrimitive;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.stringtemplate.v4.ST;

import net.amygdalum.testrecorder.ContextSnapshot.AnnotatedValue;
import net.amygdalum.testrecorder.deserializers.Computation;
import net.amygdalum.testrecorder.deserializers.DeserializerFactory;
import net.amygdalum.testrecorder.deserializers.LocalVariableNameGenerator;
import net.amygdalum.testrecorder.deserializers.TypeManager;
import net.amygdalum.testrecorder.deserializers.builder.SetupGenerators;
import net.amygdalum.testrecorder.deserializers.matcher.MatcherGenerators;
import net.amygdalum.testrecorder.evaluator.SerializedValueEvaluator;
import net.amygdalum.testrecorder.hints.AnnotateGroupExpression;
import net.amygdalum.testrecorder.hints.AnnotateTimestamp;
import net.amygdalum.testrecorder.runtime.Throwables;
import net.amygdalum.testrecorder.util.AnnotatedBy;
import net.amygdalum.testrecorder.util.ExpectedOutput;
import net.amygdalum.testrecorder.util.IORecorder;
import net.amygdalum.testrecorder.util.Pair;
import net.amygdalum.testrecorder.util.RecordInput;
import net.amygdalum.testrecorder.util.RecordOutput;
import net.amygdalum.testrecorder.util.SetupInput;
import net.amygdalum.testrecorder.util.Triple;
import net.amygdalum.testrecorder.values.SerializedField;
import net.amygdalum.testrecorder.values.SerializedInput;
import net.amygdalum.testrecorder.values.SerializedLiteral;
import net.amygdalum.testrecorder.values.SerializedOutput;

public class TestGenerator implements SnapshotConsumer {

	private static final Set<Class<?>> LITERAL_TYPES = new HashSet<>(Arrays.asList(
		Boolean.class, Character.class, Byte.class, Short.class, Integer.class, Float.class, Long.class, Double.class, String.class));

	private static final String RECORDED_TEST = "RecordedTest";

	private static final String TEST_FILE = "package <package>;\n\n"
		+ "<imports: {pkg | import <pkg>;\n}>"
		+ "\n\n\n"
		+ "@SuppressWarnings(\"unused\")\n"
		+ "<runner>"
		+ "public class <className> {\n"
		+ "\n"
		+ "  <fields; separator=\"\\n\">\n"
		+ "\n"
		+ "  <before>\n"
		+ "\n"
		+ "  <methods; separator=\"\\n\">"
		+ "\n}";

	private static final String RUNNER = "@RunWith(<runner>.class)\n";

	private static final String RECORDED_INPUT = "@RecordInput(value={<classes : {class | \"<class>\"};separator=\", \">}, signatures={<signatures : {sig | \"<sig>\"};separator=\", \">})\n";
	private static final String RECORDED_OUTPUT = "@RecordOutput(value={<classes : {class | \"<class>\"};separator=\", \">}, signatures={<signatures : {sig | \"<sig>\"};separator=\", \">})\n";

	private static final String BEFORE_TEMPLATE = "@Before\n"
		+ "public void before() throws Exception {\n"
		+ "  <statements;separator=\"\\n\">\n"
		+ "}\n";

	private static final String TEST_TEMPLATE = "@Test\n"
		+ "<annotations:{annotation | <annotation>\n}>"
		+ "public void test<testName>() throws Exception {\n"
		+ "  <statements;separator=\"\\n\">\n"
		+ "}\n";

	private static final String BEGIN_ARRANGE = "\n//Arrange";
	private static final String BEGIN_ACT = "\n//Act";
	private static final String BEGIN_ASSERT = "\n//Assert";

	private ExecutorService executor;
	private DeserializerFactory setup;
	private DeserializerFactory matcher;
	private Map<ClassDescriptor, TestGeneratorContext> tests;
	private Set<String> fields;
	private Set<String> inputClasses;
	private Set<String> outputClasses;
	private Set<String> inputSignatures;
	private Set<String> outputSignatures;

	public TestGenerator() {
		this.executor = Executors.newSingleThreadExecutor(new TestrecorderThreadFactory("$consume"));

		this.setup = new SetupGenerators.Factory();
		this.matcher = new MatcherGenerators.Factory();

		this.tests = synchronizedMap(new LinkedHashMap<>());
		this.fields = new LinkedHashSet<>();
		this.inputClasses = new LinkedHashSet<>();
		this.outputClasses = new LinkedHashSet<>();
		this.inputSignatures = new LinkedHashSet<>();
		this.outputSignatures = new LinkedHashSet<>();
	}

	@Override
	public void close() {
		executor.shutdown();
	}

	public String generateBefore(List<String> statements) {
		ST test = new ST(BEFORE_TEMPLATE);
		test.add("statements", statements);
		return test.render();
	}

	public void setSetup(DeserializerFactory setup) {
		this.setup = setup;
	}

	public void setMatcher(DeserializerFactory matcher) {
		this.matcher = matcher;
	}

	@Override
	public synchronized void accept(ContextSnapshot snapshot) {
		executor.submit(() -> {
			try {
				Class<?> thisType = baseType(snapshot.getThisType());
				while (thisType.getEnclosingClass() != null) {
					thisType = thisType.getEnclosingClass();
				}
				ClassDescriptor baseType = ClassDescriptor.of(thisType);
				TestGeneratorContext context = tests.computeIfAbsent(baseType, key -> new TestGeneratorContext(key));
				MethodGenerator methodGenerator = new MethodGenerator(snapshot, context)
					.generateArrange()
					.generateAct()
					.generateAssert();

				context.add(methodGenerator.generateTest());
			} catch (Throwable e) {
				System.out.println("failed generating test for " + snapshot.getMethodName() + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
			}

		});
	}

	public void writeResults(Path dir) {
		for (ClassDescriptor clazz : tests.keySet()) {

			String rendered = renderTest(clazz);

			try {
				Path testfile = locateTestFile(dir, clazz);
				System.out.println("writing tests to " + testfile);
				try (Writer writer = Files.newBufferedWriter(testfile, StandardCharsets.UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)) {
					writer.write(rendered);
				}
			} catch (IOException e) {
				System.out.println(rendered);
			}
		}
	}

	public void clearResults() {
		this.tests.clear();
		this.fields = new LinkedHashSet<>();
		this.inputClasses = new LinkedHashSet<>();
		this.outputClasses = new LinkedHashSet<>();
		this.inputSignatures = new LinkedHashSet<>();
		this.outputSignatures = new LinkedHashSet<>();
	}

	private Path locateTestFile(Path dir, ClassDescriptor clazz) throws IOException {
		String pkg = clazz.getPackage();
		String className = computeClassName(clazz);
		Path testpackage = dir.resolve(pkg.replace('.', '/'));

		Files.createDirectories(testpackage);

		return testpackage.resolve(className + ".java");
	}

	public Set<String> testsFor(Class<?> clazz) {
		return testsFor(ClassDescriptor.of(clazz));
	}

	public Set<String> testsFor(ClassDescriptor clazz) {
		TestGeneratorContext context = getContext(clazz);
		return context.getTests();
	}

	public TestGeneratorContext getContext(ClassDescriptor clazz) {
		return tests.getOrDefault(clazz, new TestGeneratorContext(clazz));
	}

	public String renderTest(Class<?> clazz) {
		return renderTest(ClassDescriptor.of(clazz));
	}

	public String renderTest(ClassDescriptor clazz) {
		TestGeneratorContext context = getContext(clazz);

		ST file = new ST(TEST_FILE);
		file.add("package", context.getPackage());
		file.add("runner", computeRunner());
		file.add("className", computeClassName(clazz));
		file.add("fields", fields);
		file.add("before", computeBefore(context));
		file.add("methods", context.getTests());
		file.add("imports", context.getImports());

		return file.render();
	}

	private String computeRunner() {
		if (outputClasses.isEmpty() && inputClasses.isEmpty()) {
			return null;
		}
		ServiceLoader<TestRecorderAgentInitializer> loader = ServiceLoader.load(TestRecorderAgentInitializer.class);

		for (TestRecorderAgentInitializer initializer : loader) {
			if (!outputClasses.isEmpty()) {
				outputClasses.add(initializer.getClass().getCanonicalName());
			}
			if (!inputClasses.isEmpty()) {
				inputClasses.add(initializer.getClass().getCanonicalName());
			}
		}

		ST runner = new ST(RUNNER);
		runner.add("runner", IORecorder.class.getSimpleName());

		ST recordedInput = new ST(RECORDED_INPUT);
		recordedInput.add("classes", inputClasses);
		recordedInput.add("signatures", inputSignatures);

		ST recordedOutput = new ST(RECORDED_OUTPUT);
		recordedOutput.add("classes", outputClasses);
		recordedOutput.add("signatures", outputSignatures);

		return runner.render()
			+ (inputClasses.isEmpty() ? "" : recordedInput.render())
			+ (outputClasses.isEmpty() ? "" : recordedOutput.render());
	}

	private String computeBefore(TestGeneratorContext context) {
		TypeManager types = context.getTypes();
		types.registerType(Before.class);

		ServiceLoader<TestRecorderAgentInitializer> loader = ServiceLoader.load(TestRecorderAgentInitializer.class);

		List<String> statements = new ArrayList<>();
		for (TestRecorderAgentInitializer initializer : loader) {
			types.registerType(initializer.getClass());
			String initObject = newObject(types.getConstructorTypeName(initializer.getClass()));
			String initStmt = callMethodStatement(initObject, "run");
			statements.add(initStmt);
		}
		return generateBefore(statements);
	}

	public String computeClassName(ClassDescriptor clazz) {
		return clazz.getSimpleName() + RECORDED_TEST;
	}

	public static TestGenerator fromRecorded() {
		SnapshotConsumer consumer = SnapshotManager.MANAGER.getMethodConsumer();
		if (!(consumer instanceof TestGenerator)) {
			return null;
		}
		TestGenerator testGenerator = (TestGenerator) consumer;
		return testGenerator.await();
	}

	public TestGenerator await() {
		try {
			Future<TestGenerator> waiting = executor.submit(() -> this);
			return waiting.get();
		} catch (InterruptedException | ExecutionException e) {
			return null;
		}
	}

	public void andThen(Runnable runnable) {
		try {
			Future<Void> waiting = executor.submit(runnable, null);
			waiting.get();
		} catch (InterruptedException | ExecutionException e) {
		}
	}

	private class MethodGenerator {

		private LocalVariableNameGenerator locals;

		private ContextSnapshot snapshot;
		private TestGeneratorContext context;

		private List<String> statements;

		private String base;
		private List<String> args;
		private String result;
		private String error;

		public MethodGenerator(ContextSnapshot snapshot, TestGeneratorContext context) {
			this.snapshot = snapshot;
			this.context = context;
			this.locals = new LocalVariableNameGenerator();
			this.statements = new ArrayList<>();
		}

		public MethodGenerator generateArrange() {
			TypeManager types = context.getTypes();
			statements.add(BEGIN_ARRANGE);

			List<SerializedOutput> serializedOutput = snapshot.getExpectOutput();
			if (serializedOutput != null && !serializedOutput.isEmpty()) {
				types.registerTypes(RunWith.class, RecordOutput.class, IORecorder.class, ExpectedOutput.class);
				fields.add(fieldDeclaration("public", ExpectedOutput.class.getSimpleName(), "expectedOutput"));

				List<String> methods = new ArrayList<>();
				for (SerializedOutput out : serializedOutput) {
					types.registerImport(out.getDeclaringClass());
					outputClasses.add(out.getDeclaringClass().getTypeName());
					outputSignatures.add(out.getSignature());

					List<Computation> args = Stream.of(out.getValues())
						.map(arg -> arg.accept(matcher.create(locals, types)))
						.collect(toList());

					statements.addAll(args.stream()
						.flatMap(arg -> arg.getStatements().stream())
						.collect(toList()));

					List<String> arguments = Stream.concat(
						asList(classOf(out.getDeclaringClass().getSimpleName()), stringOf(out.getName())).stream(),
						args.stream()
							.map(arg -> arg.getValue()))
						.collect(toList());

					methods.add(callLocalMethod("expect", arguments));
				}
				String outputExpectation = callMethodChainStatement("expectedOutput", methods);
				statements.add(outputExpectation);
			}
			List<SerializedInput> serializedInput = snapshot.getSetupInput();
			if (serializedInput != null && !serializedInput.isEmpty()) {
				types.registerTypes(RunWith.class, RecordInput.class, IORecorder.class, SetupInput.class);
				fields.add(fieldDeclaration("public", SetupInput.class.getSimpleName(), "setupInput"));

				List<String> methods = new ArrayList<>();
				for (SerializedInput in : serializedInput) {
					Class<?> declaringClass = deanonymized(in.getDeclaringClass());
					types.registerImport(declaringClass);
					inputClasses.add(declaringClass.getTypeName());
					inputSignatures.add(in.getSignature());

					Computation result = null;
					if (in.getResult() != null) {
						result = in.getResult().accept(setup.create(locals, types));
						statements.addAll(result.getStatements());
					}

					List<Computation> args = Stream.of(in.getValues())
						.map(arg -> arg.accept(setup.create(locals, types)))
						.collect(toList());

					statements.addAll(args.stream()
						.flatMap(arg -> arg.getStatements().stream())
						.collect(toList()));

					List<String> arguments = new ArrayList<>();
					arguments.add(classOf(declaringClass.getSimpleName()));
					arguments.add(stringOf(in.getName()));
					if (result != null) {
						arguments.add(result.getValue());
					} else {
						arguments.add("null");
					}
					arguments.addAll(args.stream()
						.map(arg -> arg.getValue())
						.collect(toList()));

					methods.add(callLocalMethod("provide", arguments));
				}
				String inputSetup = callMethodChainStatement("setupInput", methods);
				statements.add(inputSetup);
			}

			Deserializer<Computation> setupCode = setup.create(locals, types);
			Computation setupThis = snapshot.getSetupThis() != null
				? snapshot.getSetupThis().accept(setupCode)
				: new Computation(types.getVariableTypeName(types.wrapHidden(snapshot.getThisType())), null, true);
			statements.addAll(setupThis.getStatements());

			AnnotatedValue[] snapshotSetupArgs = snapshot.getAnnotatedSetupArgs();
			List<Computation> setupArgs = Stream.of(snapshotSetupArgs)
				.map(arg -> arg.value.accept(setupCode, newContext(arg.annotations)))
				.collect(toList());

			statements.addAll(setupArgs.stream()
				.flatMap(arg -> arg.getStatements().stream())
				.collect(toList()));

			List<Computation> setupGlobals = Stream.of(snapshot.getSetupGlobals())
				.map(global -> assignGlobal(global.getDeclaringClass(), global.getName(), global.getValue().accept(setupCode)))
				.collect(toList());

			statements.addAll(setupGlobals.stream()
				.flatMap(arg -> arg.getStatements().stream())
				.collect(toList()));

			this.base = setupThis.isStored()
				? setupThis.getValue()
				: assign(snapshot.getSetupThis().getType(), setupThis.getValue());
			Pair<Computation, AnnotatedValue>[] arguments = Pair.zip(setupArgs.toArray(new Computation[0]), snapshotSetupArgs);
			this.args = Stream.of(arguments)
				.map(arg -> arg.getElement1().isStored()
					? arg.getElement1().getValue()
					: assign(arg.getElement2().value.getResultType(), arg.getElement1().getValue()))
				.collect(toList());
			return this;
		}

		private Class<?> deanonymized(Class<?> declaringClass) {
			while (declaringClass.isAnonymousClass()) {
				declaringClass = declaringClass.getSuperclass();
			}
			return declaringClass;
		}

		private Computation assignGlobal(Class<?> clazz, String name, Computation global) {
			TypeManager types = context.getTypes();
			List<String> statements = new ArrayList<>(global.getStatements());
			String base = types.getVariableTypeName(clazz);
			statements.add(assignFieldStatement(base, name, global.getValue()));
			String value = fieldAccess(base, name);
			return new Computation(value, global.getType(), true, statements);
		}

		public MethodGenerator generateAct() {
			statements.add(BEGIN_ACT);

			Type resultType = snapshot.getResultType();
			String methodName = snapshot.getMethodName();
			SerializedValue exception = snapshot.getExpectException();

			MethodGenerator gen;
			if (exception != null) {
				gen = new MethodGenerator(snapshot, context);
			} else {
				gen = this;
			}
			String statement = callMethod(base, methodName, args);
			if (resultType != void.class) {
				result = gen.assign(resultType, statement, true);
			} else {
				gen.execute(statement);
			}
			if (exception != null) {
				List<String> exceptionBlock = new ArrayList<>();
				exceptionBlock.addAll(gen.statements);
				if (resultType != void.class) {
					exceptionBlock.add(returnStatement(result));
				}
				error = capture(exceptionBlock, exception.getType());
			}

			return this;
		}

		public MethodGenerator generateAssert() {
			TypeManager types = context.getTypes();
			types.staticImport(Assert.class, "assertThat");
			statements.add(BEGIN_ASSERT);

			if (error == null) {
				List<String> expectResult = Optional.ofNullable(snapshot.getExpectResult())
					.map(o -> o.accept(matcher.create(locals, types), newContext(snapshot.getResultAnnotation())))
					.map(o -> createAssertion(o, result))
					.orElse(emptyList());

				statements.addAll(expectResult);
			} else {
				List<String> expectResult = Optional.ofNullable(snapshot.getExpectException())
					.map(o -> o.accept(matcher.create(locals, types)))
					.map(o -> createAssertion(o, error))
					.orElse(emptyList());

				statements.addAll(expectResult);
			}

			boolean thisChanged = compare(snapshot.getSetupThis(), snapshot.getExpectThis());
			SerializedValue snapshotExpectThis = snapshot.getExpectThis();
			List<String> expectThis = Optional.ofNullable(snapshotExpectThis)
				.map(o -> o.accept(matcher.create(locals, types)))
				.map(o -> createAssertion(o, base, thisChanged))
				.orElse(emptyList());

			statements.addAll(expectThis);

			Boolean[] argsChanged = compare(snapshot.getSetupArgs(),snapshot.getExpectArgs());
			AnnotatedValue[] snapshotExpectArgs = snapshot.getAnnotatedExpectArgs();
			Triple<AnnotatedValue, String, Boolean>[] arguments = Triple.zip(snapshotExpectArgs, args.toArray(new String[0]), argsChanged);
			List<String> expectArgs = Stream.of(arguments)
				.filter(arg -> !(arg.getElement1().value instanceof SerializedLiteral))
				.map(arg -> new Triple<Computation, String, Boolean>(arg.getElement1().value.accept(matcher.create(locals, types), newContext(arg.getElement1().annotations)), arg.getElement2(), arg.getElement3()))
				.filter(arg -> arg.getElement1() != null)
				.map(arg -> createAssertion(arg.getElement1(), arg.getElement2(), arg.getElement3()))
				.flatMap(statements -> statements.stream())
				.collect(toList());

			statements.addAll(expectArgs);

			Boolean[] globalsChanged = compare(snapshot.getExpectGlobals(), snapshot.getExpectGlobals());
			SerializedField[] snashotExpectGlobals = snapshot.getExpectGlobals();
			List<String> expectGlobals = IntStream.range(0, snashotExpectGlobals.length)
				.mapToObj(i -> createAssertion(snashotExpectGlobals[i].getValue().accept(matcher.create(locals, types)),
					fieldAccess(types.getVariableTypeName(snashotExpectGlobals[i].getDeclaringClass()), snashotExpectGlobals[i].getName()), globalsChanged[i]))
				.flatMap(statements -> statements.stream())
				.collect(toList());

			statements.addAll(expectGlobals);

			List<SerializedOutput> serializedOutput = snapshot.getExpectOutput();
			if (serializedOutput != null && !serializedOutput.isEmpty()) {
				statements.add(callMethodStatement("expectedOutput", "verify"));
			}

			return this;
		}

		private Boolean[] compare(SerializedField[] s, SerializedField[] e) {
			Boolean[] changes = new Boolean[s.length];
			for (int i = 0; i < changes.length; i++) {
				changes[i] = compare(s[i].getValue(), e[i].getValue());
			}
			return changes;
		}

		private Boolean[] compare(SerializedValue[] s, SerializedValue[] e) {
			Boolean[] changes = new Boolean[s.length];
			for (int i = 0; i < changes.length; i++) {
				changes[i] = compare(s[i], e[i]);
			}
			return changes;
		}

		private boolean compare(SerializedValue s, SerializedValue e) {
			if (s == e) {
				return true;
			} else if (s == null || e == null) {
				return false;
			}
			Computation sc = s.accept(setup.create(new LocalVariableNameGenerator(), new TypeManager()));
			Computation ec = e.accept(setup.create(new LocalVariableNameGenerator(), new TypeManager()));
			return !ec.getValue().equals(sc.getValue())
				|| !ec.getStatements().equals(sc.getStatements());
		}

		private List<String> createAssertion(Computation matcher, String exp, Boolean changed) {
			List<String> statements = new ArrayList<>();

			statements.addAll(matcher.getStatements());

			if (changed == null) {
				statements.add(callLocalMethodStatement("assertThat", exp, matcher.getValue()));
			} else if (changed){
				statements.add(callLocalMethodStatement("assertThat", asLiteral("expected change:"), exp, matcher.getValue()));
			} else {
				statements.add(callLocalMethodStatement("assertThat", asLiteral("expected no change, but was:"), exp, matcher.getValue()));
			}

			return statements;
		}

		private List<String> createAssertion(Computation matcher, String exp) {
			List<String> statements = new ArrayList<>();

			statements.addAll(matcher.getStatements());

			statements.add(callLocalMethodStatement("assertThat", exp, matcher.getValue()));

			return statements;
		}

		public String assign(Type type, String value) {
			return assign(type, value, false);
		}

		public String assign(Type type, String value, boolean force) {
			TypeManager types = context.getTypes();
			if (isLiteral(type) && !force) {
				return value;
			} else {
				types.registerImport(baseType(type));
				String name = locals.fetchName(type);

				statements.add(assignLocalVariableStatement(types.getVariableTypeName(type), name, value));

				return name;
			}
		}

		public void execute(String value) {
			statements.add(expressionStatement(value));
		}

		public String capture(List<String> capturedStatements, Type type) {
			TypeManager types = context.getTypes();
			types.staticImport(Throwables.class, "capture");
			String name = locals.fetchName(type);

			String exceptionType = types.getRawClass(type);
			String capture = captureException(capturedStatements, exceptionType);

			statements.add(assignLocalVariableStatement(types.getVariableTypeName(type), name, capture));

			return name;
		}

		private boolean isLiteral(Type type) {
			return isPrimitive(type)
				|| LITERAL_TYPES.contains(type);
		}

		public String generateTest() {
			ST test = new ST(TEST_TEMPLATE);
			test.add("annotations", annotations());
			test.add("testName", testName());
			test.add("statements", statements);
			return test.render();
		}

		private List<String> annotations() {
			return Stream.of(snapshot.getResultAnnotation())
				.map(annotation -> transferAnnotation(annotation))
				.filter(Objects::nonNull)
				.collect(toList());
		}

		private String transferAnnotation(Annotation annotation) {
			if (annotation instanceof AnnotateTimestamp) {
				return generateTimestampAnnotation(((AnnotateTimestamp) annotation).format());
			} else if (annotation instanceof AnnotateGroupExpression) {
				return generateGroupAnnotation(((AnnotateGroupExpression) annotation).expression());
			}
			return null;
		}

		private String generateTimestampAnnotation(String format) {
			String date = new SimpleDateFormat(format).format(new Date(snapshot.getTime()));
			TypeManager types = context.getTypes();
			types.registerImport(AnnotatedBy.class);
			return annotation(types.getRawTypeName(AnnotatedBy.class), asList(
				new Pair<>("name", asLiteral("timestamp")),
				new Pair<>("value", asLiteral(date))));
		}

		private String generateGroupAnnotation(String expression) {
			TypeManager types = context.getTypes();
			types.registerImport(AnnotatedBy.class);
			Optional<SerializedValue> serialized = new SerializedValueEvaluator(expression).applyTo(snapshot.getSetupThis());
			return serialized
				.filter(value -> value instanceof SerializedLiteral)
				.map(value -> ((SerializedLiteral) value).getValue())
				.map(value -> annotation(types.getRawTypeName(AnnotatedBy.class), asList(
					new Pair<>("name", asLiteral("group")),
					new Pair<>("value", asLiteral(value.toString())))))
				.orElse(null);
		}

		private String testName() {
			String testName = snapshot.getMethodName();

			return toUpperCase(testName.charAt(0)) + testName.substring(1) + context.size();
		}

	}

}
