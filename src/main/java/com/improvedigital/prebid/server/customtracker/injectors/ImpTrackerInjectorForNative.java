package com.improvedigital.prebid.server.customtracker.injectors;

import com.iab.openrtb.request.ntv.EventTrackingMethod;
import com.iab.openrtb.request.ntv.EventType;
import com.iab.openrtb.response.EventTracker;
import com.iab.openrtb.response.Response;
import com.improvedigital.prebid.server.customtracker.contracts.IBidTypeSpecificTrackerInjector;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.ix.model.response.NativeV11Wrapper;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.ObjectUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImpTrackerInjectorForNative implements IBidTypeSpecificTrackerInjector {

    private static final Logger logger = LoggerFactory.getLogger(ImpTrackerInjectorForNative.class);
    private final JacksonMapper mapper;

    public ImpTrackerInjectorForNative(JacksonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String inject(String trackingUrl, String adm, String bidder) {
        /**
         * Response handling is inspired by {@link org.prebid.server.bidder.ix.IxBidder}
         */
        final NativeV11Wrapper nativeV11 = admToNativeResponse(adm, NativeV11Wrapper.class);
        final Response responseV11 = ObjectUtil.getIfNotNull(nativeV11, NativeV11Wrapper::getNativeResponse);
        final boolean isV11 = responseV11 != null;
        final Response response = isV11 ? responseV11 : admToNativeResponse(adm, Response.class);
        Response.ResponseBuilder newResponseBuilder = response.toBuilder();

        final List<EventTracker> existingEventTrackers = ObjectUtil.getIfNotNull(response, Response::getEventtrackers);
        if (CollectionUtils.isNotEmpty(existingEventTrackers)) {
            logger.debug("Adding custom trackers to native response eventtrackers. trackingUrl={0}", trackingUrl);
            newResponseBuilder.eventtrackers(
                    Stream.concat(
                            existingEventTrackers.stream(),
                            Arrays.asList(EventTracker.builder()
                                    .event(EventType.IMPRESSION.getValue())
                                    .method(EventTrackingMethod.IMAGE.getValue())
                                    .url(trackingUrl)
                                    .build()
                            ).stream()
                    ).collect(Collectors.toList())
            );
        }

        final List<String> impTrackers = ObjectUtil.getIfNotNull(response, Response::getImptrackers);
        if (CollectionUtils.isNotEmpty(impTrackers)) {
            logger.debug("Adding custom trackers to native response imptrackers. trackingUrl={0}", trackingUrl);
            newResponseBuilder.imptrackers(
                    Stream.concat(
                            impTrackers.stream(),
                            Arrays.asList(trackingUrl).stream()
                    ).distinct().collect(Collectors.toList())
            );
        }

        Response newResponse = newResponseBuilder.build();

        return mapper.encodeToString(
                isV11 ? NativeV11Wrapper.of(newResponse) : newResponse
        );
    }

    private <T> T admToNativeResponse(String adm, Class<T> clazz) {
        try {
            return mapper.decodeValue(adm, clazz);
        } catch (IllegalArgumentException | DecodeException e) {
            return null;
        }
    }
}
