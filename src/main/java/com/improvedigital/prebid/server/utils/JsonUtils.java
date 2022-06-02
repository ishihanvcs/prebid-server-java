package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.json.JacksonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class JsonUtils {

    private final JacksonMapper mapper;

    public JsonUtils(JacksonMapper mapper) {
        this.mapper = mapper;
    }

    public Tuple2<ObjectNode, ObjectNode> createObjectNodes(String objectPath) {
        ObjectNode root = mapper.mapper().createObjectNode();
        ObjectNode leaf = root;
        if (!StringUtils.isBlank(objectPath)) {
            final String[] pathItems = objectPath.split("/");
            for (String pathItem : pathItems) {
                leaf = leaf.putObject(pathItem);
            }
        }
        return Tuple2.of(root, leaf);
    }

    public JsonNode findFirstNode(List<? extends JsonNode> nodeList, String path) {
        return nodeList.stream().filter(node -> !node.at(path).isMissingNode())
                .map(node -> node.at(path))
                .findFirst()
                .orElse(null);
    }

    public List<String> mapToStringList(JsonNode node, String... defaultValues) {
        if (node.isMissingNode()) {
            return new ArrayList<>(Arrays.asList(defaultValues));
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("node is not an array");
        }
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
    }

    /**
     * Find the decimal value at jsonPointerExpr and expects it to be {@link BigDecimal} and return it.
     *
     * @param node            the node to traverse.
     * @param jsonPointerExpr jackson pointer expression (e.g., "/path/to/a/node")
     * @return value at the jsonPointerExpr
     */
    public BigDecimal getBigDecimalAt(JsonNode node, String jsonPointerExpr) {
        return getBigDecimalAt(node, jsonPointerExpr, null);
    }

    /**
     * Find the decimal value at jsonPointerExpr and expects it to be {@link BigDecimal} and return it.
     *
     * @param node            the node to traverse.
     * @param jsonPointerExpr jackson pointer expression (e.g., "/path/to/a/node")
     * @param defaultValue    default value to return on anything missing.
     * @return value at the jsonPointerExpr
     */
    public BigDecimal getBigDecimalAt(JsonNode node, String jsonPointerExpr, BigDecimal defaultValue) {
        if (node == null || node.isMissingNode()) {
            return defaultValue;
        }

        node = node.at(jsonPointerExpr);
        return node.isMissingNode() || !node.isNumber()
                ? defaultValue
                : node.isBigDecimal()
                    ? node.decimalValue()
                    : node.isDouble()
                        ? BigDecimal.valueOf(node.doubleValue())
                        : new BigDecimal(node.asText());
    }

    /**
     * Find the String value at jsonPointerExpr and expects it to be {@link String} and return it.
     *
     * @param node            the node to traverse.
     * @param jsonPointerExpr jackson pointer expression (e.g., "/path/to/a/node")
     * @return value at the jsonPointerExpr
     */
    public String getStringAt(JsonNode node, String jsonPointerExpr) {
        return getStringAt(node, jsonPointerExpr, null);
    }

    /**
     * Find the String value at jsonPointerExpr and expects it to be {@link String} and return it.
     *
     * @param node            the node to traverse.
     * @param jsonPointerExpr jackson pointer expression (e.g., "/path/to/a/node")
     * @param defaultValue    default value to return on anything missing.
     * @return value at the jsonPointerExpr
     */
    public String getStringAt(JsonNode node, String jsonPointerExpr, String defaultValue) {
        if (node == null || node.isMissingNode()) {
            return defaultValue;
        }

        node = node.at(jsonPointerExpr);
        return (node.isMissingNode() || !node.isTextual()) ? defaultValue : node.textValue();
    }
}