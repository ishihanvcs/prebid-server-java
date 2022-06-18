package com.improvedigital.prebid.server.it;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.prebid.server.it.IntegrationTest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("checkstyle:hideutilityclassconstructor")
@SpringBootApplication(
        scanBasePackages = {
                "org.prebid.server",
                "com.improvedigital.prebid.server"
        }
)
@TestPropertySource(properties = {
        "settings.filesystem.stored-imps-dir=src/test/resources/com/improvedigital/prebid/server/it/storedimps",
})
public class ImprovedigitalIntegrationTest extends IntegrationTest {

    protected String jsonFromFileWithMacro(String file, Map<String, String> macrosInFileContent)
            throws IOException {
        String fileContent = mapper.writeValueAsString(
                mapper.readTree(this.getClass().getResourceAsStream(file))
        );

        // Replace all occurrences of <key>s by it's <value> of map.
        if (macrosInFileContent != null) {
            return macrosInFileContent.entrySet().stream()
                    .map(m -> (Function<String, String>) s -> s.replace(m.getKey(), m.getValue()))
                    .reduce(Function.identity(), Function::andThen)
                    .apply(fileContent);
        }

        return fileContent;
    }

    protected static RequestSpecification specWithPBSHeader(int port) {
        return given(spec(port))
                .header("Referer", "http://pbs.improvedigital.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/100.0.4896.60 Safari/537.36")
                .header("Origin", "http://pbs.improvedigital.com");
    }

    private static RequestSpecification spec(int port) {
        return new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(port)
                .setConfig(RestAssuredConfig.config()
                        .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
                .build();
    }

    protected String createCacheRequest(
            String requestId,
            String... cacheContents
    ) throws IOException {
        List<String> cachePutObjects = Arrays.stream(cacheContents).map(content -> "{"
                + "  \"aid\": \"" + requestId + "\","
                + "  \"type\": \"xml\","
                + "  \"value\": \"" + content + "\""
                + "}"
        ).collect(Collectors.toList());

        return "{"
                + "\"puts\": ["
                + String.join(",", cachePutObjects)
                + "]"
                + "}";
    }

    protected String createCacheResponse(String... cacheIds) {
        List<String> cachePutObjects = Arrays.stream(cacheIds).map(cacheId -> "{"
                + "  \"uuid\": \"" + cacheId + "\""
                + "}"
        ).collect(Collectors.toList());

        return "{"
                + "\"responses\": ["
                + String.join(",", cachePutObjects)
                + "]"
                + "}";
    }

    protected String createResourceFile(String resourceFilePathFromSlash, String fileContent) throws IOException {
        Path cacheResponseFile = Paths.get(this.getClass().getResource("/").getPath() + resourceFilePathFromSlash);
        if (!Files.exists(cacheResponseFile)) {
            Files.createFile(cacheResponseFile);
        }
        Files.writeString(cacheResponseFile, fileContent, StandardOpenOption.WRITE);

        return "/" + resourceFilePathFromSlash;
    }

    protected Map<String, List<String>> splitQuery(String queryParam) {
        return Arrays.stream(queryParam.split("&"))
                .map(this::splitQueryParameter)
                .collect(Collectors.groupingBy(
                        AbstractMap.SimpleImmutableEntry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList()))
                );
    }

    protected AbstractMap.SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
        try {
            final String[] idx = it.split("=");
            return new AbstractMap.SimpleImmutableEntry<>(
                    URLDecoder.decode(idx[0], "UTF-8"),
                    URLDecoder.decode(idx.length > 1 ? idx[1] : "", "UTF-8")
            );
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected void assertQuerySingleValue(List<String> paramValues, String expectedValue) {
        assertThat(paramValues.size()).isEqualTo(1);
        assertThat(paramValues.get(0)).isEqualTo(expectedValue);
    }

    protected static String getVastXmlInline(String adId, boolean hasImpPixel) {
        return ("<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "\">"
                + "    <InLine>"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "</Impression>" : "")
                + "      <Creatives>"
                + "        <Creative AdID=\"" + adId + "\">"
                + "          <Linear>"
                + "            <Duration>00:00:60</Duration>"
                + "            <VideoClicks>"
                + "              <ClickThrough>https://click.pbs.improvedigital.com/" + adId + "</ClickThrough>"
                + "            </VideoClicks>"
                + "            <MediaFiles>"
                + "              <MediaFile type=\"video/mp4\" width=\"640\" height=\"480\">"
                + "                https://media.pbs.improvedigital.com/" + adId + ".mp4"
                + "              </MediaFile>"
                + "            </MediaFiles>"
                + "          </Linear>"
                + "        </Creative>"
                + "      </Creatives>"
                + "    </InLine>"
                + "  </Ad>"
                + "</VAST>"
        ).replace("\"", "\\\"");
    }

    protected String getVastXmlInlineWithMultipleAds(String adId, boolean hasImpPixel) {
        return ("<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "-1" + "\">"
                + "    <InLine>"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 1</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-1" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-1" + "</Impression>" : "")
                + "      <Creatives>"
                + "        <Creative AdID=\"" + adId + "-1" + "\">"
                + "          <Linear>"
                + "            <Duration>00:00:60</Duration>"
                + "            <VideoClicks>"
                + "              <ClickThrough>https://click.pbs.improvedigital.com/" + adId + "-1" + "</ClickThrough>"
                + "            </VideoClicks>"
                + "            <MediaFiles>"
                + "              <MediaFile type=\"video/mp4\" width=\"640\" height=\"480\">"
                + "                https://media.pbs.improvedigital.com/" + adId + "-1" + ".mp4"
                + "              </MediaFile>"
                + "            </MediaFiles>"
                + "          </Linear>"
                + "        </Creative>"
                + "      </Creatives>"
                + "    </InLine>"
                + "  </Ad>"
                + "  <Ad id=\"" + adId + "-2" + "\">"
                + "    <InLine>"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 2</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-2" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-2" + "</Impression>" : "")
                + "      <Creatives>"
                + "        <Creative AdID=\"" + adId + "-2" + "\">"
                + "          <Linear>"
                + "            <Duration>00:00:30</Duration>"
                + "            <VideoClicks>"
                + "              <ClickThrough>https://click.pbs.improvedigital.com/" + adId + "-2" + "</ClickThrough>"
                + "            </VideoClicks>"
                + "            <MediaFiles>"
                + "              <MediaFile type=\"video/mp4\" width=\"640\" height=\"480\">"
                + "                https://media.pbs.improvedigital.com/" + adId + "-2" + ".mp4"
                + "              </MediaFile>"
                + "            </MediaFiles>"
                + "          </Linear>"
                + "        </Creative>"
                + "      </Creatives>"
                + "    </InLine>"
                + "  </Ad>"
                + "</VAST>"
        ).replace("\"", "\\\"");
    }

    protected String getVastXmlWrapper(String adId, boolean hasImpPixel) {
        return ("<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "\">"
                + "    <Wrapper fallbackOnNoAd=\"true\">"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "</Impression>" : "")
                + "      <VASTAdTagURI>"
                + "        <![CDATA[https://vast.pbs.improvedigital.com/" + adId + "]]>"
                + "      </VASTAdTagURI>"
                + "    </Wrapper>"
                + "  </Ad>"
                + "</VAST>"
        ).replace("\"", "\\\"");
    }

    protected String getVastXmlWrapperWithMultipleAds(String adId, boolean hasImpPixel) {
        return ("<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "-1" + "\">"
                + "    <Wrapper fallbackOnNoAd=\"true\">"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 1</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-1" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-1" + "</Impression>" : "")
                + "      <VASTAdTagURI>"
                + "        <![CDATA[https://vast.pbs.improvedigital.com/" + adId + "-1" + "]]>"
                + "      </VASTAdTagURI>"
                + "    </Wrapper>"
                + "  </Ad>"
                + "  <Ad id=\"" + adId + "-2" + "\">"
                + "    <Wrapper fallbackOnNoAd=\"true\">"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 1</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-2" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-2" + "</Impression>" : "")
                + "      <VASTAdTagURI>"
                + "        <![CDATA[https://vast.pbs.improvedigital.com/" + adId + "-2" + "]]>"
                + "      </VASTAdTagURI>"
                + "    </Wrapper>"
                + "  </Ad>"
                + "</VAST>"
        ).replace("\"", "\\\"");
    }

    protected void assertBidCountSingle(JSONObject responseJson) throws JSONException {
        assertThat(responseJson.getJSONArray("seatbid").length())
                .isGreaterThanOrEqualTo(1);

        assertThat(responseJson.getJSONArray("seatbid").getJSONObject(0).getJSONArray("bid").length())
                .isGreaterThanOrEqualTo(1);
    }

    @NotNull
    protected String getAdm(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getString("adm");
    }

    protected void assertBidExtPrebidType(
            JSONObject responseJson, int seatBidIndex, int bidIndex, String expectedExtPrebidType
    ) throws JSONException {
        assertThat(getBidExtPrebidType(responseJson, seatBidIndex, bidIndex)).isEqualTo(expectedExtPrebidType);
    }

    @NotNull
    protected String getBidExtPrebidType(
            JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBidExtPrebid(responseJson, seatBidIndex, bidIndex)
                .getString("type");
    }

    @NotNull
    protected JSONObject getBidExtPrebid(
            JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getJSONObject("ext")
                .getJSONObject("prebid");
    }

    protected void assertBidIdExists(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        assertThat(getBidId(responseJson, seatBidIndex, bidIndex)).isNotEmpty();
    }

    @NotNull
    protected String getBidId(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getString("id");
    }

    protected void assertBidImpId(
            JSONObject responseJson, int seatBidIndex, int bidIndex, String expectedImpId) throws JSONException {
        assertThat(getBidImpId(responseJson, seatBidIndex, bidIndex)).isEqualTo(expectedImpId);
    }

    @NotNull
    protected String getBidImpId(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getString("impid");
    }

    protected void assertBidPrice(
            JSONObject responseJson, int seatBidIndex, int bidIndex, double expectedBidPrice) throws JSONException {
        assertThat(getBidPrice(responseJson, seatBidIndex, bidIndex)).isEqualTo(expectedBidPrice);
    }

    @NotNull
    protected Double getBidPrice(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getDouble("price");
    }

    @NotNull
    protected JSONObject getBid(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getSeatbid(responseJson, seatBidIndex)
                .getJSONArray("bid").getJSONObject(bidIndex);
    }

    protected void assertSeat(JSONObject responseJson, int seatBidIndex, String expectedSeat) throws JSONException {
        assertThat(getSeat(responseJson, seatBidIndex)).isEqualTo(expectedSeat);
    }

    @NotNull
    protected String getSeat(JSONObject responseJson, int seatBidIndex) throws JSONException {
        return getSeatbid(responseJson, seatBidIndex)
                .getString("seat");
    }

    @NotNull
    protected JSONObject getSeatbid(JSONObject responseJson, int seatBidIndex) throws JSONException {
        return responseJson
                .getJSONArray("seatbid").getJSONObject(seatBidIndex);
    }

    protected void assertCurrency(JSONObject responseJson, String expectedCurrency) throws JSONException {
        assertThat(responseJson.getString("cur")).isEqualTo(expectedCurrency);
    }
}
