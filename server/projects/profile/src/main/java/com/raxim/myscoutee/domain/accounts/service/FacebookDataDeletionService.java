package com.raxim.myscoutee.domain.accounts.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;
import com.raxim.myscoutee.domain.email.api.EmailLinks;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.FacebookDataDeletionResponseDto;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class FacebookDataDeletionService {
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";
    private static final String PAYLOAD_ALGORITHM = "HMAC-SHA256";
    private static final String PUBLIC_STATUS_ROUTE = "/api/auth/facebook/data-deletion/status";

    private final RuntimeConfiguration configProperties;
    private final EmailLinks emailProperties;
    private final UsersRepository usersRepository;
    private final AccountLifecycleService lifecycle;
    private final ObjectMapper objectMapper;

    public FacebookDataDeletionService(
            RuntimeConfiguration configProperties,
            EmailLinks emailProperties,
            UsersRepository usersRepository,
            AccountLifecycleService lifecycle,
            ObjectMapper objectMapper) {
        this.configProperties = configProperties;
        this.emailProperties = emailProperties;
        this.usersRepository = usersRepository;
        this.lifecycle = lifecycle;
        this.objectMapper = objectMapper;
    }

    public FacebookDataDeletionResponseDto handleSignedRequest(String signedRequest) {
        FacebookDeletionPayload payload = parseSignedRequest(signedRequest);
        String facebookUserId = safeText(payload.userId());
        String confirmationCode = confirmationCode(facebookUserId);
        Optional<Users> userByFacebookId = usersRepository.findFirstByFacebookId(facebookUserId);
        Users user = userByFacebookId == null ? null : userByFacebookId.orElse(null);
        if (user != null && StringUtils.hasText(user.getId())) {
            lifecycle.scheduleUserDeletionByUserId(user.getId(), "facebook-data-deletion");
        }
        return new FacebookDataDeletionResponseDto(statusUrl(confirmationCode), confirmationCode);
    }

    public String renderStatusPage(String confirmationCode) {
        String normalizedCode = safeText(confirmationCode);
        String statusContent = StringUtils.hasText(normalizedCode)
                ? "<p>Confirmation code: <strong>" + escapeHtml(normalizedCode) + "</strong></p>"
                        + "<p>The deletion request has been received. If it matches an existing MyScoutee account, "
                        + "that account is scheduled for deletion through the same account deletion flow used in the app.</p>"
                : "<p>No confirmation code was provided. If you requested deletion through Meta/Facebook, "
                        + "use the status URL returned with the confirmation response.</p>";
        return "<!doctype html>"
                + "<html lang=\"en\">"
                + "<head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>MyScoutee data deletion status</title>"
                + "<style>"
                + "body{font-family:system-ui,-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;margin:0;background:#f6f7fb;color:#10233f;}"
                + "main{max-width:720px;margin:48px auto;padding:32px;background:#fff;border:1px solid #d8e0ee;border-radius:12px;box-shadow:0 16px 40px rgba(16,35,63,.08);}"
                + "h1{margin:0 0 12px;font-size:28px;line-height:1.15;}p{font-size:16px;line-height:1.6;color:#41516d;}strong{color:#10233f;}"
                + "</style>"
                + "</head>"
                + "<body><main>"
                + "<h1>MyScoutee data deletion status</h1>"
                + statusContent
                + "<p>Deleted accounts can be reactivated within 30 days. After that window, eligible account data is permanently purged or anonymized according to the privacy policy.</p>"
                + "</main></body></html>";
    }

    private FacebookDeletionPayload parseSignedRequest(String signedRequest) {
        if (!StringUtils.hasText(configProperties.getFacebookAppSecret())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Facebook deletion callback is not configured.");
        }
        String normalizedSignedRequest = safeText(signedRequest);
        String[] parts = normalizedSignedRequest.split("\\.", 2);
        if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Facebook signed request.");
        }
        byte[] actualSignature = base64UrlDecode(parts[0]);
        byte[] expectedSignature = hmacSha256(parts[1], configProperties.getFacebookAppSecret());
        if (!MessageDigest.isEqual(actualSignature, expectedSignature)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Facebook signed request signature.");
        }
        JsonNode payloadNode;
        try {
            payloadNode = objectMapper.readTree(base64UrlDecode(parts[1]));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Facebook signed request payload.", exception);
        }
        String algorithm = safeText(payloadNode.path("algorithm").asText());
        if (StringUtils.hasText(algorithm) && !PAYLOAD_ALGORITHM.equalsIgnoreCase(algorithm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Facebook signed request algorithm.");
        }
        String userId = safeText(payloadNode.path("user_id").asText());
        if (!StringUtils.hasText(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Facebook deletion request is missing user id.");
        }
        return new FacebookDeletionPayload(userId);
    }

    private byte[] hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), SIGNATURE_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to verify Facebook signed request.", exception);
        }
    }

    private byte[] base64UrlDecode(String value) {
        try {
            return Base64.getUrlDecoder().decode(padBase64(safeText(value)));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Facebook signed request encoding.", exception);
        }
    }

    private String padBase64(String value) {
        int padding = (4 - (value.length() % 4)) % 4;
        return value + "=".repeat(padding);
    }

    private String confirmationCode(String facebookUserId) {
        return "FB" + HexFormat.of().formatHex(sha256(safeText(facebookUserId).getBytes(StandardCharsets.UTF_8)))
                .substring(0, 24)
                .toUpperCase();
    }

    private String statusUrl(String confirmationCode) {
        String baseUrl = safeText(emailProperties.getBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "https://myscoutee.com";
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + PUBLIC_STATUS_ROUTE + "?code=" + URLEncoder.encode(confirmationCode, StandardCharsets.UTF_8);
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable.", exception);
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeHtml(String value) {
        return safeText(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record FacebookDeletionPayload(String userId) {
    }
}
