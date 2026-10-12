package com.raxim.myscoutee.domain.platform.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("config")
public class ConfigProperties implements com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration {
    private String operatorUser;
    private String operatorBootstrapEmail;
    private String operatorBootstrapPasswordSha256;
    private boolean operatorBootstrapAllowInsecure;
    private String firebaseServiceAccountPath;
    private boolean firebaseEnabled = false;
    private boolean mcpDemoEnabled = false;
    private List<String> mcpManualRedirectUris = List.of();
    /** Exact deployment-approved callbacks for generated predefined clients; never caller-selected. */
    public List<String> getMcpManualRedirectUris() { return mcpManualRedirectUris; }
    public void setMcpManualRedirectUris(List<String> value) { mcpManualRedirectUris = value == null ? List.of() : List.copyOf(value); }
    /** Explicit dev-only OAuth realm; caller headers never choose the token database. */
    public boolean isMcpDemoEnabled() { return mcpDemoEnabled && !isFirebaseEnabled(); }
    public void setMcpDemoEnabled(boolean value) { mcpDemoEnabled = value; }
    private volatile boolean deploymentFirebaseEnabled = false;
    private boolean chatOnlyMode = false;
    private String memberUser = "member1@myscoutee.local";
    private String facebookAppSecret = "";
    private String websocketAllowedOriginPatterns = "http://localhost,http://localhost:4200,https://localhost,https://localhost:4200";

    public String getOperatorUser() {
        return operatorUser;
    }

    public void setOperatorUser(String operatorUser) {
        this.operatorUser = operatorUser;
    }

    public List<String> getOperatorUsers() {
        return Arrays.stream((operatorUser == null ? "" : operatorUser).split("[,;]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    public boolean isOperatorUser(String value) {
        String normalized = value == null ? "" : value.trim();
        return StringUtils.hasText(normalized)
                && getOperatorUsers().stream().anyMatch(operator -> operator.equalsIgnoreCase(normalized));
    }

    public String getOperatorBootstrapPasswordSha256() {
        return operatorBootstrapPasswordSha256;
    }

    public String getOperatorBootstrapEmail() {
        return operatorBootstrapEmail;
    }

    public void setOperatorBootstrapEmail(String operatorBootstrapEmail) {
        this.operatorBootstrapEmail = operatorBootstrapEmail;
    }

    public void setOperatorBootstrapPasswordSha256(
            String operatorBootstrapPasswordSha256) {
        this.operatorBootstrapPasswordSha256 =
                operatorBootstrapPasswordSha256;
    }

    public boolean isOperatorBootstrapAllowInsecure() {
        return operatorBootstrapAllowInsecure;
    }

    public void setOperatorBootstrapAllowInsecure(
            boolean operatorBootstrapAllowInsecure) {
        this.operatorBootstrapAllowInsecure =
                operatorBootstrapAllowInsecure;
    }

    public String getFirebaseServiceAccountPath() {
        return firebaseServiceAccountPath;
    }

    public void setFirebaseServiceAccountPath(String firebaseServiceAccountPath) {
        this.firebaseServiceAccountPath = firebaseServiceAccountPath;
    }

    public boolean isFirebaseEnabled() {
        return firebaseEnabled || deploymentFirebaseEnabled;
    }

    public void setFirebaseEnabled(boolean firebaseEnabled) {
        this.firebaseEnabled = firebaseEnabled;
    }

    /**
     * Returns the immutable startup flag used by the legacy file-backed
     * Firebase bootstrap. Operator activation is deliberately excluded so a
     * runtime deployment configuration never makes startup try to open the
     * legacy service-account path.
     */
    public boolean isStartupFirebaseEnabled() {
        return firebaseEnabled;
    }

    /**
     * Publishes the active deployment capability to the existing feature
     * gates. The authoritative credentials remain encrypted in MongoDB and are
     * resolved by the runtime Firebase service; this flag contains no secret.
     */
    public void publishDeploymentFirebaseEnabled(
            boolean deploymentFirebaseEnabled) {
        this.deploymentFirebaseEnabled = deploymentFirebaseEnabled;
    }

    public boolean isChatOnlyMode() {
        return chatOnlyMode;
    }

    public void setChatOnlyMode(boolean chatOnlyMode) {
        this.chatOnlyMode = chatOnlyMode;
    }

    public String getMemberUser() {
        return memberUser;
    }

    public void setMemberUser(String memberUser) {
        this.memberUser = memberUser;
    }

    public boolean isMemberFallbackEnabled() {
        return !isFirebaseEnabled() && StringUtils.hasText(memberUser);
    }

    public String getFacebookAppSecret() {
        return facebookAppSecret;
    }

    public void setFacebookAppSecret(String facebookAppSecret) {
        this.facebookAppSecret = facebookAppSecret;
    }

    public String getWebsocketAllowedOriginPatterns() {
        return websocketAllowedOriginPatterns;
    }

    public void setWebsocketAllowedOriginPatterns(String websocketAllowedOriginPatterns) {
        this.websocketAllowedOriginPatterns = websocketAllowedOriginPatterns;
    }

    public List<String> getWebsocketAllowedOriginPatternList() {
        return Arrays.stream((websocketAllowedOriginPatterns == null ? "" : websocketAllowedOriginPatterns).split("[,;]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
