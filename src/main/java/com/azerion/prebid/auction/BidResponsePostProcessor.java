package com.azerion.prebid.auction;

import com.azerion.prebid.auction.customtrackers.TrackerContext;
import com.azerion.prebid.auction.customtrackers.contracts.ITrackingUrlResolver;
import com.azerion.prebid.settings.model.CustomTracker;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

public class BidResponsePostProcessor implements org.prebid.server.auction.BidResponsePostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BidResponsePostProcessor.class);

    private final ApplicationContext applicationContext;

    private final Map<String, CustomTracker> customTrackers;

    public BidResponsePostProcessor(
            ApplicationContext applicationContext,
            Map<String, CustomTracker> customTrackers) {
        this.applicationContext = Objects.requireNonNull(applicationContext);
        this.customTrackers = customTrackers;
    }

    private BidType getBidType(Bid bid, List<Imp> imps) {
        Imp bidImp = imps.stream().filter(imp -> Objects.equals(imp.getId(), bid.getImpid())).findFirst().orElse(null);
        if (bidImp != null) {
            if (bidImp.getBanner() != null) {
                return BidType.banner;
            }

            if (bidImp.getVideo() != null) {
                return BidType.video;
            }

            if (bidImp.getAudio() != null) {
                return BidType.audio;
            }

            if (bidImp.getXNative() != null) {
                return BidType.xNative;
            }
        }
        return null;
    }

    private Bid injectTrackerIntoBidAdm(HttpRequestContext httpRequest,
                                        UidsCookie uidsCookie,
                                        BidRequest bidRequest,
                                        BidResponse bidResponse,
                                        SeatBid seatBid,
                                        Bid bid,
                                        Account account) {

        final BidType bidType = getBidType(bid, bidRequest.getImp());

        if (bidType == null || StringUtils.isBlank(bid.getAdm())) {
            logger.warn("Could not determine bidType or adm value is blank in bid!");
            return bid;
        }
        final Stack<String> admStack = new Stack<>();
        admStack.push(bid.getAdm());
        customTrackers.forEach((tagType, customTracker) -> {
            try {
                TrackerContext context = new TrackerContext(
                        customTracker, applicationContext, httpRequest,
                        uidsCookie, bidRequest, bidResponse,
                        seatBid, bid, bidType, account
                );
                ITrackingUrlResolver urlResolver = context.getUrlResolver();
                String trackingUrl = urlResolver.resolve(context);
                if (trackingUrl != null) {
                    logger.info(String.format("resolved trackingUrl = %s", trackingUrl));
                    admStack.push(
                            context.getInjector()
                                    .inject(trackingUrl, admStack.pop(), seatBid.getSeat(), bidType)
                    );
                }
            } catch (Exception ex) {
                logger.warn(String.format("Could not inject impression tag for tagType = %s", tagType), ex);
            }
        });

        return bid.toBuilder().adm(admStack.pop()).build();
    }

    @Override
    public Future<BidResponse> postProcess(
            HttpRequestContext httpRequest,
            UidsCookie uidsCookie,
            BidRequest bidRequest,
            BidResponse bidResponse,
            Account account) {
        if (customTrackers != null) {
            bidResponse.getSeatbid().forEach(seatBid -> seatBid.getBid().replaceAll(bid -> injectTrackerIntoBidAdm(
                    httpRequest, uidsCookie, bidRequest, bidResponse, seatBid, bid, account
            )));
        }
        return Future.succeededFuture(bidResponse);
    }
}
