package com.improvedigital.prebid.server.utils;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ResponseUtils {

    private ResponseUtils() {
    }

    public static List<SeatBid> findSeatBidsForImp(BidResponse bidResponse, Imp imp) {
        return bidResponse.getSeatbid().stream()
                .filter(seatBid -> seatBid.getBid().stream()
                        .anyMatch(bid -> Objects.equals(bid.getImpid(), imp.getId()))
                ).collect(Collectors.toList());
    }

    public static SeatBid findOrCreateSeatBid(String name, BidResponse bidResponse, Imp imp) {
        return findOrCreateSeatBid(name, findSeatBidsForImp(bidResponse, imp));
    }

    public static SeatBid findOrCreateSeatBid(String name, List<SeatBid> seatBidsForImp) {
        return seatBidsForImp.stream()
                .filter(seatBid ->
                        name.equals(seatBid.getSeat())
                ).findFirst().orElse(
                        SeatBid.builder()
                                .seat(name)
                                .bid(new ArrayList<>())
                                .build()
                );
    }

    public static List<Bid> getBidsForImp(SeatBid seatBid, Imp imp) {
        return getBidsForImp(List.of(seatBid), imp);
    }

    public static List<Bid> getBidsForImp(List<SeatBid> seatBids, Imp imp) {
        return seatBids.stream().flatMap(seatBid ->
                        seatBid.getBid().stream().filter(bid ->
                                bid.getImpid().equals(imp.getId())
                        )
                ).collect(Collectors.toList());
    }

    public static List<Bid> getBidsForImp(BidResponse bidResponse, Imp imp) {
        return getBidsForImp(findSeatBidsForImp(bidResponse, imp), imp);
    }
}
