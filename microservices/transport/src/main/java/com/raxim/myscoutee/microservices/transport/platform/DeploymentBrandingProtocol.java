package com.raxim.myscoutee.microservices.transport.platform;

import com.raxim.myscoutee.domain.platform.api.DeploymentBranding;

public final class DeploymentBrandingProtocol {
    public static final String PATH = "/_internal/v1/platform/branding";
    private DeploymentBrandingProtocol() {}
    public record Result(Boolean resolved, DeploymentBranding.ResolvedDeploymentBranding value) {}
    public record Error(String code) {}
}
