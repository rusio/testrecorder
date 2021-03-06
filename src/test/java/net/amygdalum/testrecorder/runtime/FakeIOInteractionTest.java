package net.amygdalum.testrecorder.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.doReturn;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import net.amygdalum.testrecorder.runtime.FakeIO.Input;
import net.amygdalum.testrecorder.runtime.FakeIO.InvocationData;
import net.amygdalum.testrecorder.runtime.FakeIO.Output;
import net.amygdalum.testrecorder.util.testobjects.Abstract;
import net.amygdalum.testrecorder.util.testobjects.Bean;
import net.amygdalum.testrecorder.util.testobjects.Implementor;
import net.amygdalum.testrecorder.util.testobjects.Sub;
import net.amygdalum.testrecorder.util.testobjects.Super;

public class FakeIOInteractionTest {

	@Test
	public void testResolveStaticCase() throws Exception {
		MyInteraction myInteraction = new MyInteraction(FakeIO.fake(Bean.class), "setAttribute", "(Ljava/lang/String;)V");

		assertThat(myInteraction.resolve(Bean.class)).isSameAs(Bean.class);
	}

	@Test
	public void testResolveInheritedCase() throws Exception {
		MyInteraction myInteraction = new MyInteraction(FakeIO.fake(Sub.class), "getStr", "()Ljava/lang/String;");

		assertThat(myInteraction.resolve(Sub.class)).isSameAs(Super.class);
	}

	@Test
	public void testResolveAbstractCase() throws Exception {
		MyInteraction myInteraction = new MyInteraction(FakeIO.fake(Abstract.class), "getAbstract", "()Ljava/lang/String;");

		assertThat(myInteraction.resolve(Implementor.class)).isSameAs(Implementor.class);
	}

	@Test
	public void testResolveBrokenName() throws Exception {
		MyInteraction myInteraction = new MyInteraction(FakeIO.fake(Object.class), "notExistingMethod", "()V");

		assertThatThrownBy(() -> myInteraction.resolve(Object.class)).isInstanceOf(RuntimeException.class);
	}

	@Test
	void testMatchesIfFakeAndInteractionMatch() throws Exception {
		MyInteraction myInteraction = new MyInteraction(FakeIO.fake(Bean.class), "setAttribute", "(Ljava/lang/String;)V");

		StackTraceElement[] stackTrace = new StackTraceElement[] {
			new StackTraceElement("net.amygdalum.testrecorder.util.testobjects.Bean", "setAttribute", "Bean.java", 0),
			new StackTraceElement("net.amygdalum.testrecorder.runtime.FakeIOInteractionTest", "testMatchesIfFakeAndInteractionMatch", "FakeIOInteractionTest.java", 0)
		};
		assertThat(myInteraction.matches(Invocation.capture(stackTrace, new Bean(), Bean.class, "setAttribute", "(Ljava/lang/String;)V"))).isTrue();
		assertThat(myInteraction.matches(Invocation.capture(stackTrace, new Bean(), Bean.class, "setOtherAttribute", "(Ljava/lang/String;)V"))).isFalse();
		assertThat(myInteraction.matches(Invocation.capture(stackTrace, new Object(), Object.class, "setAttribute", "(Ljava/lang/String;)V"))).isFalse();
		assertThat(myInteraction.matches(Invocation.capture(stackTrace, new Bean(), Bean.class, "setAttribute", "(I)V"))).isFalse();
	}

	@Test
	void testCallFilteringRuntimeClasses() throws Exception {
		MyInteraction myInteraction = new MyInteraction(FakeIO.fake(Bean.class), "setAttribute", "(Ljava/lang/String;)V");
		myInteraction.add(FakeIOInteractionTest.class, "testCallFiltered", 0, null);

		StackTraceElement[] stackTrace = new StackTraceElement[] {
			new StackTraceElement("net.amygdalum.testrecorder.util.testobjects.Bean", "setAttribute", "Bean.java", 0),
			new StackTraceElement("net.amygdalum.testrecorder.runtime.FakeIO", "call", "FakeIO.java", 0)
		};
		Object result = myInteraction.call(Invocation.capture(stackTrace, new Bean(), Bean.class, "setAttribute", "(Ljava/lang/String;)V"), new Object[] { "mystr" });

		assertThat(result).isNull();
	}

	@Test
	void testCallFilteringTestingClasses() throws Exception {
		MyInteraction myInteraction = new MyInteraction(FakeIO.fake(Bean.class), "setAttribute", "(Ljava/lang/String;)V");
		myInteraction.add(FakeIOInteractionTest.class, "testCallFiltered", 0, null);

		StackTraceElement[] stackTrace = new StackTraceElement[] {
			new StackTraceElement("net.amygdalum.testrecorder.util.testobjects.Bean", "setAttribute", "Bean.java", 0),
			new StackTraceElement("net.amygdalum.testrecorder.testing.hamcrest.GenericMatcher", "matches", "GenericMatcher.java", 0)
		};
		Object result = myInteraction.call(Invocation.capture(stackTrace, new Bean(), Bean.class, "setAttribute", "(Ljava/lang/String;)V"), new Object[] { "mystr" });

		assertThat(result).isNull();
	}

	@Test
	void testCallMatching() throws Exception {
		Object RESULT = new Object();

		MyInteraction myInteraction = new MyInteraction(FakeIO.fake(Bean.class), "setAttribute", "(Ljava/lang/String;)V");
		myInteraction.add(Bean.class, "setAttribute", 0, null);
		myInteraction.setResult(RESULT);

		StackTraceElement[] stackTrace = new StackTraceElement[] {
			new StackTraceElement("net.amygdalum.testrecorder.util.testobjects.Bean", "setAttribute", "Bean.java", 0),
			new StackTraceElement("net.amygdalum.testrecorder.util.testobjects.Bean", "setAttribute", "Bean.java", 0)
		};
		Object result = myInteraction.call(Invocation.capture(stackTrace, new Bean(), Bean.class, "setAttribute", "(Ljava/lang/String;)V"), new Object[] { "mystr" });

		assertThat(result).isSameAs(RESULT);
	}

	@Test
	void testCallFailing() throws Exception {
		Object RESULT = new Object();

		MyInteraction myInteraction = new MyInteraction(FakeIO.fake(Bean.class), "setAttribute", "(Ljava/lang/String;)V");
		myInteraction.add(Bean.class, "setAttribute", 0, null);
		myInteraction.setResult(RESULT);

		StackTraceElement[] stackTrace = new StackTraceElement[] {
			new StackTraceElement("net.amygdalum.testrecorder.util.testobjects.Bean", "setAttribute", "Bean.java", 0),
			new StackTraceElement("net.amygdalum.testrecorder.util.testobjects.Simple", "setAttribute", "Bean.java", 0)
		};
		assertThatThrownBy(() -> myInteraction.call(Invocation.capture(stackTrace, new Bean(), Bean.class, "setAttribute", "(Ljava/lang/String;)V"), new Object[] { "mystr" }))
			.isInstanceOf(AssertionError.class);

	}

	@Test
	public void testGetMethod() throws Exception {
		MyInteraction myInteraction = new MyInteraction(FakeIO.fake(Bean.class), "setAttribute", "(Ljava/lang/String;)V");
		
		assertThat(myInteraction.getMethod()).isEqualTo("setAttribute(Ljava/lang/String;)V");
	}

	@Test
	public void testFakeInput() throws Exception {
		FakeIO fakeIO = Mockito.mock(FakeIO.class);
		doReturn(new Input(fakeIO, "fakedInput","()V")).when(fakeIO).fakeInput(Mockito.any(Aspect.class));
		
		MyInteraction myInteraction = new MyInteraction(fakeIO, "setAttribute", "(Ljava/lang/String;)V");
		
		assertThat(myInteraction.fakeInput(new Aspect() {
			@SuppressWarnings("unused")
			public void fakedInput() {
			}
		}).getMethod()).isEqualTo("fakedInput()V");
	}

	@Test
	public void testFakeOutput() throws Exception {
		FakeIO fakeIO = Mockito.mock(FakeIO.class);
		doReturn(new Output(fakeIO, "fakedOutput","()V")).when(fakeIO).fakeOutput(Mockito.any(Aspect.class));
		
		MyInteraction myInteraction = new MyInteraction(fakeIO, "setAttribute", "(Ljava/lang/String;)V");
		
		assertThat(myInteraction.fakeOutput(new Aspect() {
			@SuppressWarnings("unused")
			public void fakedOutput() {
			}
		}).getMethod()).isEqualTo("fakedOutput()V");
	}
	
	@Test
	public void testSetup() throws Exception {
		FakeIO fakeIO = Mockito.mock(FakeIO.class);
		doReturn(fakeIO).when(fakeIO).setup();
		
		MyInteraction myInteraction = new MyInteraction(fakeIO, "setAttribute", "(Ljava/lang/String;)V");
		
		assertThat(myInteraction.setup()).isSameAs(fakeIO);
	}
	
	@Test
	public void testSignatureFor() throws Exception {
		FakeIO fakeIO = Mockito.mock(FakeIO.class);
		doReturn(fakeIO).when(fakeIO).setup();
		
		MyInteraction myInteraction = new MyInteraction(fakeIO, "setAttribute", "(Ljava/lang/String;)V");
		
		assertThat(myInteraction.signatureFor(new Object[] { "str", Long.valueOf(4), Byte.valueOf((byte) 1)})).isEqualTo("setAttribute(\"str\", <4L>, <1>)");
	}

	@Test
	public void testSignatureForMatchers() throws Exception {
		FakeIO fakeIO = Mockito.mock(FakeIO.class);
		doReturn(fakeIO).when(fakeIO).setup();
		
		MyInteraction myInteraction = new MyInteraction(fakeIO, "setAttribute", "(Ljava/lang/String;)V");
		
		assertThat(myInteraction.signatureFor(new Object[] { "str", notNullValue(), instanceOf(List.class)})).isEqualTo("setAttribute(\"str\", not null, an instance of java.util.List)");
	}
	
	private static class MyInteraction extends FakeIO.Interaction {

		private Object result;

		public MyInteraction(FakeIO io, String methodName, String methodDesc) {
			super(io, methodName, methodDesc);
		}

		public void setResult(Object result) {
			this.result = result;
		}

		@Override
		public Object call(InvocationData data, Object[] arguments) {
			return result;
		}

		@Override
		public void verify() {
		}

	}
}
