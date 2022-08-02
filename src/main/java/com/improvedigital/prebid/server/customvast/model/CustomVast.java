package com.improvedigital.prebid.server.customvast.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlCData;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;

import java.util.ArrayList;
import java.util.List;

@Builder(toBuilder = true)
@Value
@JacksonXmlRootElement(localName = "VAST")
public class CustomVast {

    @JacksonXmlProperty(isAttribute = true)
    @Builder.Default
    String version = "2.0";

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Ad")
    @Singular
    List<Ad> ads;

    @Value(staticConstructor = "of")
    public static class Ad {

        @JacksonXmlProperty(isAttribute = true)
        @NonNull
        Integer id;

        @JacksonXmlProperty(localName = "Wrapper")
        @NonNull
        Wrapper wrapper;
    }

    @Builder(toBuilder = true)
    @Value
    public static class Wrapper {

        @JacksonXmlProperty(isAttribute = true)
        Boolean fallbackOnNoAd;

        @JacksonXmlProperty(localName = "AdSystem")
        @Builder.Default
        @NonNull
        String adSystem = "ImproveDigital PBS";

        @JacksonXmlCData
        @JacksonXmlProperty(localName = "VASTAdTagURI")
        @NonNull
        String vastAdTagURI;

        @JacksonXmlElementWrapper(localName = "Creatives")
        @JacksonXmlProperty(localName = "Creative")
        @Singular
        List<String> creatives;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "Impression")
        @JacksonXmlCData
        @Singular
        List<String> impressions;

        @JacksonXmlElementWrapper(localName = "Extensions")
        @JacksonXmlProperty(localName = "Extension")
        @Singular
        List<Extension> extensions;

        public List<String> getImpressions() {
            return impressions == null ? new ArrayList<>() : impressions;
        }
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            property = "type"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = DebugExtension.class, name = "debug"),
            @JsonSubTypes.Type(value = WaterfallExtension.class, name = "waterfall")
    })
    @EqualsAndHashCode
    @ToString
    public static class Extension {
    }

    @Value(staticConstructor = "of")
    @EqualsAndHashCode(callSuper = true)
    public static class DebugExtension extends Extension {
        ObjectNode responseExt;

        public static DebugExtension of(ExtBidResponse responseExt) {
            return of(new ObjectMapper().valueToTree(responseExt));
        }
    }

    @Value(staticConstructor = "of")
    @EqualsAndHashCode(callSuper = true)
    public static class WaterfallExtension extends Extension {
        @JacksonXmlProperty(isAttribute = true, localName = "fallback_index")
        Integer fallbackIndex;
    }
}
