package com.improvedigital.prebid.server.hooks.v1.supplychain;

import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchainNode;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtSourceSchain;

import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    public static final ExtSourceSchain DEFAULT_SCHAIN = ExtSourceSchain.of(
            "1.0", 1, new ArrayList<>(), null
    );
    public static final String DEFAULT_SCHAIN_DOMAIN = "headerlift.com";

    private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);

    private final JsonUtils jsonUtils;

    public ProcessedAuctionRequestHook(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    @Override
    public String code() {
        return "improvedigital-supplychain-hooks-processed-auction-request";
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {

        final ExtSourceSchain newSchain = mergeSupplyChain(auctionRequestPayload);
        if (newSchain == null || CollectionUtils.isEmpty(newSchain.getNodes())) {
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> auctionRequestPayload, invocationContext.moduleContext()
            ));
        }

        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> AuctionRequestPayloadImpl.of(auctionRequestPayload.bidRequest().toBuilder()
                        .source(auctionRequestPayload.bidRequest().getSource().toBuilder()
                                .ext(ExtSource.of(newSchain))
                                .build()
                        )
                        .ext(auctionRequestPayload.bidRequest().getExt())
                        .build()
                ), invocationContext.moduleContext()
        ));
    }

    private ExtRequestPrebidSchainSchainNode getNewSchainNode(String requestId, Imp imp) {
        ImprovedigitalPbsImpExt improvedigitalPbsImpExt = jsonUtils.getImprovedigitalPbsImpExt(imp);
        if (improvedigitalPbsImpExt == null) {
            return null;
        }

        // No sid we can use if ext.prebid.improvedigitalpbs.headerliftPartnerId=null.
        String sid = improvedigitalPbsImpExt.getHeaderliftPartnerId();
        if (StringUtils.isEmpty(sid)) {
            return null;
        }

        // ext.prebid.improvedigitalpbs.schainNodes=null: means we add default schain.
        if (improvedigitalPbsImpExt.getSchainNodes() == null) {
            return ExtRequestPrebidSchainSchainNode.of(
                    DEFAULT_SCHAIN_DOMAIN, sid, 1, requestId, null, DEFAULT_SCHAIN_DOMAIN, null
            );
        }

        // ext.prebid.improvedigitalpbs.schainNodes=[]: means nothing to add.
        if (improvedigitalPbsImpExt.getSchainNodes().size() <= 0) {
            return null;
        }

        if (improvedigitalPbsImpExt.getSchainNodes().contains(DEFAULT_SCHAIN_DOMAIN)) {
            return ExtRequestPrebidSchainSchainNode.of(
                    DEFAULT_SCHAIN_DOMAIN, sid, 1, requestId, null, DEFAULT_SCHAIN_DOMAIN, null
            );
        }

        // Future logic to support schains other than our default...

        return null;
    }

    private ExtSourceSchain mergeSupplyChain(
            AuctionRequestPayload auctionRequestPayload) {
        final ExtSourceSchain existingSchain = getExtSourceSchain(auctionRequestPayload);
        return ExtSourceSchain.of(
                existingSchain.getVer(),
                existingSchain.getComplete(),
                Stream.concat(
                        existingSchain.getNodes().stream(),
                        auctionRequestPayload.bidRequest().getImp().stream()
                                .map(imp -> getNewSchainNode(auctionRequestPayload.bidRequest().getId(), imp))
                                .filter(Objects::nonNull)
                                .filter(schainNode -> !containsSchainNode(existingSchain, schainNode.getAsi()))
                ).collect(Collectors.toList()),
                existingSchain.getExt()
        );
    }

    private ExtSourceSchain getExtSourceSchain(AuctionRequestPayload auctionRequestPayload) {
        if (auctionRequestPayload.bidRequest().getSource() == null) {
            return DEFAULT_SCHAIN;
        }

        if (auctionRequestPayload.bidRequest().getSource().getExt() == null) {
            return DEFAULT_SCHAIN;
        }

        ExtSourceSchain existingSchain = auctionRequestPayload.bidRequest().getSource().getExt().getSchain();
        if (existingSchain == null) {
            return DEFAULT_SCHAIN;
        }

        return existingSchain;
    }

    private boolean containsSchainNode(ExtSourceSchain schain, String nodeAsiToCheck) {
        return schain.getNodes().stream()
                .anyMatch(existingNode -> existingNode.getAsi().equalsIgnoreCase(nodeAsiToCheck));
    }
}

