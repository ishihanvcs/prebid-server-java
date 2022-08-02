package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ClassUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public abstract class XmlDeserializerBase<T> extends StdDeserializer<T> {

    protected Class<? extends PropertySetter> propertySetterClass;

    public XmlDeserializerBase(Class<T> vc) {
        this(vc, null);
    }

    public XmlDeserializerBase(Class<T> vc, Class<? extends PropertySetter> propertySetterClass) {
        super(vc);
        this.propertySetterClass = Objects.requireNonNullElse(propertySetterClass, PropertySetter.class);
    }

    public static class PropertySetter {
        private final ObjectNode sourceNode;
        private final ObjectCodec objectCodec;

        public PropertySetter(ObjectNode sourceNode, ObjectCodec objectCodec) {
            this.sourceNode = Objects.requireNonNull(sourceNode);
            this.objectCodec = Objects.requireNonNull(objectCodec);
        }

        public <T> PropertySetter setProperty(String property, Class<T> valueClass, Consumer<T> setter) {
            return setProperty(property, valueClass, setter, true);
        }

        public <T> PropertySetter setProperty(
                String property, Class<T> valueClass, Consumer<T> setter,
                boolean emptyStringAsNull
        ) {
            if (sourceNode.has(property)) {
                JsonNode jsonNode = sourceNode.get(property);
                T value = null;
                if (String.class.equals(valueClass)) {
                    value = valueClass.cast(JsonUtils.nodeToValue(objectCodec, jsonNode, emptyStringAsNull));
                } else if (ClassUtils.isPrimitiveOrWrapper(valueClass)
                        || jsonNode.isObject()) {
                    value = JsonUtils.nodeToValue(objectCodec, jsonNode, valueClass);
                }
                setter.accept(value);
            }
            return this;
        }

        public <T> PropertySetter setListProperty(
                String property,
                Class<T> itemClass, Consumer<List<T>> setter
        ) {
            return setListProperty(property, itemClass, setter, null);
        }

        public <T> PropertySetter setListProperty(
                String property,
                Class<T> itemClass, Consumer<List<T>> setter,
                List<T> defaultValue
        ) {
            String jsonPtr = listPropertyNameToJsonPtr(property);
            setter.accept(
                    JsonUtils.findArrayNodeAndConvertToList(
                        sourceNode, jsonPtr, itemClass, objectCodec, defaultValue
                    )
            );
            return this;
        }

        protected String listPropertyNameToJsonPtr(String property) {
            return "/" + property + "/" + property;
        }
    }

    private PropertySetter createPropertySetter(ObjectNode sourceNode, ObjectCodec objectCodec)
            throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        return propertySetterClass.getDeclaredConstructor(
                ObjectNode.class, ObjectCodec.class
        ).newInstance(sourceNode, objectCodec);
    }

    @SneakyThrows
    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        TreeNode treeNode = p.readValueAsTree();
        if (treeNode == null || !treeNode.isObject()) {
            return null;
        }
        ObjectCodec objectCodec = p.getCodec();
        ObjectNode node = (ObjectNode) treeNode;

        return deserialize(createPropertySetter(node, objectCodec));
    }

    protected abstract T deserialize(PropertySetter propertySetter);
}
