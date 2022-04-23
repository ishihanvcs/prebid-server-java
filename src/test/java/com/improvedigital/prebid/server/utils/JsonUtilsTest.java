package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.json.JacksonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonUtilsTest extends VertxTest {

    private ObjectMapper mapper = new ObjectMapper();
    private JsonUtils jsonUtils = new JsonUtils(new JacksonMapper(mapper));

    @Test
    public void testGetBigDecimalAt() throws JsonProcessingException {
        JsonNode node = mapper.readTree(getJsonInputExampleBidResponse());
        assertThat(jsonUtils.getBigDecimalAt(node, "/my_price"))
                .isEqualTo(new BigDecimal(45.67));
        assertThat(jsonUtils.getBigDecimalAt(node, "/seatbid/0/bid/0/ext/origbidcpm"))
                .isEqualTo(new BigDecimal(15));

        assertThat(jsonUtils.getBigDecimalAt(node, "/seatbid/0/bid/0"))
                .isNull();
        assertThat(jsonUtils.getBigDecimalAt(node, "/seatbid/0/bid/0/ext/origbidcur"))
                .isNull();
        assertThat(jsonUtils.getBigDecimalAt(node, "/whatever"))
                .isNull();

        assertThat(jsonUtils.getBigDecimalAt(node, "/seatbid/0/bid/0", new BigDecimal(10.0)))
                .isEqualTo(new BigDecimal(10));
        assertThat(jsonUtils.getBigDecimalAt(node, "/seatbid/0/bid/0/ext/origbidcur", new BigDecimal(15.0)))
                .isEqualTo(new BigDecimal(15));
        assertThat(jsonUtils.getBigDecimalAt(node, "/whatever", new BigDecimal(20.0)))
                .isEqualTo(new BigDecimal(20));
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
