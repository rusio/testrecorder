package net.amygdalum.testrecorder.deserializers;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import net.amygdalum.testrecorder.Deserializer;
import net.amygdalum.testrecorder.SerializedImmutableType;
import net.amygdalum.testrecorder.SerializedReferenceType;
import net.amygdalum.testrecorder.SerializedValue;
import net.amygdalum.testrecorder.util.Types;
import net.amygdalum.testrecorder.values.SerializedField;

public class DeserializerContext {

	public static final DeserializerContext NULL = new DeserializerContext();

	private DeserializerContext parent;
	private Map<Class<?>, SerializedReferenceType> globals;
	private Map<SerializedValue, Set<SerializedReferenceType>> backReferences;
	private Map<SerializedValue, Set<SerializedValue>> closures;
	private List<Object> hints;

	public DeserializerContext() {
		this.globals = new IdentityHashMap<>();
		this.backReferences = new IdentityHashMap<>();
		this.closures = new IdentityHashMap<>();
		this.hints = emptyList();
	}

	private DeserializerContext(DeserializerContext parent, Collection<Object> hints) {
		this.parent = parent;
		this.globals = parent.globals;
		this.backReferences = parent.backReferences;
		this.closures = parent.closures;
		this.hints = new ArrayList<>(hints);
	}

	public DeserializerContext getParent() {
		return parent;
	}

	@SuppressWarnings("unchecked")
	public <T> DeserializerContext newWithHints(T... hints) {
		return new DeserializerContext(this, asList(hints));
	}

	public <T> Optional<T> getHint(Class<T> clazz) {
		return hints.stream()
			.filter(hint -> clazz.isInstance(hint))
			.map(hint -> clazz.cast(hint))
			.findFirst();
	}

	public <T> Stream<T> getHints(Class<T> clazz) {
		return hints.stream()
			.filter(hint -> clazz.isInstance(hint))
			.map(hint -> clazz.cast(hint));
	}

	public int refCount(SerializedValue value) {
		int size = 0;
		Set<SerializedReferenceType> references = backReferences.get(value);
		if (references != null) {
			size += references.size();
		}
		return size;
	}

	public void ref(SerializedReferenceType value, SerializedValue referencedValue) {
		backReferences.compute(referencedValue, (referenced, backreferenced) -> {
			if (backreferenced == null) {
				backreferenced = new HashSet<>();
			}
			backreferenced.add(value);
			return backreferenced;
		});
	}

	public void staticRef(Class<?> clazz, SerializedField referencedValue) {
		backReferences.compute(referencedValue.getValue(), (referenced, backreferenced) -> {
			if (backreferenced == null) {
				backreferenced = new HashSet<>();
			}
			backreferenced.add(globalFor(clazz));
			return backreferenced;
		});
	}

	private SerializedReferenceType globalFor(Class<?> clazz) {
		return globals.computeIfAbsent(clazz, GlobalRoot::new);
	}

	public Set<SerializedValue> closureOf(SerializedValue value) {
		Set<SerializedValue> closure = closures.get(value);
		if (closure != null) {
			return closure;
		}
		if (value instanceof SerializedImmutableType) {
			return Collections.singleton(value);
		} else if (value instanceof SerializedReferenceType) {
			closures.put(value, Collections.singleton(value));

			closure = new HashSet<>();
			closure.add(value);
			value.referencedValues().stream()
				.flatMap(val -> closureOf(val).stream())
				.forEach(closure::add);

			closures.put(value, closure);
			return closure;
		} else {
			return Collections.singleton(value);
		}
	}

	private static class GlobalRoot implements SerializedReferenceType {
		private Class<?> clazz;

		public GlobalRoot(Class<?> clazz) {
			this.clazz = clazz;
		}
		
		@Override
		public <T> T accept(Deserializer<T> visitor, DeserializerContext context) {
			return null;
		}

		@Override
		public void setResultType(Type resultType) {
		}

		@Override
		public Type getResultType() {
			return Types.parameterized(Class.class, null, clazz);
		}

		@Override
		public void setType(Type type) {
		}

		@Override
		public Type getType() {
			return Types.parameterized(Class.class, null, clazz);
		}

		@Override
		public List<SerializedValue> referencedValues() {
			return emptyList();
		}

		@Override
		public Annotation[] getAnnotations() {
			return new Annotation[0];
		}

		@Override
		public <T extends Annotation> Optional<T> getAnnotation(Class<T> clazz) {
			return Optional.empty();
		}

		@Override
		public void setId(int id) {
		}

		@Override
		public int getId() {
			return 0;
		}

	}

}
