package com.improvedigital.prebid.server.hooks.v1.supplychain;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.SupplyChainNode;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.settings.model.ImprovedigitalPbsAccountExt;
import com.improvedigital.prebid.server.utils.LogMessage;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final SupplyChain SCHAIN_EMPTY = SupplyChain.of(
            1, new ArrayList<>(), "1.0", null
    );
    private static final String SCHAIN_DOMAIN_DEFAULT = "headerlift.com";
    private static final boolean SCHAIN_PARTY_PAID_DEFAULT = true;

    private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);

    private final RequestUtils requestUtils;

    private final ApplicationSettings applicationSettings;

    public ProcessedAuctionRequestHook(RequestUtils requestUtils, ApplicationSettings applicationSettings) {
        this.requestUtils = requestUtils;
        this.applicationSettings = applicationSettings;
    }

    @Override
    public String code() {
        return "improvedigital-supplychain-hooks-processed-auction-request";
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {

        String accountId = requestUtils.getAccountId(auctionRequestPayload.bidRequest());
        if (StringUtils.isEmpty(accountId)) {
            return Future.succeededFuture(getInvocationResult(
                    auctionRequestPayload, invocationContext, null
            ));
        }

        return applicationSettings.getAccountById(accountId, invocationContext.timeout()).map(
                account -> getInvocationResult(
                        auctionRequestPayload, invocationContext, requestUtils.getJsonUtils().getAccountExt(account)
                )
        );
    }

    private InvocationResult<AuctionRequestPayload> getInvocationResult(
            AuctionRequestPayload auctionRequestPayload,
            AuctionInvocationContext invocationContext,
            ImprovedigitalPbsAccountExt accountExt
    ) {
        final SupplyChain newSchain = mergeSupplyChain(auctionRequestPayload, SupplyChainConfig.from(
                accountExt, getSupplyChainConfigFromImpExt(auctionRequestPayload.bidRequest())
        ));
        if (newSchain == null || CollectionUtils.isEmpty(newSchain.getNodes())) {
            return InvocationResultImpl.succeeded(
                    payload -> auctionRequestPayload, invocationContext.moduleContext()
            );
        }

        return InvocationResultImpl.succeeded(
                payload -> auctionRequestPayload, SupplyChainContext.from(newSchain)
        );
    }

    private ImprovedigitalPbsImpExt getSupplyChainConfigFromImpExt(BidRequest bidRequest) {
        // If we have multiple schain nodes, then we want them to be exactly same.
        // Otherwise, it means inconsistencies.
        List<ImprovedigitalPbsImpExt> pbsImpExtsEligible = bidRequest.getImp().stream()
                .map(imp -> requestUtils.getJsonUtils().getImprovedigitalPbsImpExt(imp))
                .filter(Objects::nonNull)
                // We cannot use any sid if ext.prebid.improvedigitalpbs.headerliftPartnerId=null.
                .filter(pbsImpExt -> pbsImpExt.getHeaderliftPartnerId() != null)
                .collect(Collectors.toList());
        if (pbsImpExtsEligible.size() <= 0) {
            return null; /* No imp has any eligible config for supply chain. */
        }

        // Make sure different imp doesn't have different settings because that would mean inconsistencies.
        ImprovedigitalPbsImpExt pbsImpExtToUse = pbsImpExtsEligible.stream()
                .reduce((result, e) -> result != null
                        && Objects.equals(result.getHeaderliftPartnerId(), e.getHeaderliftPartnerId())
                        && Objects.equals(result.getSchainNodes(), e.getSchainNodes()) ? result : null)
                .filter(Objects::nonNull)
                .orElse(null);

        // A logging for us to know something is wrong in the settings.
        if (pbsImpExtToUse == null) {
            logger.error(LogMessage
                    .from(bidRequest)
                    .withMessage("Supply chain configuration has mismatching values in multiple imp")
                    .withFrequency(1000)
            );
            return null;
        }

        return pbsImpExtToUse;
    }

    private SupplyChain mergeSupplyChain(AuctionRequestPayload auctionRequestPayload, SupplyChainConfig schainConfig) {
        if (StringUtils.isEmpty(schainConfig.getHeaderliftPartnerId())) {
            return null;
        }

        SupplyChain existingSchain = getExtSourceSchain(auctionRequestPayload.bidRequest());
        final SupplyChain baseSchain = ObjectUtils.firstNonNull(existingSchain, SCHAIN_EMPTY);
        return SupplyChain.of(
                baseSchain.getComplete(),
                Stream.concat(
                                baseSchain.getNodes().stream(),
                                toSchainNodes(auctionRequestPayload.bidRequest().getId(), schainConfig).stream()
                                        .filter(schainNode -> !containsSchainNode(baseSchain, schainNode.getAsi()))
                        )
                        .collect(Collectors.toList()),
                baseSchain.getVer(),
                baseSchain.getExt()
        );
    }

    private List<SupplyChainNode> toSchainNodes(String requestId, SupplyChainConfig schainConfig) {
        String sid = schainConfig.headerliftPartnerId;

        // ext.prebid.improvedigitalpbs.schainNodes=null: means we add default schain.
        if (schainConfig.getSchainNodes() == null) {
            return Arrays.asList(toSchainNode(SCHAIN_DOMAIN_DEFAULT, sid, requestId));
        }

        // ext.prebid.improvedigitalpbs.schainNodes=[]: means nothing to add.
        if (schainConfig.getSchainNodes().size() <= 0) {
            return null;
        }

        // Future work. As of now, we only work for default domain.
        // Later, we re-visit what to do with multiple values in schain nodes.
        if (schainConfig.getSchainNodes().contains(SCHAIN_DOMAIN_DEFAULT)) {
            return Arrays.asList(toSchainNode(SCHAIN_DOMAIN_DEFAULT, sid, requestId));
        }

        return null;
    }

    private SupplyChainNode toSchainNode(String domain, String sid, String requestId) {
        return SupplyChainNode.of(
                domain, sid, requestId, null, null, SCHAIN_PARTY_PAID_DEFAULT ? 1 : 0, null
        );
    }

    private SupplyChain getExtSourceSchain(BidRequest bidRequest) {
        if (bidRequest.getSource() == null) {
            return null;
        }

        if (bidRequest.getSource().getSchain() != null) {
            return bidRequest.getSource().getSchain();
        }

        if (bidRequest.getSource().getExt() == null) {
            return null;
        }

        SupplyChain existingSchain = bidRequest.getSource().getExt().getSchain();
        if (existingSchain == null) {
            return null;
        }

        return existingSchain;
    }

    private boolean containsSchainNode(SupplyChain schain, String nodeAsiToCheck) {
        return schain.getNodes().stream()
                .anyMatch(existingNode -> existingNode.getAsi().equalsIgnoreCase(nodeAsiToCheck));
    }

    @Getter
    @Builder(toBuilder = true)
    private static class SupplyChainConfig {
        List<String> schainNodes;

        String headerliftPartnerId;

        static SupplyChainConfig from(ImprovedigitalPbsAccountExt accExt, ImprovedigitalPbsImpExt pbsImpExt) {
            return SupplyChainConfig.builder()
                    .headerliftPartnerId(ObjectUtils.firstNonNull(
                            ObjectUtil.getIfNotNull(pbsImpExt, ImprovedigitalPbsImpExt::getHeaderliftPartnerId),
                            ObjectUtil.getIfNotNull(accExt, ImprovedigitalPbsAccountExt::getHeaderliftPartnerId)
                    ))
                    .schainNodes(ObjectUtils.firstNonNull(
                            ObjectUtil.getIfNotNull(pbsImpExt, ImprovedigitalPbsImpExt::getSchainNodes),
                            ObjectUtil.getIfNotNull(accExt, ImprovedigitalPbsAccountExt::getSchainNodes)
                    ))
                    .build();
        }
    }
}

