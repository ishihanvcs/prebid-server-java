package com.improvedigital.prebid.server.it.transformers;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Response;
import com.iab.openrtb.request.BidRequest;

public class BidResponseByImpidTransformer extends ImprovedigitalResponseTransformer {

    @Override
    public String getName() {
        return "it-test-bid-response-by-impid";
    }

    @Override
    public Response transform(
            com.github.tomakehurst.wiremock.http.Request request,
            Response response,
            FileSource fileSource,
            Parameters parameters) {
        try {
            BidRequest bidRequest = parseRequest(request, BidRequest.class);
            if (bidRequest.getImp().size() != 1) {
                throw new IllegalArgumentException("SSP can deal only 1 imp");
            }

            return getSuccessResponse(parameters.get(bidRequest.getImp().get(0).getId()).toString());
        } catch (Exception e) {
            return getErrorResponse(e);
        }
    }
}
