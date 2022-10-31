package com.improvedigital.prebid.server.hooks.v1.supplychain;

import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import io.vertx.core.Future;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.springframework.context.ApplicationContext;

public class EntrypointHook implements org.prebid.server.hooks.v1.entrypoint.EntrypointHook {

    private final ApplicationContext applicationContext;

    public EntrypointHook(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Future<InvocationResult<EntrypointPayload>> call(
            EntrypointPayload entrypointPayload, InvocationContext invocationContext) {

        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> entrypointPayload,
                SupplyChainContext.from(applicationContext)
        ));
    }

    @Override
    public String code() {
        return "improvedigital-supplychain-hooks-entrypoint";
    }
}
