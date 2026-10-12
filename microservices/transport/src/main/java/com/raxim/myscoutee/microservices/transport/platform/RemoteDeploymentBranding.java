package com.raxim.myscoutee.microservices.transport.platform;

import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.domain.platform.api.DeploymentBranding;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Bounded read of the canonical owner's typed result; no local fallback. */
public final class RemoteDeploymentBranding extends RemoteReadTransport implements DeploymentBranding {
    public RemoteDeploymentBranding(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
    @Override public DeploymentBranding.ResolvedDeploymentBranding resolve() {
        return exchange(DeploymentBrandingProtocol.PATH, new TypeReference<DeploymentBrandingProtocol.Result>() {},
                "deployment-branding.read", 16384, value -> Boolean.TRUE.equals(value.resolved()) && value.value() != null && value.value().productName() != null && value.value().logoUrl() != null).value();
    }
}
