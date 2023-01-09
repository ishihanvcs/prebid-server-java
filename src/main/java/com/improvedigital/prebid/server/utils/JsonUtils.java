package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.settings.model.ImprovedigitalPbsAccountExt;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class JsonUtils {

    private static final String EXT_CUSTOM_CONFIG_KEY = "improvedigitalpbs";
    private static final JsonPointer JSON_PTR_CUSTOM_CONFIG
            = JsonPointer.compile("/" + EXT_CUSTOM_CONFIG_KEY);
    private static final JsonPointer JSON_PTR_CUSTOM_CONFIG_LEGACY
            = JsonPointer.compile("/prebid/" + EXT_CUSTOM_CONFIG_KEY);

    private final JacksonMapper mapper;
    private final ObjectMapper objectMapper;

    public JsonUtils(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.objectMapper = mapper.mapper();
    }

    public JacksonMapper getMapper() {
        return mapper;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public Tuple2<ObjectNode, ObjectNode> createObjectNodes(String objectPath) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode leaf = root;
        if (!StringUtils.isBlank(objectPath)) {
            final String[] pathItems = objectPath.split("/");
            for (String pathItem : pathItems) {
                leaf = leaf.putObject(pathItem);
            }
        }
        return Tuple2.of(root, leaf);
    }

    public <T extends JsonNode> T valueToTree(Object fromValue) {
        return objectMapper.valueToTree(fromValue);
    }

    public JsonNode readTree(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public <T> T treeToValue(TreeNode node, Class<T> clazz) {
        try {
            return objectMapper.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public <T> T objectPathToValue(ObjectNode objNode, String path, Class<T> clazz) {
        return objectPathToValue(objNode, path, clazz, null);
    }

    public <T> T objectPathToValue(ObjectNode objNode, String path, Class<T> clazz, T defaultValue) {
        try {
            if (objNode == null || objNode.isMissingNode()) {
                return defaultValue;
            }
            JsonNode node = objNode.at(path);
            if (node.isMissingNode()) {
                return defaultValue;
            }
            return objectMapper.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return defaultValue;
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

    public static <T> List<T> findArrayNodeAndConvertToList(
            ObjectNode containerNode, String arrayNodePath,
            Class<T> itemClass, ObjectCodec objectCodec,
            List<T> defaultValue) {
        if (containerNode.at(arrayNodePath).isMissingNode()) {
            return defaultValue;
        }

        ArrayNode arrayNode;
        JsonNode mayBeArrayNode = containerNode.at(arrayNodePath);
        if (mayBeArrayNode.isArray()) {
            arrayNode = (ArrayNode) mayBeArrayNode;
        } else {
            arrayNode = (ArrayNode) objectCodec.createArrayNode();
            arrayNode.add(mayBeArrayNode);
        }
        return arrayNodeToValueList(
                arrayNode, itemClass,
                (itemNode, valueClass) ->
                        nodeToValue(objectCodec, itemNode, valueClass)
        );
    }

    public static <T> List<T> arrayNodeToValueList(
            ArrayNode arrayNode,
            Class<T> valueClass,
            BiFunction<JsonNode, Class<T>, T> valueConverter
    ) {
        List<T> list = new ArrayList<>();
        arrayNode.elements().forEachRemaining(itemNode -> {
            if (itemNode == null || itemNode.isNull()) {
                list.add(null);
                return;
            }
            T value = valueConverter.apply(itemNode, valueClass);
            if (value != null) { // null means value conversion error. So, skip it
                list.add(value);
            }
        });
        return list;
    }

    public static <T> T nodeToValue(ObjectCodec objectCodec, JsonNode node, Class<T> valueClass) {
        try {
            return objectCodec.treeToValue(node, valueClass);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String nodeToValue(ObjectCodec objectCodec, JsonNode node, boolean emptyToNull) {
        try {
            String value = objectCodec.treeToValue(node, String.class);
            return emptyToNull && StringUtils.EMPTY.equals(value)
                    ? null : value;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public <T> T nonDestructiveMerge(T originalObject, T mergingObject, Class<T> clazz) {
        if (!ObjectUtils.anyNotNull(originalObject, mergingObject)) {
            return null;
        }

        if (!ObjectUtils.allNotNull(originalObject, mergingObject)) {
            return ObjectUtils.defaultIfNull(originalObject, mergingObject);
        }

        JsonNode originalNode = objectMapper.valueToTree(originalObject);
        JsonNode mergingNode = objectMapper.valueToTree(mergingObject);

        if (!originalNode.isObject() || !mergingNode.isObject()) {
            return mergingObject;
        }

        return nonDestructiveMerge(
                (ObjectNode) originalNode,
                (ObjectNode) mergingNode,
                clazz
        );
    }

    public <T> T nonDestructiveMerge(ObjectNode originalObject, ObjectNode mergingObject, Class<T> clazz) {
        ObjectNode mergedNode = nonDestructiveMerge(originalObject, mergingObject);
        return ObjectUtil.getIfNotNull(mergedNode, node -> objectMapper.convertValue(node, clazz));
    }

    /**
     * A simple non-destructive merge algorithm that recursively traverse properties in
     * mergingObject and sets those properties in originalObject, if and only if the value
     * is not null (which differs from JsonMerge object found in PBS core). This allows us
     * to superimpose mergingObject into originalObject, that results all non-null properties
     * in mergingObject to be available in a copy of originalObject, that will be returned by
     * this method. To understand detail behaviour of this method, please refer to respective
     * test case in JsonUtilsTest class.
     * <p>
     * Please note, this method does not mutate any of the originalObject or mergingObject
     * and always returns a new object with the merging result.
     *
     * @param originalObject {@link ObjectNode} the object where the merge will occur upon
     * @param mergingObject  {@link ObjectNode} the object that will be merged
     * @return ObjectNode
     */
    public ObjectNode nonDestructiveMerge(ObjectNode originalObject, ObjectNode mergingObject) {
        if (!ObjectUtils.anyNotNull(originalObject, mergingObject)) {
            return null;
        }

        if (!ObjectUtils.allNotNull(originalObject, mergingObject)) {
            return ObjectUtils.defaultIfNull(originalObject, mergingObject).deepCopy();
        }

        ObjectNode ret = originalObject.isObject() ? originalObject.deepCopy()
                : objectMapper.createObjectNode();

        for (Iterator<Map.Entry<String, JsonNode>> it = mergingObject.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value != null && !(value instanceof NullNode)) {
                if (value.isObject()) {
                    ObjectNode mergingValue = (ObjectNode) value;
                    ObjectNode originalValue = ret.get(key) != null && ret.get(key).isObject()
                            ? (ObjectNode) ret.get(key)
                            : objectMapper.createObjectNode();
                    ObjectNode mergedValue = nonDestructiveMerge(originalValue, mergingValue);
                    ret.set(key, mergedValue);
                } else {
                    ret.set(key, value);
                }
            }
        }
        return ret;
    }

    /**
     * Find the decimal value at jsonPointerExpr and expects it to be {@link BigDecimal} and return it.
     *
     * @param node            the node to traverse.
     * @param jsonPointerExpr jackson pointer expression (e.g., "/path/to/a/node")
     * @return value at the jsonPointerExpr
     */
    public static BigDecimal getBigDecimalAt(JsonNode node, String jsonPointerExpr) {
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
    public static BigDecimal getBigDecimalAt(JsonNode node, String jsonPointerExpr, BigDecimal defaultValue) {
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
    public static String getStringAt(JsonNode node, String jsonPointerExpr) {
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
    public static String getStringAt(JsonNode node, String jsonPointerExpr, String defaultValue) {
        if (node == null || node.isMissingNode()) {
            return defaultValue;
        }

        node = node.at(jsonPointerExpr);
        return (node.isMissingNode() || !node.isTextual()) ? defaultValue : node.textValue();
    }

    public ImprovedigitalPbsImpExt getImprovedigitalPbsImpExt(Imp imp) {
        if (imp == null || imp.getExt() == null) {
            return null;
        }
        try {
            JsonNode configNode = imp.getExt().at(JSON_PTR_CUSTOM_CONFIG);
            if (configNode.isMissingNode()) {
                configNode = imp.getExt().at(JSON_PTR_CUSTOM_CONFIG_LEGACY);
            }
            if (configNode.isMissingNode()) {
                return null;
            }
            return objectMapper.treeToValue(
                    configNode, ImprovedigitalPbsImpExt.class
            );
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public ImprovedigitalPbsAccountExt getAccountExt(Account account) {
        if (account == null || account.getExt() == null) {
            return null;
        }
        try {
            JsonNode configNode = account.getExt().at(JSON_PTR_CUSTOM_CONFIG);
            if (configNode.isMissingNode()) {
                return null;
            }
            return objectMapper.treeToValue(
                    configNode, ImprovedigitalPbsAccountExt.class
            );
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public BidType getBidType(Bid bid) {
        if (bid == null || bid.getExt() == null) {
            return null;
        }
        String bidType = getStringAt(bid.getExt(), "/prebid/type");
        return ObjectUtil.getIfNotNull(bidType, BidType::fromString);
    }

    public boolean isBidWithVideoType(Bid bid) {
        return getBidType(bid) == BidType.video;
    }

    public boolean isBidWithNonVideoType(Bid bid) {
        return !isBidWithVideoType(bid);
    }

    public BidRequest parseBidRequest(String body) {
        return mapper.decodeValue(body, BidRequest.class);
    }
}
