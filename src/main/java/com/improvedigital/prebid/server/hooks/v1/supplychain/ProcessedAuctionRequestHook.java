package com.improvedigital.prebid.server.hooks.v1.supplychain;

import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.LogMessage;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchainNode;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtSourceSchain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final ExtSourceSchain SCHAIN_EMPTY = ExtSourceSchain.of(
            "1.0", 1, new ArrayList<>(), null
    );
    private static final String SCHAIN_DOMAIN_DEFAULT = "headerlift.com";
    private static final boolean SCHAIN_PARTY_PAID_DEFAULT = true;

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

    private ExtSourceSchain mergeSupplyChain(AuctionRequestPayload auctionRequestPayload) {
        // If we have multiple schain nodes, then we want them to be exactly same.
        // Otherwise, it means inconsistencies.
        ImprovedigitalPbsImpExt impExtToUse = auctionRequestPayload.bidRequest().getImp().stream()
                .map(imp -> jsonUtils.getImprovedigitalPbsImpExt(imp))
                .filter(Objects::nonNull)
                // No sid we can use if ext.prebid.improvedigitalpbs.headerliftPartnerId=null.
                .filter(improvedigitalPbsImpExt -> improvedigitalPbsImpExt.getHeaderliftPartnerId() != null)
                .reduce((result, e) -> result != null
                        && Objects.equals(result.getHeaderliftPartnerId(), e.getHeaderliftPartnerId())
                        && Objects.equals(result.getSchainNodes(), e.getSchainNodes()) ? result : null)
                .filter(Objects::nonNull)
                .orElse(null);
        if (impExtToUse == null) {
            logger.error(LogMessage
                    .from(auctionRequestPayload.bidRequest())
                    .withMessage("Supply chain configuration has mismatching values in multiple imp")
                    .withFrequency(1000)
            );
            return null;
        }

        final ExtSourceSchain existingSchain = getExtSourceSchain(auctionRequestPayload);
        return ExtSourceSchain.of(
                existingSchain.getVer(),
                existingSchain.getComplete(),
                Stream.concat(
                        existingSchain.getNodes().stream(),
                        toSchainNodes(auctionRequestPayload.bidRequest().getId(), impExtToUse).stream()
                                .filter(schainNode -> !containsSchainNode(existingSchain, schainNode.getAsi()))
                ).collect(Collectors.toList()),
                existingSchain.getExt()
        );
    }

    private List<ExtRequestPrebidSchainSchainNode> toSchainNodes(String requestId, ImprovedigitalPbsImpExt impExt) {
        String sid = impExt.getHeaderliftPartnerId();

        // ext.prebid.improvedigitalpbs.schainNodes=null: means we add default schain.
        if (impExt.getSchainNodes() == null) {
            return Arrays.asList(toSchainNode(SCHAIN_DOMAIN_DEFAULT, sid, requestId));
        }

        // ext.prebid.improvedigitalpbs.schainNodes=[]: means nothing to add.
        if (impExt.getSchainNodes().size() <= 0) {
            return null;
        }

        // Future work. As of now, we only work for default domain.
        // Later, we re-visit what to do with multiple values in schain nodes.
        if (impExt.getSchainNodes().contains(SCHAIN_DOMAIN_DEFAULT)) {
            return Arrays.asList(toSchainNode(SCHAIN_DOMAIN_DEFAULT, sid, requestId));
        }

        return null;
    }

    private ExtRequestPrebidSchainSchainNode toSchainNode(String domain, String sid, String requestId) {
        return ExtRequestPrebidSchainSchainNode.of(
                domain, sid, SCHAIN_PARTY_PAID_DEFAULT ? 1 : 0, requestId, null, domain, null
        );
    }

    private ExtSourceSchain getExtSourceSchain(AuctionRequestPayload auctionRequestPayload) {
        if (auctionRequestPayload.bidRequest().getSource() == null) {
            return SCHAIN_EMPTY;
        }

        if (auctionRequestPayload.bidRequest().getSource().getExt() == null) {
            return SCHAIN_EMPTY;
        }

        ExtSourceSchain existingSchain = auctionRequestPayload.bidRequest().getSource().getExt().getSchain();
        if (existingSchain == null) {
            return SCHAIN_EMPTY;
        }

        return existingSchain;
    }

    private boolean containsSchainNode(ExtSourceSchain schain, String nodeAsiToCheck) {
        return schain.getNodes().stream()
                .anyMatch(existingNode -> existingNode.getAsi().equalsIgnoreCase(nodeAsiToCheck));
    }
}

