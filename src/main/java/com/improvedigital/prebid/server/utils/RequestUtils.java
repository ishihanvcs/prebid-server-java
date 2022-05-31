package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.auction.model.VastResponseType;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;

import java.util.ArrayList;

public class RequestUtils {

    public static final String IMPROVE_BIDDER_NAME = "improvedigital";

    private final JsonUtils jsonUtils;

    public RequestUtils(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    public String getAccountId(BidRequest bidRequest) {
        final Publisher publisher = resolvePublisher(bidRequest);
        final Nullable<Publisher> publisherNullable = Nullable.of(publisher);
        return publisherNullable
                .get(this::parentAccountIdFromPublisher)
                .value(publisherNullable
                        .get(Publisher::getId)
                        .get(StringUtils::stripToNull)
                        .value()
                );
    }

    public String getParentAccountId(BidRequest bidRequest) {
        final Publisher publisher = resolvePublisher(bidRequest);
        return Nullable.of(publisher)
                .get(this::parentAccountIdFromPublisher)
                .value();
    }

    private Publisher resolvePublisher(BidRequest bidRequest) {
        return Nullable.of(bidRequest)
                .get(BidRequest::getApp)
                .get(App::getPublisher)
                .value(Nullable.of(bidRequest)
                        .get(BidRequest::getSite)
                        .get(Site::getPublisher)
                        .value());
    }

    /**
     * Parses publisher.ext and returns parentAccount value from it. Returns null if any parsing error occurs.
     */
    private String parentAccountIdFromPublisher(Publisher publisher) {
        return Nullable.of(publisher)
                .get(Publisher::getExt)
                .get(ExtPublisher::getPrebid)
                .get(ExtPublisherPrebid::getParentAccount)
                .get(StringUtils::stripToNull)
                .value();
    }

    public String getStoredRequestId(ExtRequest extRequest) {
        return Nullable.of(extRequest)
                .get(ExtRequest::getPrebid)
                .get(ExtRequestPrebid::getStoredrequest)
                .get(this::storedRequestIdFromExtStoredRequest)
                .value();
    }

    public String getStoredImpId(ExtImp extImp) {
        return Nullable.of(extImp)
                .get(ExtImp::getPrebid)
                .get(ExtImpPrebid::getStoredrequest)
                .get(this::storedRequestIdFromExtStoredRequest)
                .value();
    }

    private String storedRequestIdFromExtStoredRequest(ExtStoredRequest extStoredRequest) {
        return Nullable.of(extStoredRequest)
                .get(ExtStoredRequest::getId)
                .get(StringUtils::stripToNull)
                .value();
    }

    public boolean isNonVastVideo(Imp imp) {
        return Nullable.of(imp).get(Imp::getVideo).isNotNull()
                && Nullable.of(imp)
                    .get(jsonUtils::getImprovedigitalPbsImpExt)
                    .get(pbsImpExt -> pbsImpExt.getResponseType() != VastResponseType.vast)
                    .value(false);
    }

    public boolean isNonVastVideo(Imp imp, ImprovedigitalPbsImpExt impExt) {
        return Nullable.of(imp).get(Imp::getVideo).isNotNull()
                && Nullable.of(impExt)
                    .get(pbsImpExt -> pbsImpExt.getResponseType() != VastResponseType.vast)
                    .value(false);
    }

    public boolean hasNonVastVideo(BidRequest bidRequest) {
        return Nullable.of(bidRequest).get(BidRequest::getImp)
                .value(new ArrayList<>())
                .stream()
                .anyMatch(this::isNonVastVideo);
    }

    public Integer getImprovePlacementId(Imp imp) {
        JsonNode node = extractBidderInfo(imp, IMPROVE_BIDDER_NAME, "/placementId");
        if (!node.isMissingNode() && node.isInt()) {
            return node.asInt();
        }
        return null;
    }

    public JsonNode extractBidderInfo(Imp imp, String bidderName, String path) {
        return Nullable.of(imp)
                .get(Imp::getExt)
                .get(this::normalizeImpExt)
                .get(ExtImp::getPrebid)
                .get(ExtImpPrebid::getBidder)
                .get(node -> node.get(bidderName))
                .get(node -> node.at(path))
                .value(MissingNode.getInstance());
    }

    private ExtImp normalizeImpExt(Object impExt) {
        if (impExt == null) {
            return null;
        }

        if (impExt instanceof ExtImp) {
            return (ExtImp) impExt;
        }

        return jsonUtils.getObjectMapper().convertValue(impExt, ExtImp.class);
    }
}
