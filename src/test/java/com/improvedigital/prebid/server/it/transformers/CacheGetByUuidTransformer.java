package com.improvedigital.prebid.server.it.transformers;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Response;
import com.improvedigital.prebid.server.utils.TestUtils;

import java.util.List;
import java.util.Map;

public class CacheGetByUuidTransformer extends ImprovedigitalResponseTransformer {

    @Override
    public String getName() {
        return "it-test-cache-get-by-uuid";
    }

    @Override
    public Response transform(
            com.github.tomakehurst.wiremock.http.Request request,
            Response response,
            FileSource fileSource,
            Parameters parameters) {
        Map<String, List<String>> queryParams = TestUtils.splitQuery(
                request.getAbsoluteUrl().substring(request.getAbsoluteUrl().indexOf('?') + 1)
        );

        String cacheId = queryParams.get("uuid").get(0);
        if (parameters == null || parameters.get(cacheId) == null) {
            return getErrorResponse(new Throwable("No cached entity found"));
        }
        return getSuccessResponse(parameters.get(cacheId).toString());
    }
}
