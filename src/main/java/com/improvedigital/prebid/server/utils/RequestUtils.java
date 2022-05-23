package com.improvedigital.prebid.server.utils;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.auction.model.VastResponseType;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;

public class RequestUtils {

    private final JsonUtils jsonUtils;

    public RequestUtils(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    public String getAccountId(BidRequest bidRequest) {
        final Publisher publisher = resolvePublisher(bidRequest);
        final String publisherId = publisher != null ? resolvePublisherId(publisher) : null;
        return ObjectUtils.defaultIfNull(publisherId, StringUtils.EMPTY);
    }

    public String getParentAccountId(BidRequest bidRequest) {
        final Publisher publisher = resolvePublisher(bidRequest);
        return publisher != null ? parentAccountIdFromExtPublisher(publisher.getExt()) : StringUtils.EMPTY;
    }

    public Publisher resolvePublisher(BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        final Publisher appPublisher = app != null ? app.getPublisher() : null;
        final Site site = bidRequest.getSite();
        final Publisher sitePublisher = site != null ? site.getPublisher() : null;

        return ObjectUtils.defaultIfNull(appPublisher, sitePublisher);
    }

    /**
     * Resolves what value should be used as a publisher id - either taken from publisher.ext.parentAccount
     * or publisher.id in this respective priority.
     */
    public String resolvePublisherId(Publisher publisher) {
        final String parentAccountId = parentAccountIdFromExtPublisher(publisher.getExt());
        return ObjectUtils.defaultIfNull(parentAccountId, publisher.getId());
    }

    /**
     * Parses publisher.ext and returns parentAccount value from it. Returns null if any parsing error occurs.
     */
    public String parentAccountIdFromExtPublisher(ExtPublisher extPublisher) {
        final ExtPublisherPrebid extPublisherPrebid = extPublisher != null ? extPublisher.getPrebid() : null;
        return extPublisherPrebid != null ? StringUtils.stripToNull(extPublisherPrebid.getParentAccount()) : null;
    }

    public String getStoredRequestId(ExtRequest extRequest) {
        ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);
        ExtStoredRequest storedRequest = ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getStoredrequest);
        return ObjectUtil.getIfNotNull(storedRequest, ExtStoredRequest::getId);
    }

    public String getStoredImpId(ExtImp extImp) {
        ExtImpPrebid prebid = ObjectUtil.getIfNotNull(extImp, ExtImp::getPrebid);
        ExtStoredRequest storedRequest = ObjectUtil.getIfNotNull(prebid, ExtImpPrebid::getStoredrequest);
        return ObjectUtil.getIfNotNull(storedRequest, ExtStoredRequest::getId);
    }

    public boolean isNonVastVideo(Imp imp) {
        return imp.getVideo() != null && Nullable.of(imp).get(jsonUtils::getImprovedigitalPbsImpExt)
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
}
