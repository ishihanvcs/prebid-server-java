package com.improvedigital.prebid.server.hooks.v1.supplychain;

import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.SupplyChainNode;
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
import org.prebid.server.proto.openrtb.ext.request.ExtSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final SupplyChain SCHAIN_EMPTY = SupplyChain.of(
            1, new ArrayList<>(), "1.0", null
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

        ImprovedigitalPbsImpExt improvedigitalPbsImpExt = jsonUtils.getImprovedigitalPbsImpExt(
                auctionRequestPayload.bidRequest().getImp().get(0)
        );

        final SupplyChain newSchain = makeNewSupplyChain(auctionRequestPayload, improvedigitalPbsImpExt);
        if (newSchain == null || CollectionUtils.isEmpty(newSchain.getNodes())) {
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> auctionRequestPayload, invocationContext.moduleContext()
            ));
        }

        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> AuctionRequestPayloadImpl.of(auctionRequestPayload.bidRequest().toBuilder()
                        .source(auctionRequestPayload.bidRequest().getSource().toBuilder()
                                .schain(newSchain) /* RTB 2.6 */
                                .ext(ExtSource.of(newSchain)) /* RTB 2.5 */
                                .build()
                        )
                        .ext(auctionRequestPayload.bidRequest().getExt())
                        .build()
                ), invocationContext.moduleContext()
        ));
    }

    private SupplyChain makeNewSupplyChain(
            AuctionRequestPayload auctionRequestPayload, ImprovedigitalPbsImpExt improvedigitalPbsImpExt) {

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

    private SupplyChain addSupplyChainNodes(
            AuctionRequestPayload auctionRequestPayload, String sid, List<String> schainNodesToAdd) {
        final SupplyChain existingSchain = getExtSourceSchain(auctionRequestPayload);
        return SupplyChain.of(
                existingSchain.getComplete(),
                Stream.concat(
                        existingSchain.getNodes().stream(),
                        schainNodesToAdd.stream()
                                .filter(domainName -> !containsSchainNode(existingSchain, domainName))
                                .map(domainName -> SupplyChainNode.of(
                                        domainName,
                                        sid,
                                        auctionRequestPayload.bidRequest().getId(),
                                        null,
                                        null,
                                        1,
                                        null
                                ))
                ).collect(Collectors.toList()),
                existingSchain.getVer(),
                existingSchain.getExt()
        );
    }

    private SupplyChain getExtSourceSchain(AuctionRequestPayload auctionRequestPayload) {
        Source source = auctionRequestPayload.bidRequest().getSource();
        if (source == null) {
            return SCHAIN_EMPTY;
        }

        if (source.getSchain() != null) {
            return source.getSchain();
        }

        if (source.getExt() == null) {
            return SCHAIN_EMPTY;
        }

        if (source.getExt().getSchain() != null) {
            return source.getExt().getSchain();
        }

        return SCHAIN_EMPTY;
    }

    private boolean containsSchainNode(SupplyChain schain, String nodeAsiToCheck) {
        return schain.getNodes().stream()
                .anyMatch(existingNode -> existingNode.getAsi().equalsIgnoreCase(nodeAsiToCheck));
    }
}

