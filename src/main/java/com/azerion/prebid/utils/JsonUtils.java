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

    public JsonNode findFirstNode(List<ObjectNode> nodeList, String path) {
        final String[] pathItems = path.split("/");
        return nodeList.stream().filter(node -> {
            JsonNode n = node;
            for (String pathItem : pathItems) {
                if (n == null) {
                    break;
                }
                n = n.get(pathItem);
            }
            return n != null && !n.isMissingNode();
        }).map(node -> {
            JsonNode n = node;
            for (String pathItem : pathItems) {
                n = n.get(pathItem);
            }
            return n;
        }).findFirst().orElse(null);
    }
}
