package com.raxim.myscoutee.microservices.transport.email;

public final class EmailRenderingProtocol {
    public static final String PATH = "/_internal/v1/email/render-text";
    public static final int MAX_REQUEST_BYTES = 262144;
    private EmailRenderingProtocol() {}
    public record Query(String template, java.util.Map<String, String> variables) {}
    public record Result(Boolean resolved, String text) {}
    public record Error(String code) {}
    public static boolean valid(Query value) { return value != null && ((value.template() == null || value.template().length() <= 131072) && (value.variables() == null || (value.variables().size() <= 256 && value.variables().entrySet().stream().allMatch(entry -> entry.getKey() != null && entry.getKey().length() <= 512 && (entry.getValue() == null || entry.getValue().length() <= 8192))))); }
}
