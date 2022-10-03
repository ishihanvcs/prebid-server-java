package com.improvedigital.prebid.server.it.transformers;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Response;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;

import java.util.stream.Collectors;

public class CacheSetByContentTransformer extends ImprovedigitalResponseTransformer {

    @Override
    public String getName() {
        return "it-test-cache-set-by-content";
    }

    @Override
    public Response transform(
            com.github.tomakehurst.wiremock.http.Request request,
            Response response,
            FileSource fileSource,
            Parameters parameters) {
        try {
            return getSuccessResponse(BidCacheResponse.of(
                    parseRequest(request, BidCacheRequest.class).getPuts()
                            .stream()
                            .map(p -> parameters == null ? null : parameters.get(p.getValue().textValue()))
                            .map(uuid -> CacheObject.of(uuid == null ? "" : uuid.toString()))
                            .collect(Collectors.toList())
            ));
        } catch (Exception e) {
            return getErrorResponse(e);
        }
    }
}
