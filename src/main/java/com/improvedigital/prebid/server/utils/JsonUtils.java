package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.json.JacksonMapper;

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
}
