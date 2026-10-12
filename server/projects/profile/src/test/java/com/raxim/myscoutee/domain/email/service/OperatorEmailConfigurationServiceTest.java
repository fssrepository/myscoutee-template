package com.raxim.myscoutee.domain.email.service;

import com.raxim.myscoutee.domain.platform.service.DeploymentConfigurationCrypto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;
import com.raxim.myscoutee.domain.email.service.EmailProvider;
import com.raxim.myscoutee.domain.email.service.OperatorEmailConfigurationService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@org.junit.jupiter.api.Tag("standard")
class OperatorEmailConfigurationServiceTest {
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final DeploymentConfigurationCrypto crypto = mock(DeploymentConfigurationCrypto.class);
    final EmailProvider provider = mock(EmailProvider.class);
    final OperatorEmailConfigurationService service = new OperatorEmailConfigurationService(mongo, crypto, List.of(provider), mock(com.raxim.myscoutee.domain.email.service.EmailProviderGateway.class));
    void provider() { when(provider.providerKey()).thenReturn("sendgrid"); when(provider.configurationDescriptor()).thenReturn(new Provider("sendgrid", "SendGrid", "mail", "blue")); }
    @Test void catalogComesFromRegisteredProviderAndSnapshotNeverContainsSecret() {
        provider(); var secret = new EncryptedSecret();
        when(mongo.findById("global", Configuration.class)).thenReturn(new Configuration("global", 2, true, "sendgrid", "from@example.test", "Sender", secret, ""));
        Snapshot snapshot = service.snapshot(); assertTrue(snapshot.credentialConfigured()); assertEquals("sendgrid", snapshot.availableProviders().getFirst().id());
        assertTrue(java.util.Arrays.stream(Snapshot.class.getRecordComponents()).noneMatch(field -> field.getName().equals("credential")));
    }
    @Test void enabledConfigurationRequiresRealProviderSenderAndCredential() {
        provider(); assertThrows(ResponseStatusException.class, () -> service.save(new Save(0, true, "noop", "from@example.test", "", "key", false)));
        assertThrows(ResponseStatusException.class, () -> service.save(new Save(0, true, "sendgrid", "from@example.test", "", "", false)));
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Configuration.class));
    }
    @Test void staleSaveIsRejectedBeforeEncryptingOrWriting() {
        when(mongo.findById("global", Configuration.class)).thenReturn(new Configuration("global", 3, false, "", "", "", null, ""));
        assertThrows(ResponseStatusException.class, () -> service.save(new Save(2, false, "sendgrid", "from@example.test", "", "key", false)));
        verifyNoInteractions(crypto);
    }
    @Test void newSecretIsEncryptedAndPersistedWithoutPlaintext() {
        provider(); var envelope = new EncryptedSecret(); when(crypto.encryptEmailSecret(any(com.raxim.myscoutee.domain.platform.api.EmailSecrets.Purpose.class), eq("operator-key"))).thenReturn(envelope);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Configuration.class))).thenAnswer(call -> {
            Update update = call.getArgument(1); var fields = (org.bson.Document) update.getUpdateObject().get("$set");
            assertSame(envelope, fields.get("credential")); assertFalse(fields.values().contains("operator-key"));
            return new Configuration("global", 1, true, "sendgrid", "from@example.test", "", envelope, "");
        });
        service.save(new Save(0, true, "sendgrid", "from@example.test", "", "operator-key", false));
        verify(crypto).encryptEmailSecret(com.raxim.myscoutee.domain.platform.api.EmailSecrets.Purpose.PROVIDER, "operator-key");
    }
}
