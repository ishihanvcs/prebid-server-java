package com.improvedigital.prebid.server.utils;

import com.ctc.wstx.api.WstxOutputProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;

import java.util.Arrays;
import java.util.function.Consumer;

public class XmlUtils {

    private static final XmlMapper DEFAULT_SERIALIZER;
    private static final XmlMapper DEFAULT_DESERIALIZER;

    static {
        DEFAULT_SERIALIZER = XmlMapper.builder()
                .enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
                .disable(SerializationFeature.INDENT_OUTPUT)
                .build();
        DEFAULT_SERIALIZER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        DEFAULT_SERIALIZER.getFactory().getXMLOutputFactory()
                .setProperty(WstxOutputProperties.P_USE_DOUBLE_QUOTES_IN_XML_DECL, true);

        DEFAULT_DESERIALIZER = XmlMapper.builder()
                .enable(MapperFeature.ALLOW_EXPLICIT_PROPERTY_RENAMING)
                .enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME)
                .build();
    }

    private XmlUtils() {
    }

    public static String serialize(Object object)
            throws JsonProcessingException {
        return serialize(object, false);
    }

    public static String serialize(Object object, boolean prettyPrint)
            throws JsonProcessingException {
        return serialize(object, prettyPrint, new Module[]{});
    }

    public static String serialize(Object object, boolean prettyPrint, Module... modules)
            throws JsonProcessingException {
        return serialize(object, prettyPrint, null, modules);
    }

    public static String serialize(
            Object object, boolean prettyPrint,
            Consumer<XmlMapper> mapperModifier, Module... modules
    ) throws JsonProcessingException {
        XmlMapper mapper = copyIf(DEFAULT_SERIALIZER, prettyPrint || mapperModifier != null || modules.length > 0);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, prettyPrint);
        Arrays.stream(modules).forEach(mapper::registerModule);
        if (mapperModifier != null) {
            mapperModifier.accept(mapper);
        }
        return mapper.writeValueAsString(object);
    }

    public static <T> T deserialize(String xml, Class<T> clazz)
            throws JsonProcessingException {
        return deserialize(xml, clazz, null, new Module[]{});
    }

    public static <T> T deserialize(String xml, Class<T> clazz, Module... modules)
            throws JsonProcessingException {
        return deserialize(xml, clazz, null, modules);
    }

    public static <T> T deserialize(
            String xml, Class<T> clazz,
            Consumer<XmlMapper> mapperModifier, Module... modules
    ) throws JsonProcessingException {
        XmlMapper mapper = copyIf(DEFAULT_DESERIALIZER, mapperModifier != null || modules.length > 0);
        Arrays.stream(modules).forEach(mapper::registerModule);
        if (mapperModifier != null) {
            mapperModifier.accept(mapper);
        }
        return mapper.readValue(xml, clazz);
    }

    private static XmlMapper copyIf(XmlMapper source, boolean shouldCopy) {
        return shouldCopy ? source.copy() : source;
    }
}
