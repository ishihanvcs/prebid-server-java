package com.azerion.prebid.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.json.JacksonMapper;

import java.util.List;

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
}
