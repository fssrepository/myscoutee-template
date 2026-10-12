package com.raxim.myscoutee.domain.email.service;

import com.raxim.myscoutee.domain.email.api.EmailSendResult;

import com.raxim.myscoutee.domain.email.api.EmailSendRequest;

import com.raxim.myscoutee.domain.platform.api.EmailSecrets;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;

@Service
public class OperatorEmailConfigurationService implements com.raxim.myscoutee.domain.email.api.EmailSending {
    private static final EmailSecrets.Purpose PURPOSE = EmailSecrets.Purpose.PROVIDER;
    @Document("operatorEmailConfigurations")
    public record Configuration(@Id String id, long revision, boolean enabled, String providerId,
            String fromEmail, String fromName, EncryptedSecret credential, String updatedAtIso) {}
    public record Provider(String id, String label, String icon, String palette) {}
    public record Snapshot(long revision, boolean enabled, String providerId, String fromEmail, String fromName,
            boolean credentialConfigured, List<Provider> availableProviders) {}
    public record Save(long expectedRevision, boolean enabled, String providerId, String fromEmail, String fromName,
            @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) String credential,
            boolean clearCredential) {}
    private final MongoTemplate mongo;
    private final EmailSecrets crypto;
    private final List<EmailProvider> providers;
    private final EmailProviderGateway gateway;
    public OperatorEmailConfigurationService(MongoTemplate mongo, EmailSecrets crypto, List<EmailProvider> providers, EmailProviderGateway gateway) {
        this.mongo = mongo; this.crypto = crypto; this.providers = providers; this.gateway = gateway;
    }
    private Configuration configuration() {
        var saved = mongo.findById("global", Configuration.class);
        return saved == null ? new Configuration("global", 0, false, "", "", "", null, "") : saved;
    }
    public Snapshot snapshot() {
        var state = configuration();
        return new Snapshot(state.revision(), state.enabled(), state.providerId(), state.fromEmail(), state.fromName(),
                state.credential() != null, providers.stream().map(EmailProvider::configurationDescriptor).filter(java.util.Objects::nonNull).toList());
    }
    public Snapshot save(Save request) {
        var before = configuration();
        if (before.revision() != request.expectedRevision()) throw conflict();
        String id = text(request.providerId()), from = text(request.fromEmail()), name = text(request.fromName());
        if (!id.isEmpty() && providers.stream().noneMatch(p -> p.configurationDescriptor() != null && p.providerKey().equals(id))) throw invalid();
        if (name.length() > 100 || from.length() > 254 || (!from.isEmpty() && !from.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) throw invalid();
        String secret = text(request.credential());
        if (secret.length() > 4096 || secret.contains("\n") || secret.contains("\r")) throw invalid();
        EncryptedSecret credential = request.clearCredential() || !id.equals(before.providerId()) ? null : before.credential();
        if (!secret.isEmpty()) credential = crypto.encryptEmailSecret(PURPOSE, secret);
        if (request.enabled() && (id.isEmpty() || from.isEmpty() || credential == null)) throw invalid();
        var update = new Update().inc("revision", 1).set("enabled", request.enabled()).set("providerId", id)
                .set("fromEmail", from).set("fromName", name).set("credential", credential).set("updatedAtIso", Instant.now().toString());
        try {
            var saved = mongo.findAndModify(Query.query(Criteria.where("_id").is("global").and("revision").is(before.revision())), update,
                    org.springframework.data.mongodb.core.FindAndModifyOptions.options().upsert(before.revision() == 0).returnNew(true), Configuration.class);
            if (saved == null) throw conflict();
        } catch (DuplicateKeyException ex) { throw conflict(); }
        return snapshot();
    }
    public boolean enabled() { var state = configuration(); return state.enabled() && state.credential() != null; }
    public EmailSendResult send(EmailSendRequest content) {
        var state = configuration();
        if (!state.enabled() || state.credential() == null) return new EmailSendResult(false, null, "Email provider is not configured.");
        var provider = providers.stream().filter(p -> p.providerKey().equals(state.providerId()) && p.configurationDescriptor() != null).findFirst().orElse(null);
        if (provider == null) return new EmailSendResult(false, null, "Email provider is unavailable.");
        return gateway.sendConfigured(state.providerId(), new EmailSendRequest(content.toEmail(), content.toName(), content.subject(), content.htmlBody(), content.textBody(),
                state.fromEmail(), state.fromName(), state.fromEmail(), content.category(), content.metadata()), crypto.decryptEmailSecret(PURPOSE, state.credential()));
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "operator.email.invalid"); }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "operator.email.changed"); }
}
