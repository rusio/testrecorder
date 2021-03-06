package net.amygdalum.testrecorder;

import static net.amygdalum.testrecorder.values.SerializedLiteral.literal;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.rules.TemporaryFolder;

import net.amygdalum.testrecorder.deserializers.Computation;
import net.amygdalum.testrecorder.deserializers.DeserializerFactory;
import net.amygdalum.testrecorder.deserializers.LocalVariableNameGenerator;
import net.amygdalum.testrecorder.deserializers.TestComputationValueVisitor;
import net.amygdalum.testrecorder.deserializers.TypeManager;
import net.amygdalum.testrecorder.types.Deserializer;
import net.amygdalum.testrecorder.values.SerializedField;
import net.amygdalum.testrecorder.values.SerializedObject;

@EnableRuleMigrationSupport
public class TestGeneratorTest {

	private static SnapshotManager saveManager;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private TestGenerator testGenerator;

	@BeforeAll
	public static void beforeClass() throws Exception {
		saveManager = SnapshotManager.MANAGER;
	}

	@AfterAll
	public static void afterClass() throws Exception {
		SnapshotManager.MANAGER = saveManager;
	}

	@BeforeEach
	public void before() throws Exception {
		testGenerator = new TestGenerator();
	}

	@Test
	public void testAccept() throws Exception {
		ContextSnapshot snapshot = contextSnapshot(MyClass.class, int.class, "intMethod", int.class);
		snapshot.setSetupThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 12))));
		snapshot.setSetupArgs(literal(int.class, 16));
		snapshot.setSetupGlobals(new SerializedField[0]);
		snapshot.setExpectThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 8))));
		snapshot.setExpectArgs(literal(int.class, 16));
		snapshot.setExpectResult(literal(int.class, 22));
		snapshot.setExpectGlobals(new SerializedField[0]);

		testGenerator.accept(snapshot);

		testGenerator.await();
		assertThat(testGenerator.testsFor(TestGeneratorTest.class))
			.hasSize(1)
			.anySatisfy(test -> {
				assertThat(test).containsSequence("int field = 12;",
					"intMethod(16);",
					"equalTo(22)",
					"int field = 8;");
			});
	}

	@Test
	public void testSuppressesWarnings() throws Exception {
		ContextSnapshot snapshot = contextSnapshot(MyClass.class, int.class, "intMethod", int.class);
		snapshot.setSetupThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 12))));
		snapshot.setSetupArgs(literal(int.class, 16));
		snapshot.setSetupGlobals(new SerializedField[0]);
		snapshot.setExpectThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 8))));
		snapshot.setExpectArgs(literal(int.class, 16));
		snapshot.setExpectResult(literal(int.class, 22));
		snapshot.setExpectGlobals(new SerializedField[0]);

		testGenerator.accept(snapshot);

		testGenerator.await();
		assertThat(testGenerator.renderTest(MyClass.class).getTestCode()).containsSequence("@SuppressWarnings(\"unused\")" + System.lineSeparator() + "public class");
	}

	@Test
	public void testSetSetup() throws Exception {
		testGenerator.setSetup(new DeserializerFactory() {

			@Override
			public Deserializer<Computation> create(LocalVariableNameGenerator locals, TypeManager types) {
				return new TestComputationValueVisitor();
			}

			@Override
			public Type resultType(Type value) {
				return value;
			}
		});
		ContextSnapshot snapshot = contextSnapshot(MyClass.class, int.class, "intMethod", int.class);
		snapshot.setSetupThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 12))));
		snapshot.setSetupArgs(literal(int.class, 16));
		snapshot.setSetupGlobals(new SerializedField[0]);
		snapshot.setExpectThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 8))));
		snapshot.setExpectArgs(literal(int.class, 16));
		snapshot.setExpectResult(literal(int.class, 22));
		snapshot.setExpectGlobals(new SerializedField[0]);

		testGenerator.accept(snapshot);

		testGenerator.await();
		assertThat(testGenerator.testsFor(TestGeneratorTest.class))
			.hasSize(1)
			.anySatisfy(test -> {
				assertThat(test).containsSequence("(net.amygdalum.testrecorder.TestGeneratorTest$MyClass/",
					"int field: 12",
					"intMethod((16))",
					"equalTo(22)",
					"int field = 8;");
			});
	}

	@Test
	public void testSetMatcher() throws Exception {
		testGenerator.setMatcher(new DeserializerFactory() {

			@Override
			public Deserializer<Computation> create(LocalVariableNameGenerator locals, TypeManager types) {
				return new TestComputationValueVisitor();
			}

			@Override
			public Type resultType(Type value) {
				return value;
			}
		});
		ContextSnapshot snapshot = contextSnapshot(MyClass.class, int.class, "intMethod", int.class);
		snapshot.setSetupThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 12))));
		snapshot.setSetupArgs(literal(int.class, 16));
		snapshot.setSetupGlobals(new SerializedField[0]);
		snapshot.setExpectThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 8))));
		snapshot.setExpectArgs(literal(int.class, 16));
		snapshot.setExpectResult(literal(int.class, 22));
		snapshot.setExpectGlobals(new SerializedField[0]);

		testGenerator.accept(snapshot);

		testGenerator.await();
		assertThat(testGenerator.testsFor(TestGeneratorTest.class))
			.hasSize(1)
			.anySatisfy(test -> {
				assertThat(test).containsSequence(
					"int field = 12;",
					"intMethod(16);",
					"(22)",
					"(net.amygdalum.testrecorder.TestGeneratorTest$MyClass/",
					"int field: 8");
			});
	}

	@Test
	public void testTestsForEmpty() throws Exception {
		assertThat(testGenerator.testsFor(MyClass.class)).isEmpty();
	}

	@Test
	public void testTestsForAfterClear() throws Exception {
		ContextSnapshot snapshot = contextSnapshot(MyClass.class, int.class, "intMethod", int.class);
		snapshot.setSetupThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 12))));
		snapshot.setSetupArgs(literal(int.class, 16));
		snapshot.setSetupGlobals(new SerializedField[0]);
		snapshot.setExpectThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 8))));
		snapshot.setExpectArgs(literal(int.class, 16));
		snapshot.setExpectResult(literal(int.class, 22));
		snapshot.setExpectGlobals(new SerializedField[0]);
		testGenerator.accept(snapshot);

		testGenerator.clearResults();

		assertThat(testGenerator.testsFor(MyClass.class)).isEmpty();
	}

	@Test
	public void testRenderCode() throws Exception {
		ContextSnapshot snapshot1 = contextSnapshot(MyClass.class, int.class, "intMethod", int.class);
		snapshot1.setSetupThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 12))));
		snapshot1.setSetupArgs(literal(int.class, 16));
		snapshot1.setSetupGlobals(new SerializedField[0]);
		snapshot1.setExpectThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 8))));
		snapshot1.setExpectArgs(literal(int.class, 16));
		snapshot1.setExpectResult(literal(int.class, 22));
		snapshot1.setExpectGlobals(new SerializedField[0]);
		ContextSnapshot snapshot2 = contextSnapshot(MyClass.class, int.class, "intMethod", int.class);
		snapshot2.setSetupThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 13))));
		snapshot2.setSetupArgs(literal(int.class, 17));
		snapshot2.setSetupGlobals(new SerializedField[0]);
		snapshot2.setExpectThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 9))));
		snapshot2.setExpectArgs(literal(int.class, 17));
		snapshot2.setExpectResult(literal(int.class, 23));
		snapshot2.setExpectGlobals(new SerializedField[0]);

		testGenerator.accept(snapshot1);
		testGenerator.accept(snapshot2);

		testGenerator.await();
		assertThat(testGenerator.renderTest(TestGeneratorTest.class).getTestCode()).containsSequence(
			"int field = 12;",
			"intMethod(16);",
			"equalTo(22)",
			"int field = 8;",
			"int field = 13;",
			"intMethod(17);",
			"equalTo(23)",
			"int field = 9;");
	}

	@Test
	public void testComputeClassName() throws Exception {
		assertThat(testGenerator.computeClassName(ClassDescriptor.of(MyClass.class))).isEqualTo("MyClassRecordedTest");
	}

	@Test
	public void testFromRecordedIfConsumerIsNull() throws Exception {
		SnapshotManager.MANAGER = new SnapshotManager(new DefaultTestRecorderAgentConfig() {

			@Override
			public SnapshotConsumer getSnapshotConsumer() {
				return null;
			}
		});
		assertThat(TestGenerator.fromRecorded()).isNull();
		assertThat(TestGenerator.fromRecorded()).isNull();
	}

	@Test
	public void testFromRecordedIfConsumerIsNonNull() throws Exception {
		TestGenerator tg = new TestGenerator();
		SnapshotManager.MANAGER = new SnapshotManager(new DefaultTestRecorderAgentConfig() {

			@Override
			public SnapshotConsumer getSnapshotConsumer() {
				return tg;
			}
		});
		assertThat(TestGenerator.fromRecorded()).isSameAs(tg);
		assertThat(TestGenerator.fromRecorded()).isSameAs(tg);
	}

	@Test
	public void testFromRecordedIfConsumerIsNotTestGenerator() throws Exception {
		SnapshotManager.MANAGER = new SnapshotManager(new DefaultTestRecorderAgentConfig() {

			@Override
			public SnapshotConsumer getSnapshotConsumer() {
				return new SnapshotConsumer() {

					@Override
					public void accept(ContextSnapshot snapshot) {
					}

					@Override
					public void close() {
					}
				};
			}
		});
		assertThat(TestGenerator.fromRecorded()).isNull();
	}

	@Test
	public void testWriteResults() throws Exception {
		ContextSnapshot snapshot = contextSnapshot(MyClass.class, int.class, "intMethod", int.class);
		snapshot.setSetupThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 12))));
		snapshot.setSetupArgs(literal(int.class, 16));
		snapshot.setSetupGlobals(new SerializedField[0]);
		snapshot.setExpectThis(objectOf(MyClass.class, new SerializedField(MyClass.class, "field", int.class, literal(int.class, 8))));
		snapshot.setExpectArgs(literal(int.class, 16));
		snapshot.setExpectResult(literal(int.class, 22));
		snapshot.setExpectGlobals(new SerializedField[0]);

		testGenerator.accept(snapshot);

		testGenerator.await();
		testGenerator.writeResults(folder.getRoot().toPath());

		assertThat(Files.exists(folder.getRoot().toPath().resolve("net/amygdalum/testrecorder/TestGeneratorTestRecordedTest.java"))).isTrue();
	}

	private ContextSnapshot contextSnapshot(Class<?> declaringClass, Type resultType, String methodName, Type... argumentTypes) {
		return new ContextSnapshot(0, "key", new MethodSignature(declaringClass, new Annotation[0], resultType, methodName, new Annotation[0][0], argumentTypes));
	}

	private SerializedObject objectOf(Class<MyClass> type, SerializedField... fields) {
		SerializedObject setupThis = new SerializedObject(type);
		for (SerializedField field : fields) {
			setupThis.addField(field);
		}
		return setupThis;
	}

	@SuppressWarnings("unused")
	private static class MyClass {

		private int field;

		public int intMethod(int arg) {
			return field + arg;
		}
	}

}
