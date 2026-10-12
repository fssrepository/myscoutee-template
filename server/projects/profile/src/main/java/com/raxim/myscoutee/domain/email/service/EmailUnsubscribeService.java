package com.raxim.myscoutee.domain.email.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Supplier;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.email.config.EmailProperties;
import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;
import com.raxim.myscoutee.domain.platform.api.EmailSecrets;
import tools.jackson.databind.ObjectMapper;

/** Recipient-level suppression survives profile deletion and needs no login. */
@Service
public class EmailUnsubscribeService {
    private static final EmailSecrets.Purpose PURPOSE = EmailSecrets.Purpose.UNSUBSCRIBE;
    private static final String COLLECTION = "emailUnsubscriptions";
    private final MongoTemplate mongo;
    private final EmailSecrets crypto;
    private final ObjectMapper mapper;
    private final EmailProperties properties;
    public record Ticket(String recipientHash, String mode) {}

    public EmailUnsubscribeService(MongoTemplate mongo, EmailSecrets crypto,
            ObjectMapper mapper, EmailProperties properties) {
        this.mongo = mongo; this.crypto = crypto; this.mapper = mapper; this.properties = properties;
    }

    public boolean isUnsubscribed(String email) {
        return mongo.exists(Query.query(Criteria.where("_id").is(recipientHash(email))), COLLECTION);
    }

    public String link(String email) {
        Ticket ticket = new Ticket(recipientHash(email), MongoSessionModeContext.currentMode().name());
        EncryptedSecret encrypted = crypto.encryptEmailSecret(PURPOSE, mapper.writeValueAsString(ticket));
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(encrypted));
        return publicBaseUrl() + "/api/public/email/unsubscribe#token=" + token;
    }

    public String publicBaseUrl() {
        URI base = URI.create(properties.getBaseUrl().trim());
        if (!java.util.Set.of("https", "http").contains(base.getScheme()) || base.getHost() == null
                || base.getRawUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new IllegalStateException("Email public base URL is invalid.");
        }
        return base.toString().replaceAll("/+$", "");
    }

    public boolean status(String token) {
        Ticket ticket = decode(token);
        return inMode(ticket, () -> mongo.exists(Query.query(Criteria.where("_id").is(ticket.recipientHash())), COLLECTION));
    }

    public void unsubscribe(String token) {
        Ticket ticket = decode(token);
        inMode(ticket, () -> {
            mongo.upsert(Query.query(Criteria.where("_id").is(ticket.recipientHash())),
                    new Update().setOnInsert("unsubscribedAtIso", Instant.now().toString()), COLLECTION);
            return null;
        });
    }

    private Ticket decode(String token) {
        try {
            if (token == null || token.length() > 2048 || !token.matches("[A-Za-z0-9_-]{20,2048}")) throw new IllegalArgumentException();
            var envelope = mapper.readValue(Base64.getUrlDecoder().decode(token), EncryptedSecret.class);
            Ticket ticket = mapper.readValue(crypto.decryptEmailSecret(PURPOSE, envelope), Ticket.class);
            if (ticket.recipientHash() == null || !ticket.recipientHash().matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
            MongoSessionMode.valueOf(ticket.mode());
            return ticket;
        } catch (RuntimeException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email.unsubscribe.invalid");
        }
    }

    private <T> T inMode(Ticket ticket, Supplier<T> action) {
        MongoSessionMode before = MongoSessionModeContext.currentMode();
        try {
            MongoSessionModeContext.set(MongoSessionMode.valueOf(ticket.mode()));
            return action.get();
        } finally { MongoSessionModeContext.set(before); }
    }

    private String recipientHash(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Email recipient is missing.");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
