package com.raxim.myscoutee.microservices.transport.common;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class ReadQueryBodiesTest {
    private final JsonMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false).build();
    record Query(String id, int count) {}

    @Test void validEnvelopeRetainsValuesForBothContractPolicies() {
        for (boolean strict : new boolean[]{false, true}) {
            var query = decode("{\"id\":\"a\",\"count\":3}", strict);
            assertEquals(new Query("a", 3), query);
        }
    }

    @Test void malformedUnknownTrailingAndInvalidFieldsAreRejected() {
        for (String body : new String[]{"{", "null", "{}", "{\"id\":\"a\",\"extra\":1}",
                "{\"id\":\"a\"}{}", "{\"id\":\"\"}"}) {
            var failure = assertThrows(ReadQueryBodies.RejectedQuery.class, () -> decode(body, true));
            assertEquals(400, failure.status());
            assertEquals("INVALID_REQUEST", failure.code());
        }
    }

    @Test void primitiveNullPolicyDoesNotChangeTheSharedMapper() {
        String body = "{\"id\":\"a\",\"count\":null}";
        assertEquals(0, decode(body, false).count());
        assertThrows(ReadQueryBodies.RejectedQuery.class, () -> decode(body, true));
        assertEquals(0, decode(body, false).count());
    }

    @Test void validatorFailuresRemainOwnerFailuresInsteadOfMalformedInput() {
        assertThrows(IllegalStateException.class, () -> ReadQueryBodies.decode(bytes("{}"), mapper, Query.class,
                query -> { throw new IllegalStateException("owner failed"); }, false));
    }

    private Query decode(String body, boolean strict) {
        return ReadQueryBodies.decode(bytes(body), mapper, Query.class,
                query -> query != null && query.id() != null && !query.id().isBlank(), strict);
    }
    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
