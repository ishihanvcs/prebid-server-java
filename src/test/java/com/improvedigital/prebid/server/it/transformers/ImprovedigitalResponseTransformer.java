package com.improvedigital.prebid.server.it.transformers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;

public abstract class ImprovedigitalResponseTransformer extends ResponseTransformer {

    protected static final ObjectMapper TRANSFORMER_MAPPER = new ObjectMapper()
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Override
    public boolean applyGlobally() {
        return false;
    }

    protected <T> T parseRequest(Request request, Class<T> klass) throws JsonProcessingException {
        return TRANSFORMER_MAPPER.readValue(request.getBodyAsString(), klass);
    }

    protected Response getErrorResponse(Throwable e) {
        return Response.response()
                .status(400)
                .body("Cannot perform response transformation: " + e.getMessage())
                .build();
    }

    protected <R> Response getSuccessResponse(R body) throws JsonProcessingException {
        return getSuccessResponse(TRANSFORMER_MAPPER.writeValueAsString(body));
    }

    protected Response getSuccessResponse(String body) {
        return Response.response()
                .status(200)
                .body(body)
                .build();
    }
}
