package com.raxim.myscoutee.microservices.transport.common;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolFieldsTest {
    @Test void sharedPrivacyEnvelopeRetainsItsExistingWireShapeAndJobContext() {
        var mapper = new com.raxim.myscoutee.common.config.JsonConfig().objectMapper();
        var command = new ProtocolFields.UserCommand(new ProtocolFields.JobScope(true, "work"), "viewer");
        assertEquals("{\"scope\":{\"active\":true,\"groupId\":\"work\"},\"userId\":\"viewer\"}", mapper.writeValueAsString(command));
        assertEquals("{\"completed\":true}", mapper.writeValueAsString(new ProtocolFields.Receipt(true)));
        assertEquals("{\"code\":\"INVALID_REQUEST\"}", mapper.writeValueAsString(new ProtocolFields.Error("INVALID_REQUEST")));
        assertEquals(command, mapper.readValue(mapper.writeValueAsBytes(command), ProtocolFields.UserCommand.class));
        assertEquals(new ProtocolFields.JobScope(false, null), ProtocolFields.JobScope.current());
        com.raxim.myscoutee.domain.platform.api.JobGroupContext.run("work", () ->
                assertEquals(new ProtocolFields.JobScope(true, "work"), ProtocolFields.JobScope.current()));
        assertEquals(new ProtocolFields.JobScope(false, null), ProtocolFields.JobScope.current());
    }
    @Test void privacyCommandsKeepRequiredIdsSeparateFromNullableReadIds() {
        for (String id : Arrays.asList(null, "", " ", "x".repeat(513))) assertFalse(ProtocolFields.requiredId(id));
        assertTrue(ProtocolFields.requiredId("x".repeat(512)));
        assertTrue(ProtocolFields.requiredIds(Collections.emptyList()));
        assertTrue(ProtocolFields.requiredIds(Collections.nCopies(4096, "id")));
        assertFalse(ProtocolFields.requiredIds(null));
        assertFalse(ProtocolFields.requiredIds(Arrays.asList("id", null)));
        assertFalse(ProtocolFields.requiredIds(Collections.nCopies(4097, "id")));
    }
    @Test void jobScopeRetainsInactiveAndActiveDatingAsDifferentStates() {
        assertFalse(ProtocolFields.jobScope(null, null));
        assertTrue(ProtocolFields.jobScope(false, null));
        assertTrue(ProtocolFields.jobScope(true, null));
        assertTrue(ProtocolFields.jobScope(true, "group"));
        assertFalse(ProtocolFields.jobScope(false, "group"));
        assertFalse(ProtocolFields.jobScope(true, " "));
        assertFalse(ProtocolFields.jobScope(true, "x".repeat(513)));
    }
    @Test void nullableTextRetainsItsExactLengthBoundary() {
        assertTrue(ProtocolFields.text(null, 512));
        assertTrue(ProtocolFields.text("", 512));
        assertTrue(ProtocolFields.text("x".repeat(512), 512));
        assertFalse(ProtocolFields.text("x".repeat(513), 512));
    }

    @Test void collectionBoundsDoNotTurnOptionalIdsIntoRequiredIds() {
        assertFalse(ProtocolFields.ids(null));
        assertTrue(ProtocolFields.ids(Collections.emptyList()));
        assertTrue(ProtocolFields.ids(Arrays.asList(null, "", " ", "x".repeat(512))));
        assertTrue(ProtocolFields.ids(Collections.nCopies(4096, "id")));
        assertFalse(ProtocolFields.ids(Collections.nCopies(4097, "id")));
        assertFalse(ProtocolFields.ids(Collections.singletonList("x".repeat(513))));
    }
}
