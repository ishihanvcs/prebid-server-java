package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Video;
import org.junit.Test;
import org.prebid.server.VertxTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonUtilsTest extends VertxTest {

    protected static JsonUtils jsonUtils;

    static {
        jsonUtils = new JsonUtils(jacksonMapper);
    }

    @Test
    public void testNonDestructiveMerge() {
        ObjectNode nullResult = jsonUtils.nonDestructiveMerge(null, null);
        assertThat(nullResult).isNull();

        Imp imp1 = Imp.builder()
                .id("1")
                .video(Video.builder()
                        .w(240)
                        .protocols(List.of(1, 2))
                        .build())
                .build();

        Imp imp2 = Imp.builder()
                .id("2")
                .video(Video.builder()
                        .h(340)
                        .protocols(List.of(3, 4))
                        .build())
                .pmp(Pmp.builder()
                        .privateAuction(1)
                        .build())
                .build();

        Imp mergedImp = Imp.builder()
                .id("2")
                .video(Video.builder()
                        .w(240)
                        .h(340)
                        .protocols(List.of(3, 4))
                        .build())
                .pmp(Pmp.builder()
                        .privateAuction(1)
                        .build())
                .build();

        Imp resultImp = jsonUtils.nonDestructiveMerge(imp1, null, Imp.class);
        assertThat(resultImp).isNotNull();
        assertThat(resultImp).isEqualTo(imp1);

        resultImp = jsonUtils.nonDestructiveMerge(null, imp2, Imp.class);
        assertThat(resultImp).isNotNull();
        assertThat(resultImp).isEqualTo(imp2);

        resultImp = jsonUtils.nonDestructiveMerge(imp1, imp2, Imp.class);
        assertThat(resultImp).isNotNull();
        assertThat(resultImp).isEqualTo(mergedImp);

        ObjectNode imp1Node = mapper.valueToTree(imp1);
        ObjectNode imp2Node = mapper.valueToTree(imp2);
        ObjectNode mergedImpNode = mapper.valueToTree(mergedImp);

        ObjectNode resultNode = jsonUtils.nonDestructiveMerge(imp1Node, null);
        assertThat(resultNode).isNotNull();
        assertThat(resultNode).isEqualTo(imp1Node);

        resultNode = jsonUtils.nonDestructiveMerge(null, imp2Node);
        assertThat(resultNode).isNotNull();
        assertThat(resultNode).isEqualTo(imp2Node);

        resultNode = jsonUtils.nonDestructiveMerge(imp1Node, imp2Node);
        assertThat(resultNode).isNotNull();
        assertThat(resultNode).isEqualTo(mergedImpNode);

        int int1 = 1;
        int int2 = 2;

        Integer intResult = jsonUtils.nonDestructiveMerge(int1, null, Integer.class);
        assertThat(intResult).isNotNull();
        assertThat(intResult).isEqualTo(int1);

        intResult = jsonUtils.nonDestructiveMerge(null, int2, Integer.class);
        assertThat(intResult).isNotNull();
        assertThat(intResult).isEqualTo(int2);

        intResult = jsonUtils.nonDestructiveMerge(int1, int2, Integer.class);
        assertThat(intResult).isNotNull();
        assertThat(intResult).isEqualTo(int2);
    }

    @Test
    public void testGetBigDecimalAt() throws JsonProcessingException {
        JsonNode node = mapper.readTree(getJsonInputExampleBidResponse());
        assertThat(jsonUtils.getBigDecimalAt(node, "/my_price"))
                .isEqualTo(BigDecimal.valueOf(45.67));
        assertThat(jsonUtils.getBigDecimalAt(node, "/seatbid/0/bid/0/ext/origbidcpm"))
                .isEqualTo(BigDecimal.valueOf(15));

        assertThat(jsonUtils.getBigDecimalAt(node, "/seatbid/0/bid/0"))
                .isNull();
        assertThat(jsonUtils.getBigDecimalAt(node, "/seatbid/0/bid/0/ext/origbidcur"))
                .isNull();
        assertThat(jsonUtils.getBigDecimalAt(node, "/whatever"))
                .isNull();

        assertThat(jsonUtils.getBigDecimalAt(node, "/seatbid/0/bid/0", BigDecimal.valueOf(10.0)))
                .isEqualTo(BigDecimal.valueOf(10.0));
        assertThat(jsonUtils.getBigDecimalAt(node, "/seatbid/0/bid/0/ext/origbidcur", BigDecimal.valueOf(15.0)))
                .isEqualTo(BigDecimal.valueOf(15.0));
        assertThat(jsonUtils.getBigDecimalAt(node, "/whatever", BigDecimal.valueOf(20.0)))
                .isEqualTo(BigDecimal.valueOf(20.0));
    }

    @Test
    public void testGetStringAt() throws JsonProcessingException {
        JsonNode node = mapper.readTree(getJsonInputExampleBidResponse());
        assertThat(jsonUtils.getStringAt(node, "/cur"))
                .isEqualTo("EUR");
        assertThat(jsonUtils.getStringAt(node, "/seatbid/0/bid/0/ext/origbidcur"))
                .isEqualTo("USD");

        assertThat(jsonUtils.getStringAt(node, "/seatbid/0/bid/0"))
                .isNull();
        assertThat(jsonUtils.getStringAt(node, "/seatbid/0/bid/0/ext/origbidcpm"))
                .isNull();
        assertThat(jsonUtils.getStringAt(node, "/whatever"))
                .isNull();

        assertThat(jsonUtils.getStringAt(node, "/seatbid/0/bid/0", "USD"))
                .isEqualTo("USD");
        assertThat(jsonUtils.getStringAt(node, "/seatbid/0/bid/0/ext/origbidcpm", "EUR"))
                .isEqualTo("EUR");
        assertThat(jsonUtils.getStringAt(node, "/whatever", "BDT"))
                .isEqualTo("BDT");
    }

    private String getJsonInputExampleBidResponse() {
        return "{"
                + "    \"id\": \"f2140dc6-67c2-4b05-b2b2-7430a78d796e\","
                + "    \"seatbid\": ["
                + "        {"
                + "            \"bid\": ["
                + "                {"
                + "                    \"id\": \"C4722BF1-599B-4CFC-A018-6CF7814D1865\","
                + "                    \"price\": 13.645,"
                + "                    \"cid\": \"16939\","
                + "                    \"crid\": \"12345\","
                + "                    \"ext\": {"
                + "                        \"dspid\": 6,"
                + "                        \"advid\": 5863,"
                + "                        \"bidtype\": 1,"
                + "                        \"origbidcpm\": 15,"
                + "                        \"origbidcur\": \"USD\""
                + "                    }"
                + "                }"
                + "            ],"
                + "            \"seat\": \"improvedigital\","
                + "            \"group\": 0"
                + "        }"
                + "    ],"
                + "    \"cur\": \"EUR\","
                + "    \"my_price\": 45.67"
                + "}";
    }
}
