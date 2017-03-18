package net.amygdalum.testrecorder.deserializers;

import static net.amygdalum.testrecorder.util.GenericObject.getDefaultValue;
import static net.amygdalum.testrecorder.values.SerializedLiteral.isLiteral;
import static net.amygdalum.testrecorder.values.SerializedLiteral.literal;
import static net.amygdalum.testrecorder.values.SerializedNull.nullInstance;

import java.lang.reflect.Constructor;

import net.amygdalum.testrecorder.Deserializer;
import net.amygdalum.testrecorder.SerializedValue;
import net.amygdalum.testrecorder.values.SerializedField;

public class ConstructorParam {

    private Constructor<?> constructor;
    private int paramNumber;
    private SerializedField field;
    private Object value;
    private Class<?> type;
    private boolean needsCast;

    public ConstructorParam(Constructor<?> constructor, int paramNumber, SerializedField field, Object value) {
        this.constructor = constructor;
        this.paramNumber = paramNumber;
        this.field = field;
        this.value = value;
    }

    public ConstructorParam(Constructor<?> constructor, int paramNumber) {
        this.constructor = constructor;
        this.paramNumber = paramNumber;
    }

    public int getParamNumber() {
        return paramNumber;
    }

    public SerializedField getField() {
        return field;
    }

    public Object getValue() {
        return value;
    }

    public ConstructorParam insertTypeCasts() {
        this.needsCast = true;
        return this;
    }

    @Override
    public String toString() {
        return constructor.toString() + ":" + paramNumber + "=" + field.getValue() + "=> " + field.getName();
    }

    public SerializedValue computeSerializedValue() {
        Object value = this.value != null ? this.value : getDefaultValue(type);
        if (field == null) {
            if (type == String.class) {
                return nullInstance(String.class);
            } else if (isLiteral(type)) {
                return literal(type, value);
            } else {
                return nullInstance(type);
            }
        } else {
            return field.getValue();
        }
    }

    public ConstructorParam assertType(Class<?> type) {
        this.type = type;
        return this;
    }

    private boolean castNeeded() {
        if (needsCast) {
            return true;
        }
        if (field == null
            || value == null
            || type == null) {
            return false;
        }
        return field.getType() != type;
    }

    public Computation compile(TypeManager types, Deserializer<Computation> compiler) {
        SerializedValue serializedValue = computeSerializedValue();
        Computation computation = serializedValue.accept(compiler);
        if (castNeeded()) {
            String value = Templates.cast(types.getBestName(type), computation.getValue());
            computation = new Computation(value, type);
        }

        return computation;
    }

}