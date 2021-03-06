package net.amygdalum.testrecorder.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.amygdalum.testrecorder.util.testobjects.Collections;
import net.amygdalum.testrecorder.util.testobjects.Simple;
import net.amygdalum.testrecorder.util.testobjects.SimpleMisleadingFieldName;

public class MethodDescriptionTest {

	private Methods methodByDescriptionResult;
	private Methods methodByDescriptionArguments;

	@BeforeEach
	public void before() throws Exception {
		methodByDescriptionResult = Methods.byDescription("net/amygdalum/testrecorder/util/testobjects/Simple", "getStr", "()Ljava/lang/String;");
		methodByDescriptionArguments = Methods.byDescription("net/amygdalum/testrecorder/util/testobjects/Collections", "arrayList", "([Ljava/lang/Object;)Ljava/util/ArrayList;");
	}
	
	@Test
	public void testMatchesReflectiveMethod() throws Exception {
		assertThat(methodByDescriptionResult.matches(Simple.class.getDeclaredMethod("getStr"))).isTrue(); 
		assertThat(methodByDescriptionResult.matches(SimpleMisleadingFieldName.class.getDeclaredMethod("getStr"))).isFalse(); 

		assertThat(methodByDescriptionArguments.matches(Collections.class.getDeclaredMethod("arrayList", Object[].class))).isTrue(); 
		assertThat(methodByDescriptionArguments.matches(Simple.class.getDeclaredMethod("getStr"))).isFalse(); 
	}

	@Test
	public void testMatchesMethodDescriptor() throws Exception {
		assertThat(methodByDescriptionResult.matches("net/amygdalum/testrecorder/util/testobjects/Simple", "getStr", "()Ljava/lang/String;")).isTrue(); 
		assertThat(methodByDescriptionResult.matches("net/amygdalum/testrecorder/util/testobjects/SimpleMisleadingFieldName", "getStr", "()Ljava/lang/String;")).isFalse(); 

		assertThat(methodByDescriptionArguments.matches("net/amygdalum/testrecorder/util/testobjects/Collections", "arrayList", "([Ljava/lang/Object;)Ljava/util/ArrayList;")).isTrue(); 
		assertThat(methodByDescriptionArguments.matches("net/amygdalum/testrecorder/util/testobjects/Simple", "getStr", "()Ljava/lang/String;")).isFalse(); 
	}

}
