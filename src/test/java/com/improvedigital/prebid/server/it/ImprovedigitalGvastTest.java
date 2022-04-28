package com.improvedigital.prebid.server.it;

import com.improvedigital.prebid.server.handler.GVastHandler;
import io.restassured.response.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "auction.generate-source-tid=true"
})
@RunWith(SpringRunner.class)
public class ImprovedigitalGvastTest extends ImprovedigitalIntegrationTest {

    @Test
    public void gvastHasProperQueryParamsInVastTagUri() throws XPathExpressionException, MalformedURLException {
        Response response = specWithPBSHeader()
                /* This placement's stored imp contains ext.prebid.improvedigitalpbs.waterfall.default=gam */
                .queryParam("p", "20220325")
                .get(GVastHandler.END_POINT);

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());

        assertThat(vastQueryParams.get("gdfp_req").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("gdfp_req").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("env").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("env").get(0)).isEqualTo("vp");

        assertThat(vastQueryParams.get("unviewed_position_start").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("unviewed_position_start").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("correlator").size()).isEqualTo(1);
        assertThat(Long.parseLong(vastQueryParams.get("correlator").get(0)))
                .isGreaterThan(System.currentTimeMillis() - 2000);

        assertThat(vastQueryParams.get("iu").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("iu").get(0)).isEqualTo("/1015413/pbs/20220325");

        assertThat(vastQueryParams.get("output").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("output").get(0)).isEqualTo("vast");

        assertThat(vastQueryParams.get("sz").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("sz").get(0)).isEqualTo("640x480|640x360");

        assertThat(vastQueryParams.get("description_url").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("description_url").get(0)).isEqualTo("http://pbs.improvedigital.com");

        assertThat(vastQueryParams.get("url").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("url").get(0)).isEqualTo("http://pbs.improvedigital.com");

        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("cust_params").get(0)).isEqualTo("tnl_asset_id=prebidserver");
    }

    @Test
    public void gvastHasProperQueryParamsInVastTagUriForAppRequest()
            throws XPathExpressionException, MalformedURLException {
        Response response = specWithPBSHeader()
                /* This placement's stored imp contains ext.prebid.improvedigitalpbs.waterfall.default=gam */
                .queryParam("p", "20220325")
                .queryParam("bundle", "com.improvedigital.ittests")
                .queryParam("gdpr", "1")
                .queryParam("gdpr_consent", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA")
                .queryParam("ifa", "V0Z851OEHUF5MYVVFQVG8FLSASLFIODF")
                .queryParam("lmt", "0")
                .queryParam("os", "Android")
                .get(GVastHandler.END_POINT);

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());

        assertThat(vastQueryParams.get("gdfp_req").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("gdfp_req").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("env").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("env").get(0)).isEqualTo("vp");

        assertThat(vastQueryParams.get("unviewed_position_start").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("unviewed_position_start").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("correlator").size()).isEqualTo(1);
        assertThat(Long.parseLong(vastQueryParams.get("correlator").get(0)))
                .isGreaterThan(System.currentTimeMillis() - 2000);

        assertThat(vastQueryParams.get("iu").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("iu").get(0)).isEqualTo("/1015413/pbs/20220325");

        assertThat(vastQueryParams.get("output").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("output").get(0)).isEqualTo("vast");

        assertThat(vastQueryParams.get("sz").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("sz").get(0)).isEqualTo("640x480|640x360");

        assertThat(vastQueryParams.get("description_url").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("description_url").get(0)).isEqualTo("http://pbs.improvedigital.com");

        assertThat(vastQueryParams.get("url").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("url").get(0)).isEqualTo("com.improvedigital.ittests.adsenseformobileapps.com/");

        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("cust_params").get(0)).isEqualTo("tnl_asset_id=prebidserver");

        assertThat(vastQueryParams.get("gdpr").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("gdpr").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("gdpr_consent").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("gdpr_consent").get(0)).isEqualTo("BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA");

        assertThat(vastQueryParams.get("msid").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("msid").get(0)).isEqualTo("com.improvedigital.ittests");

        assertThat(vastQueryParams.get("an").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("an").get(0)).isEqualTo("com.improvedigital.ittests");

        assertThat(vastQueryParams.get("rdid").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("rdid").get(0)).isEqualTo("V0Z851OEHUF5MYVVFQVG8FLSASLFIODF");

        assertThat(vastQueryParams.get("is_lat").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("is_lat").get(0)).isEqualTo("0");

        assertThat(vastQueryParams.get("idtype").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("idtype").get(0)).isEqualTo("adid");
    }

    @Test
    public void gvastReturnsDefaultTnlAssetIdInVastTagUri() throws XPathExpressionException, MalformedURLException {
        Response response = specWithPBSHeader()
                /* This placement's stored imp contains ext.prebid.improvedigitalpbs.waterfall.default=gam */
                .queryParam("p", "20220325")
                .queryParam("cust_params", "abc=def")
                .get(GVastHandler.END_POINT);

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.get("tnl_asset_id")).isNotNull();
        assertThat(custParams.get("tnl_asset_id").size()).isEqualTo(1);
        assertThat(custParams.get("tnl_asset_id").get(0)).isEqualTo("prebidserver");
    }

    @Test
    public void gvastReturnsRequestProvidedTnlAssetIdInVastTagUri()
            throws XPathExpressionException, MalformedURLException {
        Response response = specWithPBSHeader()
                /* This placement's stored imp contains ext.prebid.improvedigitalpbs.waterfall.default=gam */
                .queryParam("p", "20220325")
                .queryParam("cust_params", "abc=def&tnl_asset_id=custom_tnl_123")
                .get(GVastHandler.END_POINT);

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.get("tnl_asset_id")).isNotNull();
        assertThat(custParams.get("tnl_asset_id").size()).isEqualTo(1);
        assertThat(custParams.get("tnl_asset_id").get(0)).isEqualTo("custom_tnl_123");
    }

    @Test
    public void gvastReturnsRequestProvidedMultipleTnlAssetIdsInVastTagUri()
            throws XPathExpressionException, MalformedURLException {
        Response response = specWithPBSHeader()
                /* This placement's stored imp contains ext.prebid.improvedigitalpbs.waterfall.default=gam */
                .queryParam("p", "20220325")
                .queryParam("cust_params", "abc=def&tnl_asset_id=custom_2_tnl,custom_1_tnl")
                .get(GVastHandler.END_POINT);

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.get("tnl_asset_id")).isNotNull();
        assertThat(custParams.get("tnl_asset_id").size()).isEqualTo(1);
        assertThat(
                custParams.get("tnl_asset_id").get(0).contains("custom_1_tnl,custom_2_tnl")
                        || custParams.get("tnl_asset_id").get(0).contains("custom_2_tnl,custom_1_tnl")
        ).isTrue();
    }

    private Map<String, List<String>> splitQuery(String queryParam) {
        return Arrays.stream(queryParam.split("&"))
                .map(this::splitQueryParameter)
                .collect(Collectors.groupingBy(
                        AbstractMap.SimpleImmutableEntry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList()))
                );
    }

    private AbstractMap.SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
        try {
            final String[] idx = it.split("=");
            return new AbstractMap.SimpleImmutableEntry<>(
                    URLDecoder.decode(idx[0], "UTF-8"),
                    URLDecoder.decode(idx[1], "UTF-8")
            );
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
