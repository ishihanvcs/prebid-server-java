package com.azerion.prebid.handler;

import com.azerion.prebid.auction.GVastResponseCreator;
import com.azerion.prebid.auction.model.GVastParams;
import com.azerion.prebid.auction.requestfactory.GVastContext;
import com.azerion.prebid.auction.requestfactory.GVastRequestFactory;
import com.azerion.prebid.settings.model.Placement;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Azerion /gvast GET handler similar to Iceberg's /advast
 * <p>
 * To reuse much of the existing Prebid Server logic, this handler was created by copying the AuctionHandler
 * class. AuctionHandler uses POST and oRTB 2.5 protocol and hence additional conversion is needed from the /gvast GET
 * request to oRTB2.5 /auction POST request. Similarly, the /auction oRTB2.5 response is used for constructing
 * the /gvast VAST XML response.
 * <p>
 * In the future, when new code is being merged from the master Github prebid-server-java, any relevant changes
 * in the AuctionHandler class should be merged in this class too.
 * <p>
 * INPUT: GET params following Iceberg's /advast handler:
 * p           Polaris placement ID
 * debug=1     Turn on debug output
 * gdpr        TCF2 consent string
 * referrer    Encoded referrer URL
 * Additional params not found in /advast:
 * cust_params Encoded string with key-values sent to Google Ad Manager
 * <p>
 * RESPONSE: a VAST XML containing Google VAST ad tag with attached SSP bids. See https://support.google.com/admanager/table/9749596?hl=en
 * If debug mode is enabled, the response XML will also include a "debug" extension with SSP requests/responses
 * and Prebid cache calls
 */
public class GVastHandler implements Handler<RoutingContext> {

    public static final String END_POINT = "/gvast";

    private static final boolean PRIORITIZE_IMPROVE_DIGITAL_DEALS = true;

    private static final Logger logger = LoggerFactory.getLogger(GVastHandler.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private final GVastRequestFactory gVastRequestFactory;
    private final GVastResponseCreator gVastResponseCreator;
    private final ExchangeService exchangeService;
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final Clock clock;
    private final HttpInteractionLogger httpInteractionLogger;

    public GVastHandler(
            GVastRequestFactory gVastRequestFactory,
            GVastResponseCreator gVastResponseCreator,
            ExchangeService exchangeService,
            AnalyticsReporterDelegator analyticsDelegator,
            Metrics metrics,
            Clock clock,
            HttpInteractionLogger httpInteractionLogger) {
        this.gVastRequestFactory = Objects.requireNonNull(gVastRequestFactory);
        this.gVastResponseCreator = Objects.requireNonNull(gVastResponseCreator);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.httpInteractionLogger = Objects.requireNonNull(httpInteractionLogger);
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.

        final long startTime = clock.millis();
        gVastRequestFactory.fromRequest(routingContext, startTime)
                .map(gVastContext -> this.executeAuction(gVastContext, startTime));
    }

    private GVastContext executeAuction(GVastContext gVastContext, long startTime) {
        final AuctionContext auctionContext = gVastContext.getAuctionContext();
        final RoutingContext routingContext = gVastContext.getRoutingContext();
        final AuctionEvent.AuctionEventBuilder auctionEventBuilder = AuctionEvent.builder()
                .httpContext(HttpContext.from(routingContext));

        addToEvent(auctionContext, auctionEventBuilder::auctionContext, auctionContext);
        updateAppAndNoCookieAndImpsMetrics(auctionContext);
        exchangeService.holdAuction(auctionContext)
                .map(bidResponse -> Tuple2.of(bidResponse, auctionContext))
                .map(result -> addToEvent(result.getLeft(), auctionEventBuilder::bidResponse, result))
                .setHandler(result -> handleResult(result, auctionEventBuilder, gVastContext, startTime));
        return gVastContext;
    }

    private void updateAppAndNoCookieAndImpsMetrics(AuctionContext context) {
        final BidRequest bidRequest = context.getBidRequest();
        final UidsCookie uidsCookie = context.getUidsCookie();

        final List<Imp> imps = bidRequest.getImp();
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(bidRequest.getApp() != null, uidsCookie.hasLiveUids(),
                imps.size());

        metrics.updateImpTypesMetrics(imps);
    }

    private void handleResult(
            AsyncResult<Tuple2<BidResponse, AuctionContext>> responseResult,
            AuctionEvent.AuctionEventBuilder auctionEventBuilder,
            GVastContext gVastContext,
            long startTime
    ) {
        final boolean responseSucceeded = responseResult.succeeded();
        final AuctionContext auctionContext = responseSucceeded ? responseResult.result().getRight() : null;
        final BidResponse bidResponse = responseSucceeded ? responseResult.result().getLeft() : null;
        final GVastParams gVastParams = gVastContext.getGVastParams();
        final Placement placement = gVastContext.getPlacement();
        final RoutingContext routingContext = gVastContext.getRoutingContext();

        final MetricName requestType = auctionContext != null
                ? auctionContext.getRequestTypeMetric()
                : MetricName.openrtb2web;

        final MetricName metricRequestStatus;
        final List<String> errorMessages;
        final int status;
        final String body;

        if (responseSucceeded) {
            metricRequestStatus = MetricName.ok;
            errorMessages = Collections.emptyList();
            status = HttpResponseStatus.OK.code();
            body = gVastResponseCreator.create(gVastParams, placement, routingContext, bidResponse,
                    PRIORITIZE_IMPROVE_DIGITAL_DEALS);
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                metricRequestStatus = MetricName.badinput;

                final InvalidRequestException invalidRequestException = (InvalidRequestException) exception;
                errorMessages = invalidRequestException.getMessages().stream()
                        .map(msg -> String.format("Invalid request format: %s", msg))
                        .collect(Collectors.toList());
                final String message = String.join("\n", errorMessages);

                conditionalLogger.info(String.format("%s, Referer: %s", message,
                        routingContext.request().headers().get(HttpUtil.REFERER_HEADER)), 100);

                status = HttpResponseStatus.BAD_REQUEST.code();
                body = message;
            } else if (exception instanceof UnauthorizedAccountException) {
                metricRequestStatus = MetricName.badinput;
                final String message = exception.getMessage();
                conditionalLogger.info(message, 100);
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.UNAUTHORIZED.code();
                body = message;
                final String accountId = ((UnauthorizedAccountException) exception).getAccountId();
                metrics.updateAccountRequestRejectedMetrics(accountId);
            } else if (exception instanceof BlacklistedAppException
                    || exception instanceof BlacklistedAccountException) {
                metricRequestStatus = exception instanceof BlacklistedAccountException
                        ? MetricName.blacklisted_account : MetricName.blacklisted_app;
                final String message = String.format("Blacklisted: %s", exception.getMessage());
                logger.debug(message);

                errorMessages = Collections.singletonList(message);
                status = HttpResponseStatus.FORBIDDEN.code();
                body = message;
            } else {
                metricRequestStatus = MetricName.err;
                logger.error("Critical error while running the auction", exception);

                final String message = exception.getMessage();
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
                body = String.format("Critical error while running the auction: %s", message);
            }
        }

        final AuctionEvent auctionEvent = auctionEventBuilder.status(status).errors(errorMessages).build();
        final PrivacyContext privacyContext = auctionContext != null ? auctionContext.getPrivacyContext() : null;
        final TcfContext tcfContext = privacyContext != null ? privacyContext.getTcfContext() : TcfContext.empty();
        respondWith(routingContext, status, body, startTime, requestType, metricRequestStatus, auctionEvent,
                tcfContext);

        httpInteractionLogger.maybeLogOpenrtb2Auction(auctionContext, routingContext, status, body);
    }

    private void respondWith(RoutingContext context, int status, String body, long startTime, MetricName requestType,
                             MetricName metricRequestStatus, AuctionEvent event, TcfContext tcfContext) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            metrics.updateRequestTypeMetric(requestType, MetricName.networkerr);
        } else {
            context.response()
                    .exceptionHandler(throwable -> handleResponseException(throwable, requestType))
                    .setStatusCode(status)
                    .end(body);

            metrics.updateRequestTimeMetric(requestType, clock.millis() - startTime);
            metrics.updateRequestTypeMetric(requestType, metricRequestStatus);
            analyticsDelegator.processEvent(event, tcfContext);
        }
    }

    private void handleResponseException(Throwable throwable, MetricName requestType) {
        logger.warn("Failed to send auction response: {0}", throwable.getMessage());
        metrics.updateRequestTypeMetric(requestType, MetricName.networkerr);
    }
}
