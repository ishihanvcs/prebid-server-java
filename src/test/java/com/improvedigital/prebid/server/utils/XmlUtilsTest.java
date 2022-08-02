package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Video;
import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.customvast.model.CustomVast;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlUtilsTest extends UnitTestBase {

    static ObjectMapper mapper = new ObjectMapper();

    static final String SERIALIZED_DEBUG_EXTENSION_PRETTY = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<DebugExtension type=\"debug\">\n"
            + "  <responseExt>\n"
            + "    <nullProp/>\n"
            + "    <stringProp>Value</stringProp>\n"
            + "    <nestedObject>\n"
            + "      <intProp>100</intProp>\n"
            + "    </nestedObject>\n"
            + "    <array>10</array>\n"
            + "    <array>\n"
            + "      <intProp>100</intProp>\n"
            + "    </array>\n"
            + "  </responseExt>\n"
            + "</DebugExtension>\n";

    static final String SERIALIZED_DEBUG_EXTENSION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<DebugExtension type=\"debug\">"
            + "<responseExt>"
            + "<nullProp/>"
            + "<stringProp>Value</stringProp>"
            + "<nestedObject><intProp>100</intProp></nestedObject>"
            + "<array>10</array><array><intProp>100</intProp></array>"
            + "</responseExt>"
            + "</DebugExtension>";

    static final String SERIALIZED_IMP_PRETTY = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<Imp>\n"
            + "  <video>\n"
            + "    <mimes>\n"
            + "      <mimes>video/mp4</mimes>\n"
            + "      <mimes>application/javascript</mimes>\n"
            + "      <mimes>video/webm</mimes>\n"
            + "      <mimes>video/ogg</mimes>\n"
            + "    </mimes>\n"
            + "    <minduration>1</minduration>\n"
            + "    <maxduration>30</maxduration>\n"
            + "    <protocols>\n"
            + "      <protocols>2</protocols>\n"
            + "      <protocols>3</protocols>\n"
            + "      <protocols>5</protocols>\n"
            + "      <protocols>6</protocols>\n"
            + "    </protocols>\n"
            + "    <w>640</w>\n"
            + "    <h>480</h>\n"
            + "    <startdelay>0</startdelay>\n"
            + "    <placement>1</placement>\n"
            + "    <linearity>1</linearity>\n"
            + "    <skip>1</skip>\n"
            + "    <api>\n"
            + "      <api>2</api>\n"
            + "    </api>\n"
            + "  </video>\n"
            + "  <ext>\n"
            + "    <prebid>\n"
            + "      <is_rewarded_inventory>1</is_rewarded_inventory>\n"
            + "      <improvedigitalpbs>\n"
            + "        <waterfall>\n"
            + "          <default>gam_first_look</default>\n"
            + "          <default>gam</default>\n"
            + "          <default>https://ad.360yield.com/advast?p=22137694&amp;w=4&amp;h=3&amp;gdpr={{gdpr_consent}}</default>\n"
            + "        </waterfall>\n"
            + "        <responseType>gvast</responseType>\n"
            + "      </improvedigitalpbs>\n"
            + "      <bidder>\n"
            + "        <pubmatic>\n"
            + "          <publisherId>156946</publisherId>\n"
            + "          <adSlot>a10.com_game_preroll</adSlot>\n"
            + "        </pubmatic>\n"
            + "        <improvedigital>\n"
            + "          <placementId>22137694</placementId>\n"
            + "        </improvedigital>\n"
            + "        <appnexus>\n"
            + "          <use_pmt_rule>false</use_pmt_rule>\n"
            + "          <placement_id>13232361</placement_id>\n"
            + "        </appnexus>\n"
            + "      </bidder>\n"
            + "    </prebid>\n"
            + "  </ext>\n"
            + "</Imp>\n";

    static final String SERIALIZED_IMP = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Imp>"
            + "<video>"
            + "<mimes>"
            + "<mimes>video/mp4</mimes>"
            + "<mimes>application/javascript</mimes>"
            + "<mimes>video/webm</mimes>"
            + "<mimes>video/ogg</mimes>"
            + "</mimes>"
            + "<minduration>1</minduration>"
            + "<maxduration>30</maxduration>"
            + "<protocols>"
            + "<protocols>2</protocols>"
            + "<protocols>3</protocols>"
            + "<protocols>5</protocols>"
            + "<protocols>6</protocols>"
            + "</protocols>"
            + "<w>640</w>"
            + "<h>480</h>"
            + "<startdelay>0</startdelay>"
            + "<placement>1</placement>"
            + "<linearity>1</linearity>"
            + "<skip>1</skip>"
            + "<api>"
            + "<api>2</api>"
            + "</api>"
            + "</video>"
            + "<ext>"
            + "<prebid>"
            + "<is_rewarded_inventory>1</is_rewarded_inventory>"
            + "<improvedigitalpbs>"
            + "<waterfall>"
            + "<default>gam_first_look</default>"
            + "<default>gam</default>"
            + "<default>https://ad.360yield.com/advast?p=22137694&amp;w=4&amp;h=3&amp;gdpr={{gdpr_consent}}</default>"
            + "</waterfall>"
            + "<responseType>gvast</responseType>"
            + "</improvedigitalpbs>"
            + "<bidder>"
            + "<pubmatic>"
            + "<publisherId>156946</publisherId>"
            + "<adSlot>a10.com_game_preroll</adSlot>"
            + "</pubmatic>"
            + "<improvedigital>"
            + "<placementId>22137694</placementId>"
            + "</improvedigital>"
            + "<appnexus>"
            + "<use_pmt_rule>false</use_pmt_rule>"
            + "<placement_id>13232361</placement_id>"
            + "</appnexus>"
            + "</bidder>"
            + "</prebid>"
            + "</ext>"
            + "</Imp>";

    private CustomVast.DebugExtension debugExtension;
    private Imp imp;

    @Before
    public void setup() {
        debugExtension = CustomVast.DebugExtension.of(createObjectNode());
        imp = getStoredImp(defaultStoredImpId);
        assertThat(imp).isNotNull();
    }

    @Test
    public void testSerialize() throws Exception {
        String result = XmlUtils.serialize(debugExtension, true);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(SERIALIZED_DEBUG_EXTENSION_PRETTY);
        result = XmlUtils.serialize(debugExtension, false);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(SERIALIZED_DEBUG_EXTENSION);

        result = XmlUtils.serialize(imp, true);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(SERIALIZED_IMP_PRETTY);
        result = XmlUtils.serialize(imp, false);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(SERIALIZED_IMP);
    }

    @Test
    public void testDeserialize() throws Exception {
        CustomVast.DebugExtension parsedDebugExtension = XmlUtils.deserialize(
                SERIALIZED_DEBUG_EXTENSION_PRETTY, CustomVast.DebugExtension.class
        );
        assertThat(parsedDebugExtension).usingRecursiveComparison().isEqualTo(debugExtension);
        parsedDebugExtension = XmlUtils.deserialize(
                SERIALIZED_DEBUG_EXTENSION, CustomVast.DebugExtension.class
        );
        assertThat(parsedDebugExtension).usingRecursiveComparison().isEqualTo(debugExtension);

        // Deserialization of types created with lombok from XML is very hard
        // due to the absence of data types and difference of array structure
        // in XML. As a result, in most cases it requires either the target class
        // to be extensively annotated or a custom deserializer to be implemented.
        // As we cannot modify core PBS classes, deserialization of Imp from XML
        // is not possible without using custom deserializers for each of the classes
        // wrapped in Imp class. But if we deserialize the XML to a more generic type
        // like ObjectNode, it will succeed. In such cases, we'll have to create the
        // PBS core objects manually ourselves from the deserialized ObjectNode instance.
        Imp temp = XmlUtils.deserialize(
                SERIALIZED_IMP_PRETTY, Imp.class
        );

        assertThat(temp).usingRecursiveComparison().isEqualTo(imp);

        ObjectNode objectNode = XmlUtils.deserialize(
                SERIALIZED_IMP_PRETTY, ObjectNode.class
        );
        assertThat(objectNode).isNotNull();
        assertThat(objectNode.at("/video/mimes/mimes").isMissingNode()).isFalse();
        assertThat(objectNode.at("/video/mimes/mimes").isArray()).isTrue();
        assertThat(objectNode.at("/ext/prebid/bidder").isMissingNode()).isFalse();
        assertThat(objectNode.at("/ext/prebid/bidder").isObject()).isTrue();

        // To parse Imp objects from previously serialized XML strings
        // we might need to use custom deserializers. Here, we've created
        // deserializers for only Imp & Video classes and register them via a Module.
        // But, in real scenario, we might need to define deserializers for all the
        // related classes, like Banner, Audio, Native, Pmp etc.
        SimpleModule impModule = new SimpleModule(
                "impModule",
                Version.unknownVersion(),
                Map.of(
                        Imp.class, new ImpDeserializer(Imp.class),
                        Video.class, new VideoDeserializer(Video.class)
                )
        );

        Imp parsedImp = XmlUtils.deserialize(
                SERIALIZED_IMP_PRETTY, Imp.class, impModule
        );

        assertThat(imp).isNotNull();
        // As XML supports only string types, in the deserialized
        // Imp.ext object, which is an ObjectNode, all number values
        // becomes string and hence we need to do recursive comparison
        // with strict type checking disabled (which is the default behavior).
        assertThat(parsedImp).usingRecursiveComparison().isEqualTo(imp);

        parsedImp = XmlUtils.deserialize(
                SERIALIZED_IMP, Imp.class, impModule
        );

        assertThat(imp).isNotNull();
        // As XML supports only string types, in the deserialized
        // Imp.ext object, which is an ObjectNode, all number values
        // becomes string and hence we need to do recursive comparison
        // with strict type checking disabled (which is the default behavior).
        assertThat(parsedImp).usingRecursiveComparison().isEqualTo(imp);
    }

    private static ObjectNode createObjectNode() {
        return createObjectNode(node -> {
            node.putNull("nullProp");
            node.put("stringProp", "Value");
            node.set("nestedObject", createObjectNode(nestedNode -> nestedNode.put("intProp", 100)));
            ArrayNode arrayNode = node.putArray("array");
            arrayNode.add(10);
            arrayNode.add(createObjectNode(nestedNode -> nestedNode.put("intProp", 100)));
        });
    }

    private static ObjectNode createObjectNode(Consumer<ObjectNode> modifier) {
        ObjectNode node = mapper.createObjectNode();
        if (modifier != null) {
            modifier.accept(node);
        }
        return node;
    }

    static class ImpDeserializer extends XmlDeserializerBase<Imp> {

        ImpDeserializer(Class<Imp> vc) {
            super(vc);
        }

        @Override
        protected Imp deserialize(XmlDeserializerBase.PropertySetter propertySetter) {
            Imp.ImpBuilder builder = Imp.builder();
            propertySetter.setProperty("id", String.class, builder::id)
                    .setProperty("banner", Banner.class, builder::banner)
                    .setListProperty("metric", Metric.class, builder::metric)
                    .setProperty("video", Video.class, builder::video)
                    .setProperty("audio", Audio.class, builder::audio)
                    .setProperty("native", Native.class, builder::xNative)
                    .setProperty("pmp", Pmp.class, builder::pmp)
                    .setProperty("displaymanager", String.class, builder::displaymanager)
                    .setProperty("displaymanagerver", String.class, builder::displaymanagerver)
                    .setProperty("instl", Integer.class, builder::instl)
                    .setProperty("tagid", String.class, builder::tagid)
                    .setProperty("bidfloor", BigDecimal.class, builder::bidfloor)
                    .setProperty("bidfloorcur", String.class, builder::bidfloorcur)
                    .setProperty("clickbrowser", Integer.class, builder::clickbrowser)
                    .setProperty("secure", Integer.class, builder::secure)
                    .setListProperty("iframebuster", String.class, builder::iframebuster)
                    .setProperty("exp", Integer.class, builder::exp)
                    .setProperty("ext", ObjectNode.class, builder::ext);
            return builder.build();
        }
    }

    static class VideoDeserializer extends XmlDeserializerBase<Video> {

        VideoDeserializer(Class<Video> vc) {
            super(vc);
        }

        @Override
        protected Video deserialize(XmlDeserializerBase.PropertySetter propertySetter) {
            Video.VideoBuilder builder = Video.builder();
            propertySetter.setListProperty("mimes", String.class, builder::mimes)
                    .setProperty("minduration", Integer.class, builder::minduration)
                    .setProperty("maxduration", Integer.class, builder::maxduration)
                    .setProperty("maxduration", Integer.class, builder::maxduration)
                    .setListProperty("protocols", Integer.class, builder::protocols)
                    .setProperty("w", Integer.class, builder::w)
                    .setProperty("h", Integer.class, builder::h)
                    .setProperty("startdelay", Integer.class, builder::startdelay)
                    .setProperty("placement", Integer.class, builder::placement)
                    .setProperty("linearity", Integer.class, builder::linearity)
                    .setProperty("skip", Integer.class, builder::skip)
                    .setProperty("skipmin", Integer.class, builder::skipmin)
                    .setProperty("skipafter", Integer.class, builder::skipafter)
                    .setProperty("sequence", Integer.class, builder::sequence)
                    .setListProperty("battr", Integer.class, builder::battr)
                    .setProperty("maxextended", Integer.class, builder::maxextended)
                    .setProperty("minbitrate", Integer.class, builder::minbitrate)
                    .setProperty("maxbitrate", Integer.class, builder::maxbitrate)
                    .setProperty("boxingallowed", Integer.class, builder::boxingallowed)
                    .setListProperty("playbackmethod", Integer.class, builder::playbackmethod)
                    .setProperty("playbackend", Integer.class, builder::playbackend)
                    .setListProperty("delivery", Integer.class, builder::delivery)
                    .setProperty("pos", Integer.class, builder::pos)
                    .setListProperty("companionad", Banner.class, builder::companionad)
                    .setListProperty("api", Integer.class, builder::api)
                    .setListProperty("companiontype", Integer.class, builder::companiontype)
                    .setProperty("ext", ObjectNode.class, builder::ext);
            return builder.build();
        }
    }
}
