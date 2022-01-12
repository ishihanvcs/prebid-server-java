package com.improvedigital.prebid.server.utils;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.util.HttpUtil;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class FluentMapTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Test
    public void fromQueryStringShouldReturnEmptyMapForNull() {
        Map<String, Set<String>> map = FluentMap.fromQueryString(null).result();
        assertThat(map.size() == 0).isTrue();
    }

    @Test
    public void fromQueryStringShouldReturnEmptyMapForEmptyString() {
        Map<String, Set<String>> map = FluentMap.fromQueryString("").result();
        assertThat(map.size() == 0).isTrue();
    }

    @Test
    public void fromQueryStringShouldReturnEmptyMapForNonKeyValueString() {
        Map<String, Set<String>> map = FluentMap.fromQueryString("keyWithoutValue").result();
        assertThat(map.size() == 0).isTrue();
    }

    @Test
    public void fromQueryStringShouldNotHaveKeyWithEmptyValue() {
        Map<String, Set<String>> map = FluentMap.fromQueryString("key=").result();
        assertThat(map.containsKey("key")).isFalse();
        assertThat(map.size() == 0).isTrue();
    }

    @Test
    public void fromQueryStringShouldStripSpacesInKeyValue() {
        Map<String, Set<String>> map = FluentMap.fromQueryString("  key =  value ").result();
        assertThat(map.size() > 0).isTrue();
        assertThat(map.containsKey("key")).isTrue();
        assertThat(map.get("key").contains("value")).isTrue();
    }

    @Test
    public void fromQueryStringShouldNotContainNonExistingValue() {
        Map<String, Set<String>> map = FluentMap.fromQueryString("key=value").result();
        assertThat(map.size() > 0).isTrue();
        assertThat(map.containsKey("key")).isTrue();
        assertThat(map.get("key").contains("value")).isTrue();
        assertThat(map.get("key").contains("non_existing")).isFalse();
    }

    @Test
    public void fromQueryStringShouldReturnEmptyMapForSpacesValue() {
        Map<String, Set<String>> map = FluentMap.fromQueryString("key=  ").result();
        assertThat(map.size() == 0).isTrue();
    }

    @Test
    public void fromQueryStringShouldSplitValuesSeparatedByComma() {
        final String queryString = String.format(
                "key=%s",
                HttpUtil.encodeUrl("value1 , value2")
        );
        Map<String, Set<String>> map = FluentMap.fromQueryString(queryString).result();
        assertThat(map.size() > 0).isTrue();
        assertThat(map.containsKey("key")).isTrue();
        assertThat(map.get("key").contains("value1")).isTrue();
        assertThat(map.get("key").contains("value2")).isTrue();
    }

    @Test
    public void fromQueryStringShouldDecodeValuesAsUrlComponent() {
        Map<String, Set<String>> map = FluentMap.fromQueryString("key=this+is+value1,this+is+value2").result();
        assertThat(map.size() > 0).isTrue();
        assertThat(map.containsKey("key")).isTrue();
        assertThat(map.get("key").contains("this is value1")).isTrue();
        assertThat(map.get("key").contains("this is value2")).isTrue();
    }

    @Test
    public void fromQueryStringShouldTrimWhiteSpacesAfterDecodingValuesAsUrlComponent() {
        Map<String, Set<String>> map = FluentMap.fromQueryString("key=++this+is+value+").result();
        assertThat(map.size() > 0).isTrue();
        assertThat(map.containsKey("key")).isTrue();
        assertThat(map.get("key").contains("this is value")).isTrue();
    }

    @Test
    public void fromQueryStringAndQueryStringMethodsShouldWorkViceVersa1() {
        final String eq1 = "1+3=4";
        final String eq2 = "2 + 2 = 4";
        final String originalQueryString = "eq1=1%2B3%3D4&eq2=2+%2B+2+%3D+4";
        final String encodedQueryString = String.format(
                "eq1=%s&eq2=%s",
                HttpUtil.encodeUrl(eq1),
                HttpUtil.encodeUrl(eq2)
        );
        assertThat(originalQueryString.equals(encodedQueryString)).isTrue();

        Map<String, Set<String>> map = FluentMap.fromQueryString(originalQueryString).result();
        assertThat(map.size() == 2).isTrue();
        assertThat(map.get("eq1").contains(eq1)).isTrue();
        assertThat(map.get("eq2").contains(eq2)).isTrue();
        final String mapToQueryString = FluentMap.from(map).queryString();
        assertThat(originalQueryString.equals(mapToQueryString)).isTrue();
    }

    @Test
    public void fromQueryStringAndQueryStringMethodsShouldWorkViceVersa2() {
        final String key1 = "1,2,3";
        final String key2 = "4,5,6";
        final String queryStringWithSpaces = "key1=1%2C2%2C3&key2=4%20%2C%205%20%2C%206";
        final String encodedQueryString = String.format(
                "key1=%s&key2=%s",
                HttpUtil.encodeUrl(key1),
                HttpUtil.encodeUrl(key2)
        );
        // these two query strings should not be equal due to leading & trailing spaces in value of key2
        assertThat(queryStringWithSpaces.equals(encodedQueryString)).isFalse();

        Map<String, Set<String>> map = FluentMap.fromQueryString(queryStringWithSpaces).result();
        assertThat(map.size() == 2).isTrue();

        assertThat(map.get("key1").size() == 3).isTrue();
        assertThat(map.get("key1").contains("1")).isTrue();
        assertThat(map.get("key1").contains("2")).isTrue();
        assertThat(map.get("key1").contains("3")).isTrue();

        assertThat(map.get("key2").size() == 3).isTrue();
        assertThat(map.get("key2").contains("4")).isTrue();
        assertThat(map.get("key2").contains("5")).isTrue();
        assertThat(map.get("key2").contains("6")).isTrue();

        final String mapToQueryString = FluentMap.from(map).queryString();
        assertThat(encodedQueryString.equals(mapToQueryString)).isTrue();
    }

    @Test
    public void keysInQueryStringResultShouldBeSorted() {
        final String key1 = "1,2,3";
        final String key2 = "4,5,6";
        final String queryStringWithRandomKeyOrderAndSpaces = "key2=4%20%2C%205%20%2C%206&key1=1%2C2%2C3";
        final String queryStringSortedByKey = String.format(
                "key1=%s&key2=%s",
                HttpUtil.encodeUrl(key1),
                HttpUtil.encodeUrl(key2)
        );
        // these two query strings should not be equal due to leading & trailing spaces in value of key2
        assertThat(queryStringWithRandomKeyOrderAndSpaces.equals(queryStringSortedByKey)).isFalse();

        Map<String, Set<String>> map = FluentMap.fromQueryString(queryStringWithRandomKeyOrderAndSpaces).result();
        assertThat(map.size() == 2).isTrue();

        assertThat(map.get("key1").size() == 3).isTrue();
        assertThat(map.get("key1").contains("1")).isTrue();
        assertThat(map.get("key1").contains("2")).isTrue();
        assertThat(map.get("key1").contains("3")).isTrue();

        assertThat(map.get("key2").size() == 3).isTrue();
        assertThat(map.get("key2").contains("4")).isTrue();
        assertThat(map.get("key2").contains("5")).isTrue();
        assertThat(map.get("key2").contains("6")).isTrue();

        final String mapToQueryString = FluentMap.from(map).queryString();
        assertThat(queryStringSortedByKey.equals(mapToQueryString)).isTrue();
    }
}
