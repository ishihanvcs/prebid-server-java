package com.improvedigital.prebid.server.customvast.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.improvedigital.prebid.server.utils.JsonUtils;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Getter
@ToString
public class CreatorContext extends GVastHandlerParams {

    BidRequest bidRequest;
    ObjectNode extBidResponse;
    List<Bid> bids;
    Imp imp;
    List<String> waterfall;
    ImprovedigitalPbsImpExtGam gamConfig;

    @Builder.Default
    boolean prioritizeImproveDeals = true;

    @Builder.Default
    VastResponseType responseType = VastResponseType.gvast;

    public static CreatorContext from(HooksModuleContext context, JsonUtils jsonUtils) {
        Objects.requireNonNull(context);

        Objects.requireNonNull(context.getBidRequest());
        Objects.requireNonNull(context.getBidResponse());
        Objects.requireNonNull(jsonUtils);

        BidRequest request = context.getBidRequest();
        BidResponse response = context.getBidResponse();

        CreatorContextBuilder<?, ?> builder = CreatorContext.builder()
                .bidRequest(request)
                .alpha3Country(context.getAlpha3Country());

        final Optional<Site> siteOptional = Optional.ofNullable(request.getSite());
        final Optional<App> appOptional = Optional.ofNullable(request.getApp());
        final Optional<Regs> regsOptional = Optional.ofNullable(request.getRegs());
        final Optional<User> userOptional = Optional.ofNullable(request.getUser());
        final Optional<Device> deviceOptional = Optional.ofNullable(request.getDevice());

        builder.debug(Optional.ofNullable(request.getTest())
                .orElse(0)
                .equals(1)
        );

        if (response.getExt() != null) {
            builder.extBidResponse(
                    jsonUtils.valueToTree(response.getExt())
            );
        }

        builder.gdpr(ObjectUtils.firstNonNull(
                regsOptional.map(Regs::getGdpr)
                        .map(String::valueOf)
                        .orElse(null),
                regsOptional.map(Regs::getExt)
                        .map(ExtRegs::getGdpr)
                        .map(String::valueOf)
                        .orElse(null),
                StringUtils.EMPTY
        ));

        builder.gdprConsent(ObjectUtils.firstNonNull(
                userOptional.map(User::getConsent)
                        .orElse(null),
                userOptional.map(User::getExt)
                        .map(ExtUser::getConsent)
                        .orElse(null),
                StringUtils.EMPTY
        ));

        builder.ifa(deviceOptional.map(Device::getIfa).orElse(null));
        builder.lmt(deviceOptional.map(Device::getLmt).orElse(null));
        builder.os(deviceOptional.map(Device::getOs).orElse(null));

        builder.cat(siteOptional.map(Site::getCat).orElse(null));

        final String bundleId = appOptional.map(App::getBundle).orElse(null);
        final boolean isApp = StringUtils.isNotBlank(bundleId);
        builder.bundle(bundleId);

        if (isApp) {
            builder.referrer(appOptional.map(App::getStoreurl)
                    .orElse(bundleId + ".adsenseformobileapps.com/"));
        } else {
            builder.referrer(siteOptional.map(Site::getPage).orElse(null));
        }

        return builder.build();
    }

    public CreatorContext with(
            Imp imp,
            List<Bid> bids,
            JsonUtils jsonUtils) {
        Objects.requireNonNull(imp);
        Objects.requireNonNull(jsonUtils);
        final ImprovedigitalPbsImpExt improvedigitalPbsImpExt = jsonUtils.getImprovedigitalPbsImpExt(imp);
        CreatorContextBuilder<?, ?> builder = this.toBuilder()
                .bids(bids)
                .imp(imp);

        builder.bidfloor(
                ObjectUtil.getIfNotNull(imp.getBidfloor(), BigDecimal::doubleValue)
        );

        builder.bidfloorcur(
                ObjectUtils.defaultIfNull(imp.getBidfloorcur(), "USD")
        );

        builder.custParams(Optional.ofNullable(imp.getExt())
                .map(impExt -> jsonUtils.objectPathToValue(
                        impExt,
                        "/prebid/bidder/improvedigital/keyValues",
                        CustParams.class
                ))
                .orElse(new CustParams())
        );

        builder.protocols(Optional.ofNullable(imp.getVideo())
                .map(Video::getProtocols)
                .orElse(null)
        );

        return populateBuilderFromImprovedigitalPbsImpExt(
                builder,
                improvedigitalPbsImpExt
        ).build();
    }

    public boolean isApp() {
        return StringUtils.isNotBlank(this.getBundle());
    }

    public String getEncodedReferrer() {
        return Optional.ofNullable(this.getReferrer())
                .map(HttpUtil::encodeUrl)
                .orElse(null);
    }

    public String getGamIdType() {
        if (StringUtils.isBlank(this.getOs())) {
            return null;
        }
        switch (this.getOs().toLowerCase()) {
            case "ios":
                return "idfa";
            case "android":
                return "adid";
            default:
                return null;
        }
    }

    public VastResponseType getResponseType() {
        return ObjectUtils.defaultIfNull(responseType, VastResponseType.gvast);
    }

    public boolean isGVast() {
        return getResponseType() == VastResponseType.gvast;
    }

    public List<String> getWaterfall() {
        List<String> list = ListUtils.emptyIfNull(waterfall);
        return isGVast() && list.isEmpty() ? new ArrayList<>(List.of("gam")) : list;
    }

    public List<String> getWaterfall(boolean isImprovedigitalDeal) {
        if (!isGVast()) {
            return this.getWaterfall();
        }

        final List<String> list = new ArrayList<>(this.getWaterfall());

        // In order to prioritize Improve Digital deal over other ads, a second GAM tag is added
        // The first GAM call will disable AdX/AdSense. In case Improve's VAST doesn't fill or the ad
        // fails to play, the second GAM call is added as a fallback to give AdX a chance to backfill
        if (isImprovedigitalDeal) {
            list.add(0, "gam_improve_deal");
            final int gamIndex = list.indexOf("gam");
            if (gamIndex >= 0) {
                list.set(gamIndex, "gam_no_hb");
            }
        }

        return list;
    }

    @Override
    public Double getBidfloor() {
        return ObjectUtils.defaultIfNull(super.getBidfloor(), 0.0);
    }

    public ImprovedigitalPbsImpExtGam getGamConfig() {
        return ObjectUtils.defaultIfNull(
                gamConfig,
                ImprovedigitalPbsImpExtGam.DEFAULT
        );
    }

    public List<Bid> getBids() {
        return ObjectUtils.defaultIfNull(bids, List.of());
    }

    @Override
    public CustParams getCustParams() {
        return ObjectUtils.defaultIfNull(
                super.getCustParams(),
                new CustParams()
        );
    }

    private CreatorContextBuilder<?, ?> populateBuilderFromImprovedigitalPbsImpExt(
            CreatorContextBuilder<?, ?> builder,
            ImprovedigitalPbsImpExt improvedigitalPbsImpExt
    ) {
        final Optional<ImprovedigitalPbsImpExt> optionalConfig = Optional.ofNullable(improvedigitalPbsImpExt);

        builder.responseType(optionalConfig
                .map(ImprovedigitalPbsImpExt::getResponseType)
                .orElse(VastResponseType.gvast)
        );

        builder.waterfall(
                optionalConfig
                        .map(config -> config.getWaterfall(alpha3Country))
                        .orElse(List.of())
        );

        builder.gamConfig(optionalConfig
                .map(ImprovedigitalPbsImpExt::getImprovedigitalPbsImpExtGam)
                .orElse(
                        null
                )
        );

        return builder;
    }
}
