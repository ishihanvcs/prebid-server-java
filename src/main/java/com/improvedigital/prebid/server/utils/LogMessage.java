package com.improvedigital.prebid.server.utils;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;

import java.util.List;
import java.util.Map;

@Getter(AccessLevel.NONE)
@Builder(toBuilder = true)
public class LogMessage {

    Throwable throwable;
    String message;
    BidRequest request;
    Imp imp;
    BidResponse response;
    SeatBid seatBid;
    Bid bid;
    String logCounterKey;
    @Getter(AccessLevel.PUBLIC)
    int frequency;

    // static 'from' methods
    public static LogMessage from(Throwable throwable) {
        return LogMessage.builder().throwable(throwable).build();
    }

    public static LogMessage from(String message) {
        return LogMessage.builder().message(message).build();
    }

    public static LogMessage from(BidRequest request) {
        return LogMessage.builder().request(request).build();
    }

    public static LogMessage from(BidResponse response) {
        return LogMessage.builder().response(response).build();
    }

    public static LogMessage from(Imp imp) {
        return LogMessage.builder().imp(imp).build();
    }

    public static LogMessage from(SeatBid seatBid) {
        return LogMessage.builder().seatBid(seatBid).build();
    }

    public static LogMessage from(Bid bid) {
        return LogMessage.builder().bid(bid).build();
    }

    // with methods

    public LogMessage withMessage(String message) {
        return this.toBuilder().message(message).build();
    }

    public LogMessage withLogCounterKey(String logCounterKey) {
        return this.toBuilder().logCounterKey(logCounterKey).build();
    }

    public LogMessage withFrequency(int frequency) {
        return this.toBuilder().frequency(frequency).build();
    }

    public LogMessage with(Throwable throwable) {
        return this.toBuilder().throwable(throwable).build();
    }

    public LogMessage with(BidRequest request) {
        return this.toBuilder().request(request).build();
    }

    public LogMessage with(BidResponse response) {
        return this.toBuilder().response(response).build();
    }

    public LogMessage with(Imp imp) {
        return this.toBuilder().imp(imp).build();
    }

    public LogMessage with(SeatBid seatBid) {
        return this.toBuilder().seatBid(seatBid).build();
    }

    public LogMessage with(Bid bid) {
        return this.toBuilder().bid(bid).build();
    }

    private void appendErrorDetails(StringBuilder sb, Throwable t) {
        StackTraceElement ste = t.getStackTrace()[0];
        final String sourceFile = t.getStackTrace()[0].getFileName();
        sb.append(
                String.format("%s: %s\n", t.getClass().getName(), t.getMessage())
        );
        sb.append("\tat ").append(ste).append("\n");
        int index = 1;
        while (index < t.getStackTrace().length) {
            ste = t.getStackTrace()[index];
            if (!StringUtils.equals(sourceFile, ste.getFileName())) {
                break;
            }
            sb.append("\tat ").append(ste).append("\n");
            index++;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        try {
            sb.append(message != null ? message : "").append("\n");

            if (throwable != null) {
                appendErrorDetails(sb, throwable);
            }

            if (request != null || imp != null) {
                sb.append("Request:").append("\n");
                if (request != null) {
                    appendRequestInfo(sb);
                } else {
                    appendImpInfo(sb, imp);
                }
            }

            if (response != null || seatBid != null || bid != null) {
                sb.append("Response:").append("\n");
                if (response != null) {
                    appendResponseInfo(sb);
                } else if (seatBid != null) {
                    appendSeatBidInfo(sb, seatBid);
                } else {
                    appendBidInfo(sb, bid);
                }
            }
        } catch (Throwable t) {
            // Ideally, this code should never execute.
            // But if we get an error during toString() invocation,
            // we should log it for fixing that first
            appendErrorDetails(sb, t);
        }

        return sb.toString();
    }

    private void appendResponseInfo(StringBuilder sb) {
        if (response.getExt() != null) {
            if (response.getExt().getErrors() != null && response.getExt().getErrors().isEmpty()) {
                appendExtBidderErrors(sb, "Errors", response.getExt().getErrors());
                appendExtBidderErrors(sb, "Warnings", response.getExt().getWarnings());
            }
        }

        if (seatBid == null) {
            if (bid == null) {
                response.getSeatbid().forEach(seatBid1 -> appendSeatBidInfo(sb, seatBid1));
            }
        }
    }

    private void appendExtBidderErrors(StringBuilder sb, String type, Map<String, List<ExtBidderError>> errors) {
        if (response.getExt().getErrors() != null && response.getExt().getErrors().isEmpty()) {
            sb.append(String.format("\t%s\n", type));
            response.getExt().getErrors().forEach((key, errorList) -> {
                sb.append(String.format("\t\t%s:\n", key));
                if (!errorList.isEmpty()) {
                    errorList.forEach(error -> {
                        sb.append(String.format("\t\t\t%d:%s\n", error.getCode(), error.getMessage()));
                    });
                }
            });
        }
    }

    private void appendSeatBidInfo(StringBuilder sb, SeatBid seatBid) {
        sb.append(String.format("\tseatbid[%s].ext: ", seatBid.getSeat())).append("\n");
        if (bid == null && seatBid.getBid() != null) {
            seatBid.getBid().forEach(b -> appendBidInfo(sb, b));
        }
    }

    private void appendBidInfo(StringBuilder sb, Bid bid) {
        sb.append("\t\tbid.impid: ").append(bid.getImpid()).append("\n");
        sb.append("\t\tbid.price: ").append(bid.getPrice()).append("\n");
        sb.append("\t\tbid.dealid: ").append(bid.getDealid()).append("\n");
        sb.append("\t\tbid.ext: ").append(bid.getExt()).append("\n");
    }

    private void appendImpInfo(StringBuilder sb, Imp imp) {
        sb.append(String.format("\timp[%s].ext: ", imp.getId())).append(imp.getExt()).append("\n");
    }

    private void appendRequestInfo(StringBuilder sb) {
        if (request.getSite() != null) {
            final Site site = request.getSite();
            sb.append("\tsite.page: ").append(site.getPage()).append("\n");
            sb.append("\tsite.domain: ").append(site.getDomain()).append("\n");
        } else if (request.getApp() != null) {
            final App app = request.getApp();
            sb.append("\tapp.name: ").append(app.getName()).append("\n");
            sb.append("\tapp.bundle: ").append(app.getBundle()).append("\n");
            sb.append("\tapp.storeurl: ").append(app.getStoreurl()).append("\n");
        }

        if (imp == null) {
            request.getImp().forEach(i -> {
                appendImpInfo(sb, i);
            });
        }
    }

    public String resolveLogCounterKey() {
        if (StringUtils.isNotBlank(logCounterKey)) {
            return logCounterKey;
        }

        if (StringUtils.isNotBlank(message)) {
            return message;
        }

        if (throwable != null && StringUtils.isNotBlank(throwable.getMessage())) {
            return throwable.getMessage();
        }

        return toString();
    }
}
