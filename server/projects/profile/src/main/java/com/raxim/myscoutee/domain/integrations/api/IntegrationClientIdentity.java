package com.raxim.myscoutee.domain.integrations.api;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
/** Details issued after integration-key or OAuth-grant verification. */
public interface IntegrationClientIdentity {
 String ownerUserId(); String tokenId(); String clientId(); String scope(); String grantId(); IntegrationAccess.Mode accessMode();
}
