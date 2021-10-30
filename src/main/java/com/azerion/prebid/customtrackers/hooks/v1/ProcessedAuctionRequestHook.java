package com.azerion.prebid.customtrackers.hooks.v1;

import com.azerion.prebid.customtrackers.CustomTrackerModuleContext;
import com.azerion.prebid.hooks.v1.InvocationResultImpl;
import com.azerion.prebid.settings.SettingsLoader;
import com.azerion.prebid.utils.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.json.JacksonMapper;

import java.util.List;
import java.util.stream.Collectors;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);
    private final SettingsLoader settingsLoader;
    private final JsonUtils jsonUtils;

    public ProcessedAuctionRequestHook(
            SettingsLoader settingsLoader,
            JacksonMapper mapper
    ) {
        this.settingsLoader = settingsLoader;
        this.jsonUtils = new JsonUtils(mapper);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        try {
            final BidRequest bidRequest = auctionRequestPayload.bidRequest();
            final CustomTrackerModuleContext context = getModuleContext(invocationContext, bidRequest);
            return Future.succeededFuture(
                    InvocationResultImpl.succeeded(
                            payload -> AuctionRequestPayloadImpl.of(context.getBidRequest()),
                            context)
            );
        } catch (Throwable e) {
            logger.warn(e.getMessage());
            return Future.succeededFuture(InvocationResultImpl.rejected(e.getMessage()));
        }
    }

    private CustomTrackerModuleContext getModuleContext(
            AuctionInvocationContext invocationContext
    ) {
        if (invocationContext.moduleContext() instanceof CustomTrackerModuleContext) {
            return (CustomTrackerModuleContext) invocationContext.moduleContext();
        }
        return null;
    }

    private CustomTrackerModuleContext getModuleContext(
            AuctionInvocationContext invocationContext,
            BidRequest bidRequest
    ) throws Exception {
        // previous context
        CustomTrackerModuleContext moduleContext = getModuleContext(invocationContext);
        if (moduleContext != null && moduleContext.getPlacement() != null) {
            setPlacementId(bidRequest, moduleContext.getPlacement().getId());
            return moduleContext.with(bidRequest);
        }

        return CustomTrackerModuleContext.builder().bidRequest(bidRequest).build();
    }

    private ObjectNode createNodesWithPlacementId(String objectPath, String placementId) {
        Tuple2<ObjectNode, ObjectNode> rootAndLeaf = jsonUtils.createObjectNodes(objectPath);
        rootAndLeaf.getRight().put("placementId", Long.parseLong(placementId));
        return rootAndLeaf.getLeft();
    }

    private void setPlacementId(BidRequest bidRequest, String placementId) {
        final List<Imp> impList = bidRequest.getImp();
        if (impList.isEmpty()) { // imp is empty
            ObjectNode newNode = createNodesWithPlacementId("prebid/bidder/improvedigital", placementId);
            impList.add(
                    Imp.builder().ext(newNode).build()
            );
            return;
        }
        final List<ObjectNode> extList = impList.stream().map(Imp::getExt).collect(Collectors.toList());

        if (extList.isEmpty()) { // no ext defined
            impList.set(0, impList.get(0).toBuilder()
                    .ext(createNodesWithPlacementId(
                            "prebid/bidder/improvedigital",
                            placementId
                    )).build()
            );
            return;
        }

        ObjectNode improveNode = (ObjectNode) jsonUtils.findFirstNode(extList, "prebid/bidder/improvedigital");
        if (improveNode != null) { // improvedigital node exists
            improveNode.put("placementId", Long.parseLong(placementId));
            return;
        }

        ObjectNode bidderNode = (ObjectNode) jsonUtils.findFirstNode(extList, "prebid/bidder");
        if (bidderNode != null) { // bidder node exists
            improveNode = bidderNode.putObject("improvedigital");
            improveNode.put("placementId", Long.parseLong(placementId));
            return;
        }

        ObjectNode prebidNode = (ObjectNode) jsonUtils.findFirstNode(extList, "prebid");
        bidderNode = createNodesWithPlacementId("bidder/improvedigital", placementId);
        if (prebidNode != null) { // prebid node exists
            prebidNode.set("bidder", bidderNode);
            return;
        }

        final ObjectNode finalBidderNode = bidderNode;
        extList.get(0).set("prebid", finalBidderNode);
    }

    @Override
    public String code() {
        return "custom-tracker-processed-auction-request";
    }
}
