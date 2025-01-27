package com.improvedigital.prebid.server.customvast;

import com.iab.openrtb.response.Bid;
import com.improvedigital.prebid.server.customvast.model.CreatorContext;
import com.improvedigital.prebid.server.customvast.model.CustomVast;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Class for creating {@link CustomVast} and {@link Bid} that supports waterfall as well as
 * <a href="https://support.google.com/admanager/table/9749596?hl=en">Google Ad Manager tag</a>
 * with params and SSP bids attached. If debug mode is enabled, the {@link CustomVast}
 * will also include a "debug" extension with SSP requests/responses and Prebid cache calls.
 */
public class CustomVastCreator {

    private static final Logger logger = LoggerFactory.getLogger(CustomVastCreator.class);

    private final CustomVastUtils customVastUtils;

    public CustomVastCreator(
            CustomVastUtils customVastUtils
    ) {
        this.customVastUtils = Objects.requireNonNull(customVastUtils);
    }

    public void validateContext(CreatorContext context) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(context.getGdprConsent());
        if (context.isDebug()) {
            Objects.requireNonNull(context.getExtBidResponse());
        }
        if (context.isApp()) {
            Objects.requireNonNull(context.getBundle());
            Objects.requireNonNull(context.getReferrer());
        }
        Objects.requireNonNull(context.getBids());
        Objects.requireNonNull(context.getImp());
        Objects.requireNonNull(context.getWaterfall());
        Objects.requireNonNull(context.getCustParams());
        Objects.requireNonNull(context.getBidfloor());
        Objects.requireNonNull(context.getGamConfig());
    }

    public CustomVast create(
            CreatorContext context,
            Collection<String> userSyncUrls
    ) {
        validateContext(context);
        CustomVast.CustomVastBuilder builder = CustomVast.builder();
        List<String> vastTags = context.isGVast()
                ? new ArrayList<>()
                : customVastUtils.getCachedBidUrls(
                    context.getBids(), context.isPrioritizeImproveDeals()
                );

        String gamPrebidTargeting = null;
        boolean isImprovedigitalDeal = false;
        String custParams = null;
        String categoryTargeting = null;

        if (context.isGVast()) {
            gamPrebidTargeting = customVastUtils.formatPrebidGamKeyValueString(
                    context.getBids(), context.isPrioritizeImproveDeals()
            );
            isImprovedigitalDeal = context.isPrioritizeImproveDeals()
                    && gamPrebidTargeting.contains("hb_deal_improvedigit");
            custParams = context.getCustParams().toString();
            if (context.getCat() != null && context.getCat().size() > 0) {
                categoryTargeting = "iab_cat=" + String.join(",", context.getCat());
            }
        }

        vastTags.addAll(context.getWaterfall(isImprovedigitalDeal));

        // If there's no bid/tag but the debug mode is enabled, respond
        // with a test domain and debug info
        if (context.isDebug() && vastTags.isEmpty()) {
            vastTags.add("https://example.com");
        }

        for (int i = 0; i < vastTags.size(); i++) {
            String adTag = vastTags.get(i);
            boolean addUserSyncs = true;
            boolean addDebugInfo = !context.isGVast() && i == 0;

            if (context.isGVast()) {
                switch (adTag) {
                    // GAM + HB bids
                    case "gam":
                    case "gam_improve_deal":
                        adTag = customVastUtils.buildGamVastTagUrl(
                                context, Stream.of(
                                    gamPrebidTargeting, custParams, categoryTargeting
                                )
                        );
                        addDebugInfo = true;
                        break;
                    case "gam_no_hb":
                        adTag = customVastUtils.buildGamVastTagUrl(
                                context, Stream.of(
                                        custParams, categoryTargeting
                                )
                        );
                        break;
                    // First look is for all low-fill campaigns that should get first look before allowing AdX to monetise
                    // fl=1 -> first look targeting KV
                    // tnl_wog=1 -> disable AdX & AdSense
                    case "gam_first_look":
                        adTag = customVastUtils.buildGamVastTagUrl(
                                context, Stream.of(
                                        custParams, categoryTargeting, "fl=1&tnl_wog=1"
                                )
                        );
                        break;
                    default:
                        addUserSyncs = false;
                }
            }
            builder.ad(
                    createAdForCustomVast(
                            context, i, vastTags.size(), adTag, addUserSyncs ? userSyncUrls : null, addDebugInfo
                    )
            );
        }

        return builder.build();
    }

    public CustomVast.Ad createAdForCustomVast(
            CreatorContext context,
            int adIndex, int adCount,
            String tagUrl, Collection<String> userSyncUrls, boolean addDebugInfo
    ) {
        CustomVast.Wrapper.WrapperBuilder wrapperBuilder = CustomVast.Wrapper.builder()
                .vastAdTagURI(customVastUtils.replaceMacros(tagUrl, context));

        if (adIndex < adCount - 1) { // if not last ad
            wrapperBuilder.fallbackOnNoAd(true);
        }

        if (context.isDebug() && addDebugInfo) {
            wrapperBuilder.extension(
                    CustomVast.DebugExtension.of(
                            context.getExtBidResponse()
                    )
            );
        }

        if (adCount > 1) { // not single ad
            wrapperBuilder.extension(
                    CustomVast.WaterfallExtension.of(adIndex)
            );
        }

        if (userSyncUrls != null && !userSyncUrls.isEmpty() && !context.isApp()) {
            // Inject sync pixels as imp pixels.
            // Only inject for web, not app as apps can't do cookie syncing and rely on device id (IFA) instead
            wrapperBuilder.impressions(userSyncUrls);
        }
        return CustomVast.Ad.of(adIndex, wrapperBuilder.build());
    }
}
