package com.raxim.myscoutee.domain.email.api;

public interface EmailRendering {
    String renderText(String template, java.util.Map<String, String> variables);
}
