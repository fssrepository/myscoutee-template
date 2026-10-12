package com.raxim.myscoutee.domain.platform.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

public interface DeploymentBranding {
    ResolvedDeploymentBranding resolve();

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    record ResolvedDeploymentBranding(
            String productName,
            String logoUrl,
            long revision) {
        public String applicationIcon(String candidate) {
            String normalized = safeText(candidate);
            String withoutLeadingSlash = normalized.startsWith("/")
                    ? normalized.substring(1)
                    : normalized;
            return !StringUtils.hasText(normalized)
                    || "assets/logo/heart.webp".equalsIgnoreCase(withoutLeadingSlash)
                    || "assets/logo/heart.png".equalsIgnoreCase(withoutLeadingSlash)
                            ? logoUrl
                            : normalized;
        }

        public Map<String, String> enrich(
                Map<String, String> source) {
            Map<String, String> variables = new LinkedHashMap<>();
            if (source != null) {
                variables.putAll(source);
            }
            variables.put("productName", productName);
            variables.put("product_name", productName);
            variables.put("productLogoUrl", logoUrl);
            variables.put("product_logo_url", logoUrl);
            variables.put(
                    "productBrandingRevision",
                    Long.toString(revision));
            variables.put(
                    "product_branding_revision",
                    Long.toString(revision));
            return variables;
        }
    }
}
