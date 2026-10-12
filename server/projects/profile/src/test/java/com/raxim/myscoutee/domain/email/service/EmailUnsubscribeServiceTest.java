package com.raxim.myscoutee.domain.email.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.email.config.EmailProperties;
import com.raxim.myscoutee.domain.platform.config.OperatorConfigurationProperties;
import com.raxim.myscoutee.domain.platform.service.DeploymentConfigurationCrypto;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.Tag("standard")
class EmailUnsubscribeServiceTest {
    @TempDir Path directory;
    MongoTemplate mongo;
    EmailUnsubscribeService service;
    @BeforeEach void setup() throws Exception {
        mongo = mock(MongoTemplate.class);
        Path key = directory.resolve("key");
        Files.writeString(key, "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        var cryptoProperties = new OperatorConfigurationProperties(); cryptoProperties.setMasterKeyPath(key);
        var email = new EmailProperties(); email.setBaseUrl("https://example.test");
        service = new EmailUnsubscribeService(mongo, new DeploymentConfigurationCrypto(cryptoProperties), new ObjectMapper(), email);
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
    }
    @AfterEach void clear() { MongoSessionModeContext.clear(); }
    @Test void tokenCarriesItsOwnModeAndNormalizesRecipientWithoutAccountLookup() {
        String token = service.link(" QA@EXAMPLE.TEST ").split("#token=")[1];
        assertFalse(token.contains("QA@"));
        MongoSessionModeContext.set(MongoSessionMode.REAL);
        when(mongo.exists(any(Query.class), eq("emailUnsubscriptions"))).thenAnswer(invocation -> {
            assertEquals(MongoSessionMode.DEMO, MongoSessionModeContext.currentMode()); return false;
        });
        assertFalse(service.status(token));
        assertEquals(MongoSessionMode.REAL, MongoSessionModeContext.currentMode());
        verify(mongo, never()).upsert(any(Query.class), any(Update.class), anyString());
        service.unsubscribe(token);
        assertEquals(MongoSessionMode.REAL, MongoSessionModeContext.currentMode());
        var query = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongo).upsert(query.capture(), any(Update.class), eq("emailUnsubscriptions"));
        reset(mongo); service.isUnsubscribed("qa@example.test");
        verify(mongo).exists(eq(query.getValue()), eq("emailUnsubscriptions"));
    }
    @Test void malformedOrTamperedCapabilityCannotWriteSuppression() {
        String token = service.link("qa@example.test").split("#token=")[1];
        assertThrows(ResponseStatusException.class, () -> service.unsubscribe(token.substring(0, 40) + "tampered"));
        assertThrows(ResponseStatusException.class, () -> service.unsubscribe("x".repeat(2050)));
        verifyNoInteractions(mongo);
    }
    @Test void repeatedUnsubscribeOnlySetsInitialTimestampAndRestoresModeOnDatabaseFailure() {
        String token = service.link("qa@example.test").split("#token=")[1];
        service.unsubscribe(token); service.unsubscribe(token);
        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).upsert(any(Query.class), update.capture(), eq("emailUnsubscriptions"));
        assertEquals(java.util.Set.of("$setOnInsert"), update.getValue().getUpdateObject().keySet());
        MongoSessionModeContext.set(MongoSessionMode.REAL);
        when(mongo.exists(any(Query.class), anyString())).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class, () -> service.status(token));
        assertEquals(MongoSessionMode.REAL, MongoSessionModeContext.currentMode());
    }
}
