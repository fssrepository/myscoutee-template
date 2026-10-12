package com.raxim.myscoutee.microservices.transport.security;

import java.util.List;
import java.util.Set;

public final class ActorDelegationBrokerProtocol {
    public static final String PATH = "/_internal/v1/accounts/actor-delegation";
    public static final String AUTHORITY = "accounts:actor-delegation:derive";
    public static final int MAX_REQUEST_BYTES = 16384;
    private ActorDelegationBrokerProtocol() {}
    public record Operation(String audience, String method, String path) {}
    public record Request(String parent, Operation operation, String bodyDigest) {
        @Override public String toString() { return "DelegationRequest[redacted]"; }
    }
    public record Result(String token) {
        @Override public String toString() { return "DelegationResult[redacted]"; }
    }
    public record Error(String code) {}
    public record Policies(java.util.Map<String, ClientPolicy> clients) {
        public Policies { clients = java.util.Map.copyOf(clients); }
    }
    public record ClientPolicy(String token, Set<String> audiences, List<Operation> operations) {
        public ClientPolicy {
            com.raxim.myscoutee.microservices.transport.common.InternalReadCredentialFilter.requireToken(token);
            audiences = Set.copyOf(audiences);
            operations = List.copyOf(operations);
            if (audiences.isEmpty() || audiences.size() > 512 || operations.size() > 512
                    || audiences.stream().anyMatch(value -> value.isBlank() || value.length() > 256)
                    || operations.stream().anyMatch(value -> !valid(value)))
                throw new IllegalArgumentException("Invalid delegation policy");
        }
        @Override public String toString() { return "ClientPolicy[redacted]"; }
    }
    public static boolean valid(Operation operation) {
        return operation != null && operation.audience() != null && !operation.audience().isBlank()
                && operation.audience().length() <= 256 && "POST".equals(operation.method())
                && operation.path() != null && operation.path().matches("/_internal/v1/[a-z0-9/_-]{1,256}");
    }
    public static boolean valid(Request request) {
        return request != null && request.parent() != null && request.parent().length() <= 8192
                && valid(request.operation()) && request.bodyDigest() != null
                && request.bodyDigest().matches("[0-9a-f]{64}");
    }
}
