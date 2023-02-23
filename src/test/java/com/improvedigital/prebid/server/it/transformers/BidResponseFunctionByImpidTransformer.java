package com.improvedigital.prebid.server.it.transformers;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.iab.openrtb.request.BidRequest;

import java.util.function.Function;

public class BidResponseFunctionByImpidTransformer extends ImprovedigitalResponseTransformer {

    @Override
    public String getName() {
        return "it-test-bid-response-function-by-impid";
    }

    @Override
    public Response transform(
            Request request,
            Response response,
            FileSource fileSource,
            Parameters parameters) {
        try {
            BidRequest bidRequest = parseRequest(request, BidRequest.class);
            if (bidRequest.getImp().size() != 1) {
                throw new IllegalArgumentException("SSP can deal only 1 imp");
            }

            String impId = bidRequest.getImp().get(0).getId();
            Function<BidRequest, String> f = (Function<BidRequest, String>) parameters.get(impId);
            if (f == null) {
                throw new IllegalArgumentException("No function found to deal with imp id=" + impId);
            }

            String bidResponse = f.apply(bidRequest);
            if (bidResponse == null) {
                throw new IllegalArgumentException("No bid response found for imp id=" + impId);
            }

            return getSuccessResponse(bidResponse);
        } catch (Exception e) {
            return getErrorResponse(e);
        }
    }
}
