package com.azerion.prebid.auction.customtrackers;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Objects;

@SuperBuilder(toBuilder = true)
@Getter
public class BidResponseModifierContext {

    @NonNull
    ApplicationContext applicationContext;
    @NonNull
    BidRequest bidRequest;
    @NonNull
    BidResponse bidResponse;
    Account account;
    HttpRequestContext httpRequest;
    UidsCookie uidsCookie;

    public BidType getBidType(Bid bid) {
        List<Imp> imps = this.getBidRequest().getImp();
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
}
