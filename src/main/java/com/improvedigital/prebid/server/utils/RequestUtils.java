package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.customvast.model.VastResponseType;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.spring.config.bidder.ImprovedigitalConfiguration;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

public class RequestUtils {

    public static final String IMPROVE_DIGITAL_BIDDER_NAME;

    static {
        IMPROVE_DIGITAL_BIDDER_NAME = ReflectionUtils.getPrivateProperty(
                "BIDDER_NAME", ImprovedigitalConfiguration.class
        );
    }

    private final JsonUtils jsonUtils;

    public RequestUtils(JsonUtils jsonUtils) {
        this.jsonUtils = Objects.requireNonNull(jsonUtils);
    }

    public JsonUtils getJsonUtils() {
        return jsonUtils;
    }

    public String getAccountId(BidRequest bidRequest) {
        final Publisher publisher = resolvePublisher(bidRequest);
        final Optional<Publisher> publisherOptional = Optional.ofNullable(publisher);
        return publisherOptional
                .map(this::parentAccountIdFromPublisher)
                .orElse(publisherOptional
                        .map(Publisher::getId)
                        .map(StringUtils::stripToNull)
                        .orElse(null)
                );
    }

    public String getParentAccountId(BidRequest bidRequest) {
        final Publisher publisher = resolvePublisher(bidRequest);
        return Optional.ofNullable(publisher)
                .map(this::parentAccountIdFromPublisher)
                .orElse(null);
    }

    private Publisher resolvePublisher(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getApp)
                .map(App::getPublisher)
                .orElse(Optional.ofNullable(bidRequest)
                        .map(BidRequest::getSite)
                        .map(Site::getPublisher)
                        .orElse(null));
    }

    /**
     * Parses publisher.ext and returns parentAccount value from it. Returns null if any parsing error occurs.
     */
    private String parentAccountIdFromPublisher(Publisher publisher) {
        return Optional.ofNullable(publisher)
                .map(Publisher::getExt)
                .map(ExtPublisher::getPrebid)
                .map(ExtPublisherPrebid::getParentAccount)
                .map(StringUtils::stripToNull)
                .orElse(null);
    }

    public String getStoredRequestIdFromExtRequest(ExtRequest extRequest) {
        return Optional.ofNullable(extRequest)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getStoredrequest)
                .map(this::storedRequestIdFromExtStoredRequest)
                .orElse(null);
    }

    public String getStoredRequestId(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getExt)
                .map(this::getStoredRequestIdFromExtRequest)
                .orElse(null);
    }

    public String getStoredRequestIdFromExtRequestNode(ObjectNode extRequest) {
        return Optional.ofNullable(extRequest)
                .map(node -> node.at("/prebid/storedrequest/id"))
                .map(node -> node.isMissingNode() ? null : node.asText())
                .orElse(null);
    }

    public String getStoredImpIdFromExtImp(ExtImp extImp) {
        return Optional.ofNullable(extImp)
                .map(ExtImp::getPrebid)
                .map(ExtImpPrebid::getStoredrequest)
                .map(this::storedRequestIdFromExtStoredRequest)
                .orElse(null);
    }

    public String getStoredImpId(Imp imp) {
        return Optional.ofNullable(imp)
                .map(Imp::getExt)
                .map(this::getStoredImpIdFromExtImpNode)
                .orElse(null);
    }

    public String getStoredImpIdFromExtImpNode(ObjectNode extImp) {
        return Optional.ofNullable(extImp)
                .map(node -> node.at("/prebid/storedrequest/id"))
                .map(node -> node.isMissingNode() ? null : node.asText())
                .orElse(null);
    }

    private String storedRequestIdFromExtStoredRequest(ExtStoredRequest extStoredRequest) {
        return Optional.ofNullable(extStoredRequest)
                .map(ExtStoredRequest::getId)
                .map(StringUtils::stripToNull)
                .orElse(null);
    }

    private boolean isOfResponseType(ImprovedigitalPbsImpExt pbsImpExt, VastResponseType responseType) {
        return Optional.ofNullable(pbsImpExt)
                .map(pbsImpExt1 -> pbsImpExt1.responseTypeOrDefault() == responseType)
                .orElse(false);
    }

    public boolean isCustomVastVideo(Imp imp) {
        ImprovedigitalPbsImpExt pbsImpExt = jsonUtils.getImprovedigitalPbsImpExt(imp);
        return isCustomVastVideo(imp, pbsImpExt);
    }

    public boolean isCustomVastVideo(Imp imp, ImprovedigitalPbsImpExt pbsImpExt) {
        return imp != null && imp.getVideo() != null && pbsImpExt != null
                && !isOfResponseType(pbsImpExt, VastResponseType.vast);
    }

    public boolean isCustomVastVideo(Imp imp, ImprovedigitalPbsImpExt pbsImpExt, VastResponseType responseType) {
        return imp != null && imp.getVideo() != null && pbsImpExt != null
                && isOfResponseType(pbsImpExt, responseType);
    }

    public boolean hasGVastResponseType(ImprovedigitalPbsImpExt impExt) {
        return isOfResponseType(impExt, VastResponseType.gvast);
    }

    public boolean hasWaterfallResponseType(ImprovedigitalPbsImpExt impExt) {
        return isOfResponseType(impExt, VastResponseType.waterfall);
    }

    public boolean hasCustomVastVideo(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getImp)
                .orElse(new ArrayList<>())
                .stream()
                .anyMatch(this::isCustomVastVideo);
    }

    public Integer getImprovedigitalPlacementId(Imp imp) {
        JsonNode node = extractBidderInfo(imp, IMPROVE_DIGITAL_BIDDER_NAME, "/placementId");
        if (!node.isMissingNode() && node.isInt()) {
            return node.asInt();
        }
        return null;
    }

    public Integer getImprovedigitalPlacementId(BidRequest bidRequest, String impId) {
        return getImprovedigitalPlacementId(
                bidRequest.getImp().stream()
                        .filter(i -> i.getId().equals(impId))
                        .findFirst()
                        .orElse(null)
        );
    }

    public JsonNode extractBidderInfo(Imp imp, String bidderName, String path) {
        return Optional.ofNullable(imp)
                .map(Imp::getExt)
                .map(node -> node.at("/prebid/bidder/" + bidderName + path))
                .orElse(MissingNode.getInstance());
    }
}
