package com.improvedigital.prebid.server.hooks.v1.supplychain;

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
import java.util.List;
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

        final ExtSourceSchain newSchain = makeNewSupplyChain(auctionRequestPayload);
        if (newSchain == null) {
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

    private ExtSourceSchain makeNewSupplyChain(AuctionRequestPayload auctionRequestPayload) {
        ImprovedigitalPbsImpExt improvedigitalPbsImpExt = jsonUtils.getImprovedigitalPbsImpExt(
                auctionRequestPayload.bidRequest().getImp().get(0)
        );

        if (improvedigitalPbsImpExt == null) {
            return null;
        }

        List<String> schainNodesToAdd = improvedigitalPbsImpExt.getSchainNodes() == null
                ? List.of(DEFAULT_SCHAIN_DOMAIN) : improvedigitalPbsImpExt.getSchainNodes();

        if (CollectionUtils.isEmpty(schainNodesToAdd)) {
            return null;
        }

        // As of now, we know "sid" for headerlift.com only. So, if we have headerlift.com in the schain
        // and we do not have headerlift partner id, then we do not add any schain.
        if (schainNodesToAdd.contains(DEFAULT_SCHAIN_DOMAIN)
                && StringUtils.isEmpty(improvedigitalPbsImpExt.getHeaderliftPartnerId())) {
            return null;
        }

        return addSupplyChainNodes(
                auctionRequestPayload,
                improvedigitalPbsImpExt.getHeaderliftPartnerId(),
                List.of(DEFAULT_SCHAIN_DOMAIN)
        );
    }

    private ExtSourceSchain addSupplyChainNodes(
            AuctionRequestPayload auctionRequestPayload, String sid, List<String> schainNodesToAdd) {
        final ExtSourceSchain existingSchain = getExtSourceSchain(auctionRequestPayload);
        return ExtSourceSchain.of(
                existingSchain.getVer(),
                existingSchain.getComplete(),
                Stream.concat(
                        existingSchain.getNodes().stream(),
                        schainNodesToAdd.stream()
                                .filter(domainName -> !containsSchainNode(existingSchain, domainName))
                                .map(domainName -> ExtRequestPrebidSchainSchainNode.of(
                                        domainName,
                                        sid,
                                        1,
                                        auctionRequestPayload.bidRequest().getId(),
                                        null,
                                        null,
                                        null
                                ))
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

